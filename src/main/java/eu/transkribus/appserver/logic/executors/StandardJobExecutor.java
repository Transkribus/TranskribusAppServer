package eu.transkribus.appserver.logic.executors;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import eu.transkribus.appserver.logic.jobs.AddBaselinesJob;
import eu.transkribus.core.model.beans.enums.Task;
import eu.transkribus.core.model.beans.job.TrpJobStatus;

public class StandardJobExecutor extends AJobExecutor {
	private static final Logger logger = LoggerFactory.getLogger(StandardJobExecutor.class);
	private static Map<String, Future<?>> futMap;
	private static BlockingQueue<Runnable> q;
	private static ThreadFactory tf;
	private static ThreadPoolExecutor ex;
	
	public StandardJobExecutor(final Task task, final int qSize, final int corePoolSize, 
			final int maximumPoolSize, final int keepAliveTime){
		super(task, "standard");
		futMap = new HashMap<>();
		q = new ArrayBlockingQueue<>(qSize);
		tf = new ThreadFactoryBuilder().setNameFormat(task.toString() + "-%d").build();
		ex = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, q, tf);
	}

	@Override
	public void submit(final TrpJobStatus j) {
		logger.debug("Trying to submit: " + j);
		
		//TODO Check if there are resources available to submit the job into the queue
		
		try {
			final Method m = AddBaselinesJob.class.getMethod("getSomething", TrpJobStatus.class);
			
			Runnable r = new Runnable(){

				@Override
				public void run() {
					try {
						m.invoke(AddBaselinesJob.class, j);
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};
			ex.submit(r);
		} catch (NoSuchMethodException | SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
