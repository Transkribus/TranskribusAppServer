package eu.transkribus.appserver.logic.jobs.standard;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;

public class DummyJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(DummyJob.class);
	
	public DummyJob(TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		try{
			this.setJobStatusProgress("Dummy job is running");
			TrpJobStatus job = this.getJobStatus();
			final String msg = "\nDocId = " + job.getDocId() 
					+ "\nPageNr = " + job.getPages() 
					+ "\nuserId = " + job.getUserId() 
					+ "\nuserName = " + job.getUserName()
					+ "\njobId = " + job.getJobId(); 
					
			
			logger.info("Dummy job says: " + msg);
			logger.info("Job status = " + job.toString());
			
		} catch(IOException e){
			this.setJobStatusFailed(e.getMessage());
			logger.error("Exception in Dummy job!", e);
		}
	}

}
