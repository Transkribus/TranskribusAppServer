package eu.transkribus.appserver.logic.executors;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.enums.JobType;

public class JobExecutorFactory {
	private static final Logger logger = LoggerFactory.getLogger(JobExecutorFactory.class);

	public static AJobExecutor createExecutor(final JobType task, final Properties props){
		final String type = props.getProperty("executorType");
		switch(type){
			case "standard":
				logger.info("Instantiating StandardJobExecutor: " + task);
				StandardJobExecutor sje;
				try{
					sje = createStandardJobExecutor(task, props);	
				} catch(NumberFormatException nfe){
					throw new IllegalArgumentException("Bad configuration for standard executor: " + task);
				}
				return sje;
			default:
				logger.info("No executor found with type: " + type);
				throw new IllegalArgumentException("Bad configuration for executor: " + task);
		}
	}
	
	private static StandardJobExecutor createStandardJobExecutor(final JobType task, final Properties props) throws NumberFormatException{
		final String qSizeStr = props.getProperty("qSize");
		final String corePoolSizeStr = props.getProperty("corePoolSize");
		final String maxPoolSizeStr = props.getProperty("maximumPoolSize");
		final String keepAliveTimeStr = props.getProperty("keepAliveTime");
		return new StandardJobExecutor(task, Integer.parseInt(qSizeStr),
				Integer.parseInt(corePoolSizeStr), Integer.parseInt(maxPoolSizeStr), Integer.parseInt(keepAliveTimeStr));
	}
}
