package eu.transkribus.appserver.logic.executors;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobExecutorFactory {
	private static final Logger logger = LoggerFactory.getLogger(JobExecutorFactory.class);

	public static AJobExecutor createExecutor(final String name, final Properties props){
		final String type = props.getProperty("executorType");
		switch(type){
		case "simple":
			logger.info("Instantiating SimpleJobExecutor: " + name);
			SimpleJobExecutor sje;
			try{
				sje = createSimpleJobExecutor(name, props);	
			} catch(NumberFormatException nfe){
				throw new IllegalArgumentException("Bad configuration for executor: " + name);
			}
			return sje;
		default:
			logger.info("No executor found with type: " + type);
			throw new IllegalArgumentException("Bad configuration for executor: " + name);
		}
	}
	
	private static SimpleJobExecutor createSimpleJobExecutor(final String name, final Properties props) throws NumberFormatException{
		final String qSizeStr = props.getProperty("qSize");
		final String corePoolSizeStr = props.getProperty("corePoolSize");
		final String maxPoolSizeStr = props.getProperty("maximumPoolSize");
		final String keepAliveTimeStr = props.getProperty("keepAliveTime");
		return new SimpleJobExecutor(name, Integer.parseInt(qSizeStr),
				Integer.parseInt(corePoolSizeStr), Integer.parseInt(maxPoolSizeStr), Integer.parseInt(keepAliveTimeStr));
	}
}
