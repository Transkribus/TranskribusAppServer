package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.AHmmHtrJob;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.persistence.jobs.htr.HtrConf;
import eu.transkribus.persistence.jobs.htr.logic.HtrDecoder;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.util.MailUtils;

public class HmmHtrJob extends AHmmHtrJob {
	private static final Logger logger = LoggerFactory.getLogger(HmmHtrJob.class);

	private final String modelName;

	public HmmHtrJob(TrpJobStatus job) {
		super(job);
		modelName = getProperty(JobConst.PROP_MODELNAME);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		int nrOfCores = HtrConf.getInt("nrOfCores");
		logger.info("Using " + nrOfCores + " cores");

		logger.info("DB Service name: " + DbConnection.getDbServiceName());

		File workdir = new File(HtrConf.getString("workdir"));
		logger.info("Using workdir: " + workdir.getAbsolutePath());
		if (!workdir.isDirectory() && !workdir.mkdir()) {
			this.setJobStatusFailed("Could not create workDir: " + workdir.getAbsolutePath());
			return;
		}

		DocManager docMan = new DocManager();
		final int nPages;
		try {
			final int docId = getJobStatus().getDocId();
			TrpDocMetadata docMd = docMan.getDocMdById(docId);
			nPages = docMd.getNrOfPages();
		} catch (EntityNotFoundException | IllegalArgumentException | SQLException | ReflectiveOperationException
				| IOException e) {
			logger.error("Doc with ID " + job.getDocId() + " does not exist!", e);
			this.setJobStatusFailed("Doc with ID " + job.getDocId() + " does not exist!");
			return;
		}

		HtrDecoder decoder;
		try {
			decoder = new HtrDecoder(this.getJobStatus(), modelName);
		} catch (IOException | SQLException | ReflectiveOperationException e) {
			logger.error("Could not initialize HTR Decoder!", e);
			this.setJobStatusFailed("Could not initialize HTR Decoder");
			return;
		}

		logger.debug("Running decoding for selected pages.");
		List<Integer> pageList;
		try {
			pageList = CoreUtils.parseRangeListStrToList(job.getPages(), nPages);
		} catch (IOException e) {
			logger.error("Could not parse page range String!");
			this.setJobStatusFailed("Could not parse page range String! " + job.getPages());
			return;
		}
		try {
			startHtrProcessOnPages(this.getJobStatus(), decoder, nrOfCores, modelName, job.getDocId(), pageList);
		} catch (SQLException | ReflectiveOperationException | TimeoutException | InterruptedException
				| IOException e) {
			logger.error(e.getMessage(), e);
			this.setJobStatusFailed(e.getMessage());
			return;
		}
		
		try {
			//send mail to user
			UserDao ud = new UserDao();
			TrpJobStatus job = this.getJobStatus();
			TrpUser user = ud.getUser(job.getUserId(), true);
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
		} catch (IOException | EntityNotFoundException | SQLException | IllegalArgumentException | ReflectiveOperationException e){
			logger.error("Could not send mail!", e);
		}
	}
}
