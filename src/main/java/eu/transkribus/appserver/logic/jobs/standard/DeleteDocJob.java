package eu.transkribus.appserver.logic.jobs.standard;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.SolrManager;

public class DeleteDocJob extends ATrpJobRunnable {
	
	private static final Logger logger = LoggerFactory.getLogger(DeleteDocJob.class);

	public DeleteDocJob(TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		logger.debug("Doc deletion thread started.");
		DocManager docMan = new DocManager();
		try {
			logger.info("Deleting doc ID = " + job.getDocId());
			logger.warn("User " + job.getUserName() + " requested deletion of document ID = " + job.getDocId());
			this.setJobStatusProgress("Deleting document...");
			docMan.deleteDoc(job.getDocId());
			SolrManager.getInstance().removeDocIndex(job.getDocId());
			logger.info("Deleted!");
		} catch (SQLException e) {
			setJobStatusFailed("Failed: " + e.getMessage());
			return;
		}
	}

}
