package eu.transkribus.appserver.logic.executors;

import java.util.Properties;

public class JobExecutorFactory {
	public static IJobExecutor createExecutor(Properties props){
		
		//TODO read the props and do stuff
		
		return new SimpleJobExecutor(Integer.parseInt(props.getProperty("nrOfCores")));
	}
}
