package eu.transkribus.appserver;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.MDC;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.enums.JobType;
import eu.transkribus.interfaces.types.util.SysPathUtils;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.logic.QuartzClusteredSchedulerManager;

public class App {
	static {	
		MDC.put("appName", Config.getString("appName"));
	}
	private static final Logger logger = LoggerFactory.getLogger(App.class);
	private static App app = null;
	private final QuartzClusteredSchedulerManager qMan;
	// private final JobDelegator delegator;

	private App() throws IOException {
		// delegator = JobDelegator.getInstance();

		// TODO create datasources for REST service and DB
		// jMan = new JobManager();

		Config.checkSetup();

		qMan = QuartzClusteredSchedulerManager.getInstance();

		final String jobTypes = Config.getString("types");
		final JobType[] jobTypesArr = parseJobTypes(jobTypes);

		qMan.configure(jobTypesArr);

		logger.info("DB Service name: " + DbConnection.getDbServiceName());

		SysPathUtils.addDirToPath(Config.getString("libdir"));
		SysPathUtils.addDirToPath(Config.getString("opencvdir"));

		System.out.println("libpath = " + SysPathUtils.getPath());

		try {
			System.loadLibrary("TranskribusInterfacesWrapper");
		} catch (UnsatisfiedLinkError e) {
			throw new RuntimeException("Could not find .so file(s):" + e.getMessage(), e);
		}

	}

	public static App getInstance() throws IOException {
		if (app == null)
			app = new App();
		return app;
	}

	public void run() throws InterruptedException {
		logger.info("Starting up...");
		try {
			qMan.startSchedulers();
			logger.info("All schedulers running!");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// //Let the job delegator configure the executors for the specific
		// tasks, define in the properties
		// String[] jobTypes = Config.getString("types").split(",");
		// delegator.configure(jobTypes);
		//
		// // Check jobs periodically and let the delegator provide them to the
		// specific executors
		// Connection conn = null;
		// while(true && !Thread.interrupted()){
		// try {
		// conn = DbConnection.getConnection();
		// conn.setAutoCommit(false);
		//// conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
		// List<TrpJobStatus> jobs = jMan.getPendingJobs(conn);
		// List<TrpJobStatus> submittedJobs = delegator.delegate(jobs);
		// if(!submittedJobs.isEmpty()){
		// jMan.setJobsToWaitingState(conn, submittedJobs);
		// conn.commit();
		// }
		// } catch (SQLException | ReflectiveOperationException e) {
		// logger.error("Could not retrieve jobs!", e);
		// try {
		// conn.rollback();
		// } catch (SQLException e1) {}
		// } finally {
		// try {
		// conn.close();
		// } catch (SQLException e2) {}
		// }
		// //wait for 3 secs
		// Thread.sleep(3000);
		// }
	}

	public void shutdown(boolean waitForJobsToComplete) throws SchedulerException {
		logger.info("Shutting down app server");
		// Shutdown the executors
		// delegator.shutdown();
		qMan.stopSchedulers(waitForJobsToComplete);
		// TODO datasource shutdown
		DbConnection.shutDown();
	}

	private static void registerShutdownHook(final App app) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					app.shutdown(false);
				} catch (SchedulerException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Parses the CSV String from the properties file and converts it to an
	 * array of enum types
	 * 
	 * @param jobTypesStr
	 *            a CSV String with valid JobType values
	 * @return An array with according JobType enum values
	 */
	private JobType[] parseJobTypes(String jobTypesStr) {
		String[] jobTypes = jobTypesStr.split(",");

		ArrayList<JobType> jobTypesList = new ArrayList<>(jobTypes.length);
		for (String s : jobTypes) {
			try {
				JobType j = JobType.valueOf(s);
				jobTypesList.add(j);
			} catch (IllegalArgumentException e) {
				logger.error("Illegal job Type in Config: " + s);
				continue;
			}
		}
		return jobTypesList.toArray(new JobType[jobTypesList.size()]);
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		final App app = App.getInstance();
		registerShutdownHook(app);
		app.run();
	}
}
