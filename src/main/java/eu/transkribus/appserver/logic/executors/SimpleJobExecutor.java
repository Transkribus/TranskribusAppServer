package eu.transkribus.appserver.logic.executors;

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

public class SimpleJobExecutor implements IJobExecutor {
	private static final Logger logger = LoggerFactory.getLogger(SimpleJobExecutor.class);
	private final String name;
	private static Map<String, Future<?>> futMap;
	private static BlockingQueue<Runnable> q;
	private static ThreadFactory tf;
	private static ThreadPoolExecutor ex;
	
	public SimpleJobExecutor(final String name, final int qSize, final int corePoolSize, 
			final int maximumPoolSize, final int keepAliveTime){
		this.name = name;
		futMap = new HashMap<>();
		q = new ArrayBlockingQueue<>(qSize);
		tf = new ThreadFactoryBuilder().setNameFormat(name + "-%d").build();
		ex = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, q, tf);
	}

	@Override
	public void shutdown() {
		logger.debug("shutting down executor: " + name);
		ex.shutdownNow(); // sends interrupts to all running threads and stops accepting new threads!
		try {
			ex.awaitTermination(5, TimeUnit.SECONDS); // wait at most 5 sec's for all threads to be stopped!
		} catch (InterruptedException e) {
			logger.error("Error shutting down executor: "+e.getMessage(), e);
		}
	}
	
	public String getName() {
		return this.name;
	}
}