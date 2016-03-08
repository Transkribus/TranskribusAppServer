package eu.transkribus.appserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.persistence.DbConnection;

public class App {
	private static final Logger logger = LoggerFactory.getLogger(App.class);
	private static int nrOfCores;
	
    public static void main( String[] args ) throws InterruptedException {
    	logger.info("Starting up...");
		
		registerShutdownHook();
		
		logger.info("Using " + nrOfCores + " cores");
		
		logger.info("DB Service name: " + DbConnection.getDbServiceName());
		
		while(true && !Thread.interrupted()){
			
			
			
			//wait for 3 secs
			Thread.sleep(3000);
		}
    }

	private static void registerShutdownHook(final Thread... threads) {
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run(){
				logger.info("Shutting down app server");
				for(Thread t : threads){
					t.interrupt();
				}
			}
		});
	}
}
