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

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.job.enums.JobImpl;
import eu.transkribus.core.model.beans.job.enums.JobType;

public class StandardJobExecutor extends AJobExecutor {
	private static final Logger logger = LoggerFactory.getLogger(StandardJobExecutor.class);
	private Map<String, Future<?>> futMap;
	private BlockingQueue<Runnable> q;
	private ThreadFactory tf;
	private ThreadPoolExecutor ex;
	
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
		//Check if there are resources available to submit the job into the queue
		if(ex.getActiveCount() >= ex.getMaximumPoolSize()) {
			logger.debug("Got " + ex.getActiveCount() + " active threads and maxPoolSize is " + ex.getMaximumPoolSize());
			throw new RejectedExecutionException("Threadpool is exceeded at the moment.");
		}
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

	@Override
	public boolean hasResources() {
		logger.debug("Active: " + ex.getActiveCount() + " - corePoolSize: " + ex.getCorePoolSize());
		return ex.getActiveCount() < ex.getCorePoolSize();
	}
}
