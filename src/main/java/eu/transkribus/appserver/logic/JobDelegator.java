package eu.transkribus.appserver.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.executors.IJobExecutor;
import eu.transkribus.appserver.logic.executors.JobExecutorFactory;
import eu.transkribus.core.model.beans.enums.Task;
import eu.transkribus.core.model.beans.job.TrpJobStatus;

public class JobDelegator {
	private static final Logger logger = LoggerFactory.getLogger(JobDelegator.class);
	private static JobDelegator jobEx = null;
	
	Map<Task, IJobExecutor> executorMap;
	
	private JobDelegator(){
		executorMap = new HashMap<>();
	}
	
	public static JobDelegator getInstance(){
		if(jobEx == null){
			jobEx = new JobDelegator();
		}
		return jobEx;
	}
	
	public void configure(String... taskStrs) {
		for(String t : taskStrs){
			this.configure(t);
		}
	}
	
	public void configure(String taskStr) {
		IJobExecutor e;
		Task task;
		try{
			task = Task.valueOf(taskStr);
		} catch (Exception ex){
			logger.info("Could not configure unknown task: " + taskStr);
			return;
		}
		Properties props = new Properties();
		InputStream is = JobDelegator.class.getClassLoader().getResourceAsStream(task.toString() + ".properties");
		if(is == null){
			logger.info("Configuration could not be found for task: " + task.toString());
			return;
		}
		try {
			props.load(is);
		} catch (IOException ioe){
			logger.error("Configuration could not be loaded for task: " + task.toString(), ioe);
			return;
		}
		
		e = JobExecutorFactory.createExecutor(props);
		executorMap.put(task, e);
	}
	
	public void delegate(List<TrpJobStatus> jobs){
		
	}

	public void shutdown() {
		// TODO Auto-generated method stub
		
	}
}	
