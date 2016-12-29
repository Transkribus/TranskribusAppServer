package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.AHmmHtrJob;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.persistence.jobs.htr.HtrConf;
import eu.transkribus.persistence.jobs.htr.logic.HtrTrainer;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.util.MailUtils;

public class HmmHtrTrainingJob extends AHmmHtrJob {
	private static final Logger logger = LoggerFactory.getLogger(HmmHtrTrainingJob.class);
	
	private final String modelName;
	private final List<Integer> docIds;
	
	public HmmHtrTrainingJob(TrpJobStatus job) {
		super(job);
		modelName = getProperty(JobConst.PROP_MODELNAME);
		docIds = getIntListProperty(JobConst.PROP_DOC_IDS);
	}
	
		
	@Override
	public void doProcess() throws JobCanceledException {
		
		int nrOfCores = HtrConf.getInt("nrOfCores");
		logger.info("Using " + nrOfCores + " cores");
		
		logger.info("DB Service name: " + DbConnection.getDbServiceName());
		
		File workdir = new File(HtrConf.getString("workdir"));
		logger.info("Using workdir: " + workdir.getAbsolutePath());
		if(!workdir.isDirectory() && !workdir.mkdir()){
			this.setJobStatusFailed("Could not create workDir: " + workdir.getAbsolutePath());
			return;
		}
		
		HtrTrainer trainer;
		try {
			trainer = new HtrTrainer(this.getJobStatus(), modelName);
		} catch (IOException e) {
			logger.error("Could not initialize HTR Trainer!", e);
			this.setJobStatusFailed("Could not initialize HTR Trainer!");
			return;
		}
		
		try {
			super.startHtrProcess(this.getJobStatus(), trainer, nrOfCores, modelName, docIds);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			this.setJobStatusFailed(e.getMessage());
			return;
		}
		
		//send mail to user
		try {	
			UserDao ud = new UserDao();
			TrpUser user = ud.getUser(this.getJobStatus().getUserId(), true);
			final String email = user.getEmail();
			if(email == null || email.isEmpty()){
				logger.warn("User has no email address! Skipping mail...");
			} else {
				String msg = "Dear " + user.getFirstname() + ",\n";
				msg += "your handwriting model \"" + modelName 
						+ "\" is now trained and ready to use for recognition processes in Transkribus!\n";
				MailUtils.sendMailFromUibkAddress(email, MailUtils.TRANSKRIBUS_EMAIL_MAIL_SERVER.getEmail(), "HTR Training is complete", msg, null, true, true);
			}
		} catch (SQLException | IOException e) {
			logger.error(e.getMessage(), e);
		}
		
		this.setJobStatusFinished();
	}	
}
