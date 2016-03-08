package eu.transkribus.appserver.logic.jobs;

import java.util.Observable;
import java.util.Observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.server.logic.JobManager.TrpJobStatusUpdate;

public abstract class ATrpJobRunnable extends Observable implements Runnable  {
	protected static final Logger logger = LoggerFactory.getLogger(ATrpJobRunnable.class);
	protected final TrpJobStatus job;
	protected final String jobId;

	protected ATrpJobRunnable(final TrpJobStatus job) {
		this.job = job;
		this.jobId = job.getJobId();
	}
	
	public abstract void run();
	
	protected class PassThroughObserver implements Observer {
		public void update(Observable obj, Object arg) {
			if (arg instanceof String) {
				updateStatus((String) arg);
			}
		}
	}
	protected void updateStatus(String string) {
		setChanged();
		notifyObservers(string);
	}
	
	protected void updateStatus(TrpJobStatusUpdate upd) {
		setChanged();
		notifyObservers(upd);
	}

	public String getJobId() {
		return jobId;
	}
	
	public TrpJobStatus getJobStatus(){
		return this.job;
	}
}
