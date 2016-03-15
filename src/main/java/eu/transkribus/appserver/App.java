package eu.transkribus.appserver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.JobDelegator;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.logic.JobManager;

public class App {
	private static final Logger logger = LoggerFactory.getLogger(App.class);
	private static App app = null;
	private final JobManager jMan;
	private final JobDelegator delegator;
    
	private App(){
		delegator = JobDelegator.getInstance();
		
		//TODO create datasources for REST service and DB
		jMan = new JobManager();
		logger.info("DB Service name: " + DbConnection.getDbServiceName());
	}
	
	public static App getInstance(){
		if(app == null) app = new App();
		return app;
	}
	
	public void run() throws InterruptedException {
		logger.info("Starting up...");
		
		//Let the job delegator configure the executors for the specific tasks, define in the properties
		String[] jobTypes = Config.getString("types").split(",");
		delegator.configure(jobTypes);
		
		// Check jobs periodically and let the delegator provide them to the specific executors
		Connection conn = null;
		while(true && !Thread.interrupted()){	
			try {
				conn = DbConnection.getConnection();
				conn.setAutoCommit(false);
//				conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
				List<TrpJobStatus> jobs = jMan.getPendingJobs(conn);
				List<TrpJobStatus> submittedJobs = delegator.delegate(jobs);
				if(!submittedJobs.isEmpty()){
					jMan.setJobsToWaitingState(conn, submittedJobs);
					conn.commit();
				}
			} catch (SQLException | ReflectiveOperationException e) {
				logger.error("Could not retrieve jobs!", e);
				try {
					conn.rollback();
				} catch (SQLException e1) {}
			} finally {
				try {
					conn.close();
				} catch (SQLException e2) {}
			}
			//wait for 3 secs
			Thread.sleep(3000);
		}
	}
	
	public void stopApp () {
		logger.info("Shutting down app server");
		// Shutdown the executors
		delegator.shutdown();
		//TODO datasource shutdown
		DbConnection.shutDown();
	}

	private static void registerShutdownHook(final App app) {
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run(){
				app.stopApp();
			}
		});
	}
	
	public static void main( String[] args ) throws InterruptedException {
    	final App app  = App.getInstance();
    	registerShutdownHook(app);
    	app.run();
    }
}
