package eu.transkribus.appserver.logic.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.server.logic.JobManager;

public class DeleteDocJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(DeleteDocJob.class);
	private final String userName;
	private final int docId;
	
	public DeleteDocJob(final TrpJobStatus job) {
		super(job);
		userName = job.getUserName();
		docId = job.getDocId();
	}

	@Override
	public void run() {
		logger.info("Doc creation thread started.");
		DocManager docMan = new DocManager();
		try {
			updateStatus("Deleting doc ID = " + docId);
			logger.warn("User " + userName + " requested deletion of document ID = " + docId);
			
			docMan.deleteDoc(docId);

			JobManager.getInstance().finishJob(jobId, "DONE", true);
			logger.debug("Deleted!");		
		} catch (Exception e) {
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
//				JobManager.getInstance().updateJob(jobId, TrpJob.FAILED, e.getMessage());
				logger.error(e.getMessage(), e);
			} catch (Exception e2){
				logger.error(e.getMessage(), e2);
			}
		}	
	}

}
