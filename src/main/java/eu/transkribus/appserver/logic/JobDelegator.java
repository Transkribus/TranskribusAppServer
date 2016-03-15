package eu.transkribus.appserver.logic;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
	
	public void configure(final String... taskStrs) {
		for(String t : taskStrs){
			this.configure(t);
		}
	}
	
	public void configure(final String taskStr) {
		IJobExecutor executor;
		JobType type;
		try{
			type = JobType.valueOf(taskStr);
		} catch (Exception e){
			logger.info("Could not configure unknown task: " + taskStr);
			return;
		}
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
	
	public List<TrpJobStatus> delegate(List<TrpJobStatus> jobs){
		List<TrpJobStatus> submittedJobs = new LinkedList<>();
		for(TrpJobStatus j : jobs){
			JobType type = j.getJobImpl().getTask().getJobType();
			if(executorMap.containsKey(type)){
				IJobExecutor jex = executorMap.get(type);
				try {
					jex.submit(j);
					submittedJobs.add(j);
				} catch (RejectedExecutionException ree){
					logger.info("Rejected execution for job: " + j);
				}
			} else {
				logger.debug("Ignoring unconfigured job type: " + type);
			}
		}
		return submittedJobs;
	}

	public void shutdown() {
		for(Entry<JobType, IJobExecutor> e : executorMap.entrySet()){
			logger.info("Shutting down job executor for job type: " + e.getKey().toString());
			e.getValue().shutdown();
		}
	}
}	
