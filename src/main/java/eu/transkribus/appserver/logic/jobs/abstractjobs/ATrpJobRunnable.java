package eu.transkribus.appserver.logic.jobs.abstractjobs;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.JobManager;
import eu.transkribus.persistence.util.MailUtils;

public abstract class ATrpJobRunnable extends Observable implements Runnable  {
	protected static final Logger logger = LoggerFactory.getLogger(ATrpJobRunnable.class);
	protected TrpJobStatus job;
	protected Properties jobProps;
	protected JobManager jMan;
	
	protected ATrpJobRunnable(final TrpJobStatus job) {
		this.job = job;
		this.jobProps = job.getJobDataProps();
		this.jMan = new JobManager();
	}
	
	@Override
	public void run() {
		MDC.put("jobId", getJobId());
		try {
			this.setJobStatusStarted("Starting...");
			//execute the job's main workflow
			doProcess();
			if(!job.getState().equals(TrpJobStatus.FAILED)) {
				this.setJobStatusFinished();
			}
		} catch (JobCanceledException jce) {
			// this job is already set to state=CANCELLED and thus we don't have to do anything here
			return;
		} catch (Throwable e) {
			// this is only reached when an unhandled exception is thrown and should never happen!
			setJobStatusFailed("An unexpected error occurred!", e);
		} finally {
			MDC.remove("jobId");
		}
	}
	
	public abstract void doProcess() throws JobCanceledException;
	
	protected TrpJobStatus getJobStatus() throws IOException{
		if(job == null){
			this.updateJobStatusFromDb();
		}
		return job;
	}
	
	
	public String getJobId() {
		return job.getJobId();
	}
		
	protected void setJobStatusStarted(String desc) throws JobCanceledException {
		try{
			this.updateJobStatusFromDb();
			if(job.isCancelled()){
				throw new JobCanceledException();
			}
			job.setState(TrpJobStatus.RUNNING);
			job.setStartTime(System.currentTimeMillis());
			job.setDescription(desc);
			this.storeJobStatusOnDb();
		} catch (IOException ioe){
			logger.error("Could not update JobStatus!", ioe);
		}
	}
	
	protected void setJobStatusProgress(String desc) throws JobCanceledException {
		logger.info(desc);
		try {
			this.updateJobStatusFromDb();
			if(job.isCancelled()){
				logger.info("Job is canceled!");
				job.setDescription("Canceled");
				job.setEndTime(System.currentTimeMillis());
				this.storeJobStatusOnDb();
				throw new JobCanceledException();
			}
			if(!job.isRunning()){
				job.setState(TrpJobStatus.RUNNING);
			}
			job.setDescription(desc);
			this.storeJobStatusOnDb();
		} catch (IOException ioe){
			logger.error("Could not update JobStatus!", ioe);
		}
	}
	
	protected void setJobStatusResult(String result) throws JobCanceledException {
		logger.info(result);
		try {
			this.updateJobStatusFromDb();
			if(job.isCancelled()){
				logger.info("Job is canceled!");
				job.setDescription("Canceled");
				job.setEndTime(System.currentTimeMillis());
				this.storeJobStatusOnDb();
				throw new JobCanceledException();
			}
			if(!job.isRunning()){
				job.setState(TrpJobStatus.RUNNING);
			}
			job.setResult(result);
			this.storeJobStatusOnDb();
		} catch (IOException ioe){
			logger.error("Could not update JobStatus!", ioe);
		}
	}
	
	protected void setJobStatusFinished() {
		try {
			this.updateJobStatusFromDb();
			job.setState(TrpJobStatus.FINISHED);
			job.setSuccess(true);
			job.setDescription("Done");
			job.setEndTime(System.currentTimeMillis());
			this.storeJobStatusOnDb();
		} catch (IOException ioe){
			logger.error("Could not update JobStatus!", ioe);
		}
	}
	
	protected void setJobStatusFailed(String desc, Throwable e) {
		if(e != null){
			logger.error(desc, e);
		} else {
			logger.error(desc);
		}
		try{
			final long endTime = System.currentTimeMillis();
			this.updateJobStatusFromDb();
			job.setState(TrpJobStatus.FAILED);
			job.setSuccess(false);
			job.setDescription(desc + (e != null ? ": " + e.getMessage() : ""));
			job.setEndTime(endTime);
			this.storeJobStatusOnDb();
			
			final String jobs = "jobs@transkribus.eu";
			final String subject = "A " + job.getType() + " job failed!";
			final String msg = job.getDescription() + "\n"
					+ "User: " + job.getUserName() + "\n"
					+ "Doc-ID: " + job.getDocId() + " | Page NR.: " + job.getPageNr() + "\n"
 					+ "Time: " + new Date(endTime).toString()
 					+ "\n\n\n" + job.toString();					
			MailUtils.sendMailFromUibkAddress(jobs, jobs, subject, msg, null, false, false);
			
		} catch (IOException ioe){
			logger.error(ioe.getMessage(), ioe);
		}
	}
	
	protected void setJobStatusFailed(String desc) {
		setJobStatusFailed(desc, null);
	}
	
	protected void updateJobStatusFromDb() throws IOException {
		try {
			job = jMan.getJob(getJobId());
		} catch (Exception e) {
			throw new IOException("Could not get job", e);
		}
	}
	
	protected void storeJobStatusOnDb() throws IOException{
		try {
			jMan.updateJob(job);
		} catch (SQLException | ReflectiveOperationException e) {
			throw new IOException("Could not update job", e);
		}
	}
	
	protected class JobUpdateObserver implements Observer {
		public JobUpdateObserver() {}

		@Override
		public void update(Observable o, Object arg) {
			if(arg instanceof String) {
				try {
					setJobStatusProgress((String) arg);
				} catch (JobCanceledException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}
	}
	
	protected String getProperty(String key) {
		return jobProps.getProperty(key);
	}
	
	protected Integer getIntProperty(String key) {
		String propStr = getProperty(key);
		Integer retVal = null;
		try {
			retVal = Integer.parseInt(propStr);
		} catch (NumberFormatException nfe) {}
		return retVal;
	}
	
	protected Boolean getBoolProperty(String key) {
		return Boolean.parseBoolean(getProperty(key));
	}
	
	protected List<String> getStringListProperty(String key) {
		List<String> result = new LinkedList<>();
		String str = jobProps.getProperty(key);
		if(str != null && !str.isEmpty()) {
			String[] arr = str.split(",");
			for(String s : arr) {
				result.add(s);
			}
		}
		return result;
	}
	
	protected List<Integer> getIntListProperty(String key) {
		List<Integer> result = new LinkedList<>();
		String str = jobProps.getProperty(key);
		if(str != null && !str.isEmpty()) {
			String[] arr = str.split(",");
			for(String s : arr) {
				result.add(Integer.parseInt(s));
			}
		}
		return result;
	}
	
}