package eu.transkribus.appserver.logic.jobs;

import java.sql.SQLException;
import java.util.Properties;

import javax.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.server.logic.JobManager;
import eu.transkribus.server.util.MailUtils;

public class HtrTrainingJob extends AResumableTrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(HtrTrainingJob.class);
	
	private String modelName;
	private int[] docIds;
	//set initial state
	private String state = JobConst.STATE_CREATE;	
	
	public HtrTrainingJob(TrpJobStatus job) {
		super(job);
	}
	
	public HtrTrainingJob(TrpJobStatus job, final String modelName, int... docIds) {
		super(job);
		this.modelName = modelName;
		this.docIds = docIds;
	}
	
	public void setModelName(final String modelName){
		this.modelName = modelName;
	}
	
	public void setDocIds(final int... docIds){
		this.docIds = docIds;
	}
	
//	@Override
//	protected void resume(String... params) {
//		if(params.length != 3){
//			throw new IllegalArgumentException("HTR Training job needs 3 params to resume!");
//		}
//		this.state = params[0];
//		this.modelName = params[1];
//		String docIdStr = params[2];
//		String[] tmp = docIdStr.split(",");
//		docIds = new int[tmp.length];
//		for(int i = 0; i < tmp.length; i++){
//			docIds[i] = Integer.parseInt(tmp[i]);
//		}
//	}
	
	@Override
	protected void resume(Properties p) {
		if(p.entrySet().size() != 3){
			throw new IllegalArgumentException("HTR Training job needs 3 params to resume!");
		}
		this.state = p.getProperty(JobConst.PROP_STATE);
		this.modelName = p.getProperty(JobConst.PROP_MODELNAME);
		String docIdStr = p.getProperty(JobConst.PROP_DOC_IDS);
		String[] tmp = docIdStr.split(JobConst.SEP);
		docIds = new int[tmp.length];
		for(int i = 0; i < tmp.length; i++){
			docIds[i] = Integer.parseInt(tmp[i]);
		}
	}
	
	@Override
	protected Properties buildJobData(){
		Properties p = new Properties();
		p.setProperty(JobConst.PROP_STATE, state);
		p.setProperty(JobConst.PROP_MODELNAME, modelName);
		String idStr = "";
		boolean isFirst = true;
		for(int id : docIds){
			if(isFirst){
				idStr += id;
				isFirst = false;
			} else {
				idStr += JobConst.SEP + id;
			}
		}
		p.setProperty(JobConst.PROP_DOC_IDS,  idStr);
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
			try {
				//TODO do all kinds of document checks				
			} catch (Exception e) {
				try {
					JobManager.getInstance().finishJob(this.job.getJobId(), e.getMessage(), false);
					return;
				} catch (EntityNotFoundException | IllegalArgumentException | SQLException
						| ReflectiveOperationException e1) {
					logger.error("Could not update job!", e1);
					return;
				}
			}
			if(Thread.currentThread().isInterrupted()){
				return;
			}
			//create dealog docId
			state = JobConst.STATE_HTR;
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Waiting for HTR Training to start...", buildJobDataStr()));
		case JobConst.STATE_HTR:
			logger.debug("In state: " + JobConst.STATE_HTR);
			//wait for entry in dealog db to be set to finished
			while(true && !Thread.currentThread().isInterrupted()){
			
				try {
					TrpJobStatus job = JobManager.getInstance().getJob(this.jobId);
					logger.debug("jobData = " + job.getJobData());
					final String htrJobState = CoreUtils.readPropertiesFromString(job.getJobData()).getProperty(JobConst.PROP_STATE);
					if(JobConst.STATE_FINISH.equals(htrJobState)){
					
//					if(job.getJobData().startsWith(JobConst.STATE_FINISH)){
						logger.info("Remote HTR Training is finished! Taking over...");
						break;
					}
					if(job.getState().equals(TrpJobStatus.FAILED)){
						//break if job failed
						logger.error("Remote HTR Training failed!");
						return;
					}
					
				} catch (Exception e1) {
					logger.error("Could not retrieve job from DB!", e1);
				}
				
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					logger.error("HTR Training job sleep was interrupted!",e);
					Thread.currentThread().interrupt();
					return;
				}
			}
			
			if(Thread.currentThread().isInterrupted()){
				return;
			}			
			state = JobConst.STATE_FINISH;
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("HTR Training done", buildJobDataStr()));
		case JobConst.STATE_FINISH:
			logger.debug("In state: " + JobConst.STATE_FINISH);
			try {
				//check the model on dea_scratch
				
				//send mail to user
				UserDao ud = new UserDao();
				TrpUser user = ud.getUser(job.getUserId(), true);
				final String email = user.getEmail();
				if(email == null || email.isEmpty()){
					logger.warn("User has no email address! Skipping mail...");
				} else {
					String msg = "Dear " + user.getFirstname() + ",\n";
					msg += "your handwriting model \"" + modelName 
							+ "\" is now trained and ready to use for recognition processes in Transkribus!\n";
					MailUtils.sendMailFromUibkAddress(email, MailUtils.TRANSKRIBUS_EMAIL_MAIL_SERVER.getEmail(), "HTR Training is complete", msg, null, true, true);
				}
			} catch (Exception e) {
				logger.error("Problem during finish phase of HTR Training!", e);
			}
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("HTR Training complete", buildJobDataStr()));
		}
		try {
			JobManager.getInstance().finishJob(this.job.getJobId(), "HTR Training finished!", true);
		} catch (EntityNotFoundException | IllegalArgumentException | SQLException
				| ReflectiveOperationException e) {
			logger.error("Could not update job!", e);
			return;
		}
		return;
	}
}
