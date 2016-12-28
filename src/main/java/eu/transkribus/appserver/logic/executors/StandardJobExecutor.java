package eu.transkribus.appserver.logic.executors;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.job.enums.JobImpl;
import eu.transkribus.core.model.beans.job.enums.JobType;
import eu.transkribus.persistence.jobs.abstractJobs.ATrpJobRunnable;

public class StandardJobExecutor extends AJobExecutor {
	private static final Logger logger = LoggerFactory.getLogger(StandardJobExecutor.class);
	private static Map<String, Future<?>> futMap;
	private static BlockingQueue<Runnable> q;
	private static ThreadFactory tf;
	private static ThreadPoolExecutor ex;
	
	private final static String JOBS_PACKAGE = "eu.transkribus.appserver.logic.jobs.standard.";
	
	public StandardJobExecutor(final JobType task, final int qSize, final int corePoolSize, 
			final int maximumPoolSize, final int keepAliveTime){
		super(task, "standard");
		futMap = new HashMap<>();
		q = new ArrayBlockingQueue<>(qSize);
		tf = new ThreadFactoryBuilder().setNameFormat(task.toString() + "-%d").build();
		ex = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, q, tf);
	}

	@Override
	public void submit(final TrpJobStatus j) throws RejectedExecutionException {
		//TODO Check if there are resources available to submit the job into the queue

		JobImpl impl = j.getJobImpl();
		final String clazzName = JOBS_PACKAGE + impl.getClassName();
		ATrpJobRunnable o;
		logger.info("Instantiating job class: " + clazzName);
		try{
			//try to load the class
			Class<?> clazz = this.getClass().getClassLoader().loadClass(clazzName);
			//find constructor that takes the jobStatus as argument
			Constructor<?> constr = clazz.getConstructor(TrpJobStatus.class);
			//use constructor
			o = (ATrpJobRunnable)constr.newInstance(j);
			//submit job into queue
			logger.debug("Trying to submit: " + j);
			
			Future<?> f = ex.submit(o);
			futMap.put(j.getJobId(), f);
		} catch(ReflectiveOperationException e){
			//TODO stuff
			logger.error("Could not load a job of type " + j.getType() + "!", e);
		}
	}
	
	@Override
	public void shutdown() {
		logger.debug("shutting down executor: " + task.toString());
		ex.shutdownNow(); // sends interrupts to all running threads and stops accepting new threads!
		try {
			ex.awaitTermination(5, TimeUnit.SECONDS); // wait at most 5 sec's for all threads to be stopped!
		} catch (InterruptedException e) {
			logger.error("Error shutting down executor: "+e.getMessage(), e);
		}
	}
}
