package eu.transkribus.appserver.logic;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.executors.IJobExecutor;
import eu.transkribus.appserver.logic.executors.JobExecutorFactory;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.job.enums.JobType;

public class JobDelegator {
	private static final Logger logger = LoggerFactory.getLogger(JobDelegator.class);
	private static JobDelegator jobDelegator = null;
	
	Map<JobType, IJobExecutor> executorMap;
	
	private JobDelegator(){
		executorMap = new HashMap<>();
	}
	
	public static JobDelegator getInstance(){
		if(jobDelegator == null){
			jobDelegator = new JobDelegator();
		}
		return jobDelegator;
	}
	
	public void configure(final JobType... jobTypes) {
		for(JobType t : jobTypes){
			this.configure(t);
		}
	}
	
	public void configure(final JobType type) {
		IJobExecutor executor;
		
		Properties props = new Properties();
		final String propFileName = type.toString() + ".properties";
		try (InputStream is = JobDelegator.class.getClassLoader().getResourceAsStream(propFileName)) {
			if(is == null){
				throw new FileNotFoundException();
			}
			props.load(is);
		} catch (IOException ioe){
			logger.error("Configuration could not be loaded: " + propFileName, ioe);
			return;
		}
		
		executor = JobExecutorFactory.createExecutor(type, props);
		executorMap.put(type, executor);
	}
	
	public boolean delegate(TrpJobStatus job){
		boolean success = false;
		JobType type = job.getJobImpl().getTask().getJobType();
		if(executorMap.containsKey(type)){
			IJobExecutor jex = executorMap.get(type);
			logger.debug("Submitting job of type " + type + " into queue.");
			try {
				jex.submit(job);
				success = true;
			} catch (RejectedExecutionException ree){
				logger.info("Rejected execution for job: " + job);
				success = false;
			}
		}
		return success;
	}

	public void shutdown() {
		for(Entry<JobType, IJobExecutor> e : executorMap.entrySet()){
			IJobExecutor ex = e.getValue();
			logger.info("Shutting down job executor for job type: " + e.getKey().toString());
			ex.shutdown();
		}
	}

	public boolean isConfiguredForJob(TrpJobStatus j) {
		JobType type = j.getJobImpl().getTask().getJobType();
		return executorMap.containsKey(type);
	}

	public boolean hasResources(TrpJobStatus j) {
		JobType type = j.getJobImpl().getTask().getJobType();
		IJobExecutor ex = executorMap.get(type);
		return ex.hasResources();
	}
}	
