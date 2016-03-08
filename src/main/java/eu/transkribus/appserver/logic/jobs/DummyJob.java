package eu.transkribus.appserver.logic.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.server.logic.JobManager;

public class DummyJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(DummyJob.class);
	public DummyJob(final TrpJobStatus job) {
		super(job);
	}

	@Override
	public void run() {
		logger.info("Dummy Job " + this.toString() + " START");
		try {
			Thread.sleep(5000);
			JobManager.getInstance().finishJob(jobId, "DONE", true);
		} catch (Exception e) {
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
			} catch (Exception ex) {
				logger.error("Could not update job in DB! " + jobId);
				ex.printStackTrace();
			}
			logger.error(e.getMessage(), e);
			e.printStackTrace();
		}
		logger.info("Dummy Job END");
	}
}
