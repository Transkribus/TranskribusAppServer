package eu.transkribus.appserver;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.JobDelegator;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.job.enums.JobType;
import eu.transkribus.interfaces.types.util.SysPathUtils;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.logic.JobManager;

public class App {
	static {
		MDC.put("appName", Config.getString("appName"));
	}

	private static final Logger logger = LoggerFactory.getLogger(App.class);
	private static App app = null;

	private final JobDelegator delegator;
	private final JobManager jMan;

	private App() throws IOException {
		delegator = JobDelegator.getInstance();

		// TODO create datasources for REST service and DB
		jMan = new JobManager();

		final String jobTypes = Config.getString("types");
		final JobType[] jobTypesArr = parseJobTypes(jobTypes);

		Config.checkSetup(jobTypesArr);

		logger.info("DB Service name: " + DbConnection.getDbServiceName());

		SysPathUtils.addDirToPath(Config.getString("libdir"));
		SysPathUtils.addDirToPath(Config.getString("opencvdir"));

		System.out.println("libpath = " + SysPathUtils.getPath());

		try {
			System.loadLibrary("TranskribusInterfacesWrapper");
		} catch (UnsatisfiedLinkError e) {
			throw new RuntimeException("Could not load .so file(s):" + e.getMessage(), e);
		}

	}

	public static App getInstance() throws IOException {
		if (app == null)
			app = new App();
		return app;
	}

	public void run() throws InterruptedException {
		logger.info("Starting up...");

		// Let the job delegator configure the executors for the specific
		// tasks, defined in the properties
		JobType[] jobTypes = parseJobTypes(Config.getString("types"));
		delegator.configure(jobTypes);

		// Check jobs periodically and let the delegator provide them to the
		// specific executors
		while (true && !Thread.interrupted()) {
			try (Connection conn = DbConnection.getConnection();){
				List<TrpJobStatus> jobs = jMan.getPendingJobs(conn);
				for(TrpJobStatus j : jobs) {
					logger.info("Found pending job with impl: " + j.getJobImpl());
					logger.debug(""+j);
					//check if delegator is configured for job type and try checking out the job, i.e. setting it to waiting state
					if(delegator.isConfiguredForJob(j)) {
						if(jMan.setJobToWaitingState(conn, j)){
							//if that worked, actually schedule the job
							if(!delegator.delegate(j)) {
								//if that fails, release the job again
								jMan.resetJob(conn, j);
							}
						}
					} else {
						continue;
					}
				}
			} catch (SQLException | ReflectiveOperationException e) {
				logger.error("Could not access DB!", e);
			}
			// wait for 3 secs
			Thread.sleep(3000);
		}
	}

	public void shutdown() {
		logger.info("Shutting down app server");
		// Shutdown the executors
		delegator.shutdown();
		// TODO datasource shutdown
		DbConnection.shutDown();
	}

	private static void registerShutdownHook(final App app) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				app.shutdown();
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
