package eu.transkribus.appserver.logic.jobs;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.util.CoreUtils;

public abstract class AResumableTrpJobRunnable extends ATrpJobRunnable  {
	protected static final Logger logger = LoggerFactory.getLogger(AResumableTrpJobRunnable.class);
	
	protected AResumableTrpJobRunnable(final TrpJobStatus job) {
		super(job);
	}
		
	public void resume() throws IOException{
		final String jobDataStr = job.getJobData();
		logger.info("Resume from jobData: " + jobDataStr);
		Properties p;
		try{
			p = CoreUtils.readPropertiesFromString(jobDataStr);
		} catch (Exception e){
			logger.info("Could not parse jobData = " + jobDataStr, e);
			p = new Properties();
		}
		resume(p);
	}
	
	/** Method maps params to the job class' fields that are necessary for resuming
	 * @param params
	 */
	protected abstract void resume(Properties props);
	
	protected abstract Properties buildJobData();
	
	/** Method serializes job class' fields into a jobData String
	 * @return
	 * @throws IOException 
	 */
	protected String buildJobDataStr() {
		Properties p = buildJobData();
		String str = CoreUtils.writePropertiesToString(p);
		return str;
	}
}
