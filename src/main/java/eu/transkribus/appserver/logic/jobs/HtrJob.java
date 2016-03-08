package eu.transkribus.appserver.logic.jobs;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.server.logic.JobManager;
import eu.transkribus.server.util.MailUtils;

public class HtrJob extends AResumableTrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(HtrJob.class);
	
	private String modelName;
	private String pages;
	//set initial state
	private String state = JobConst.STATE_CREATE;	
	
	public HtrJob(TrpJobStatus job) {
		super(job);
	}
	
	public HtrJob(TrpJobStatus job, final String pageStr, final String modelName) {
		super(job);
		this.modelName = modelName;
		this.pages = pageStr;
	}
	
	public void setModelName(final String modelName){
		this.modelName = modelName;
	}
	
	@Override
	protected void resume(Properties p) {
		if(p.entrySet().size() < 2){
			throw new IllegalArgumentException("HTR job needs 2 params to resume!");
		}
		this.state = p.getProperty(JobConst.PROP_STATE);
		this.modelName = p.getProperty(JobConst.PROP_MODELNAME);
		this.pages = p.getProperty(JobConst.PROP_PAGES);
	}
	
	@Override
	protected Properties buildJobData(){
		Properties p = new Properties();
		p.setProperty(JobConst.PROP_STATE, state);
		p.setProperty(JobConst.PROP_MODELNAME, modelName);
		if(this.pages != null && !this.pages.isEmpty()){
			p.setProperty(JobConst.PROP_PAGES, pages);
		}
		return p;
	}
	
	@Override
	public void run() {
		updateStatus("Started job");
		switch(state){
		case JobConst.STATE_CREATE:
			logger.debug("In state: " + JobConst.STATE_CREATE);
			//download the document files to path
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Checking document files...", buildJobDataStr()));
//			try {
//				//TODO do all kinds of document checks				
//			} catch (Exception e) {
//				try {
//					JobManager.getInstance().finishJob(this.job.getJobId(), e.getMessage(), false);
//					return;
//				} catch (EntityNotFoundException | IllegalArgumentException | SQLException
//						| ReflectiveOperationException e1) {
//					logger.error("Could not update job!", e1);
//					return;
//				}
//			}
//			if(Thread.currentThread().isInterrupted()){
//				return;
//			}
			state = JobConst.STATE_HTR;
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Waiting for HTR to start...", buildJobDataStr()));
		case JobConst.STATE_HTR:
			logger.debug("In state: " + JobConst.STATE_HTR);
			//wait for entry in dealog db to be set to finished
			while(true && !Thread.currentThread().isInterrupted()){
			
				try {
					TrpJobStatus job = JobManager.getInstance().getJob(this.jobId);
					logger.trace("jobData = " + job.getJobData());
					logger.trace("job = " + job);
					final String htrJobState = CoreUtils.readPropertiesFromString(job.getJobData()).getProperty(JobConst.PROP_STATE);
					if(JobConst.STATE_FINISH.equals(htrJobState)){
						logger.info("Remote HTR is finished! Taking over...");
						break;
					}
					if(job.getState().equals(TrpJobStatus.FAILED)){
						//break if job failed
						logger.error("Remote HTR Decoding failed!");
						return;
					}
					
				} catch (EntityNotFoundException | IllegalArgumentException | SQLException
						| ReflectiveOperationException | IOException e1) {
					logger.error("Could not retrieve job from DB!", e1);
				}
				
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					logger.error("HTR job sleep was interrupted!",e);
					Thread.currentThread().interrupt();
					return;
				}
			}
			
			if(Thread.currentThread().isInterrupted()){
				return;
			}			
			state = JobConst.STATE_FINISH;
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("HTR done", buildJobDataStr()));
		case JobConst.STATE_FINISH:
			logger.debug("In state: " + JobConst.STATE_FINISH);
			try {
				//do checks?
				
				//send mail to user
				UserDao ud = new UserDao();
				TrpUser user = ud.getUser(job.getUserId(), true);
				DocManager docMan = new DocManager();
				TrpDocMetadata docMd = docMan.getDocMdById(job.getDocId());
				final String email = user.getEmail();
				if(email == null || email.isEmpty()){
					logger.warn("User has no email address! Skipping mail...");
				} else {
					String msg = "Dear " + user.getFirstname() + ",\n";
					msg += "the handwritten text recognition in your document\n" 
							+ "ID " + job.getDocId() + ", Title: " + docMd.getTitle() + "\n";
							if(job.getPageNr() > 0){
								msg += "Page: " + job.getPageNr() + "\n";
							}
					msg +="\nwas successfully completed! ";
					msg += "You can now review the result in Transkribus.";
					MailUtils.sendMailFromUibkAddress(email, MailUtils.TRANSKRIBUS_EMAIL_MAIL_SERVER.getEmail(), "HTR process is complete", msg, null, true, true);
				}
				
			} catch (Exception e) {
				logger.error("Problem during finish phase of HTR!", e);
			}
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("HTR complete", buildJobDataStr()));
		}
		try {
			JobManager.getInstance().finishJob(this.job.getJobId(), "HTR finished!", true);
		} catch (EntityNotFoundException | IllegalArgumentException | SQLException
				| ReflectiveOperationException e) {
			logger.error("Could not update job!", e);
			return;
		}
		return;
	}
}
