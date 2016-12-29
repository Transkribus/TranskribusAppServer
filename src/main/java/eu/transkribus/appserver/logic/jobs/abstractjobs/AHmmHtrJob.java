package eu.transkribus.appserver.logic.jobs.abstractjobs;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import javax.persistence.EntityNotFoundException;
import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.persistence.dao.JobDao;
import eu.transkribus.persistence.jobs.htr.logic.AHtrManager;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;

public abstract class AHmmHtrJob extends ATrpJobRunnable {
	

	private static final Logger logger = LoggerFactory.getLogger(AHmmHtrJob.class);
	protected JobDao jDao = new JobDao();
	
	protected AHmmHtrJob(TrpJobStatus job) {
		super(job);
		// TODO Auto-generated constructor stub
	}
	
	protected void startHtrProcess(final TrpJobStatus job, final AHtrManager man, final int nrOfCores, final String modelName, final List<Integer> docIds) throws SQLException, ReflectiveOperationException, TimeoutException, InterruptedException, JobCanceledException{		
		for(int docId : docIds){
			try {
				man.addDocument(docId);
			} catch (IOException | EntityNotFoundException | ReflectiveOperationException | SQLException | JAXBException e) {
				logger.error("Could not add document to HTR process!", e);
				job.setDescription("Could not add Document to HTR process: " + e.getMessage());
				job.setState(TrpJobStatus.FAILED);
				job.setSuccess(false);
				job.setEndTime(System.currentTimeMillis());
				jDao.updateJob(job);
				return;
			}
		}
		
		startProcessing(job, man, nrOfCores);
	}
	
	protected void startHtrProcessOnPage(final TrpJobStatus job, final AHtrManager man, final int nrOfCores, final String modelName, final int docId, final int pageNr) throws SQLException, ReflectiveOperationException, TimeoutException, InterruptedException, JobCanceledException{		
		try {
			man.addPage(docId, pageNr);
		} catch (IOException | EntityNotFoundException | ReflectiveOperationException | SQLException | JAXBException e) {
			logger.error("Could not add page to HTR process!", e);
			job.setDescription("Could not add Document to HTR process: " + e.getMessage());
			job.setState(TrpJobStatus.FAILED);
			job.setSuccess(false);
			job.setEndTime(System.currentTimeMillis());
			jDao.updateJob(job);
			return;
		}
	
		startProcessing(job, man, nrOfCores);
	}
	
	protected void startHtrProcessOnPages(final TrpJobStatus job, final AHtrManager man, final int nrOfCores, final String modelName, final int docId, final List<Integer> pages) throws SQLException, ReflectiveOperationException, TimeoutException, InterruptedException, JobCanceledException{		
		try {
			for(Integer i : pages){
				man.addPage(docId, i+1);
			}
		} catch (IOException | EntityNotFoundException | ReflectiveOperationException | SQLException | JAXBException e) {
			logger.error("Could not add page to HTR process!", e);
			job.setDescription("Could not add Document to HTR process: " + e.getMessage());
			job.setState(TrpJobStatus.FAILED);
			job.setSuccess(false);
			job.setEndTime(System.currentTimeMillis());
			jDao.updateJob(job);
			return;
		}
	
		startProcessing(job, man, nrOfCores);
	}

	protected void startProcessing(TrpJobStatus job, final AHtrManager man, final int nrOfCores) throws SQLException, ReflectiveOperationException {
		try {
			man.doProcess(nrOfCores);
//			man.destroy();
		} catch (JobCanceledException e) {
			logger.error("HTR process was canceled!");
			job.setDescription("HTR process was canceled.");
			job.setState(TrpJobStatus.CANCELED);
			job.setSuccess(false);
			job.setEndTime(System.currentTimeMillis());
			jDao.updateJob(job);
			return;
		} catch (Exception e) {
			logger.error("HTR process failed!", e);
			job.setDescription("HTR process failed: " + e.getMessage());
			job.setState(TrpJobStatus.FAILED);
			job.setSuccess(false);
			job.setEndTime(System.currentTimeMillis());
			jDao.updateJob(job);
			return;
		}
		
		job = jDao.getJobById(job.getJobId());
		if(!job.getState().equals(TrpJobStatus.CANCELED)){
			job.setDescription("HTR process done");
			Properties p;
			try {
				p = CoreUtils.readPropertiesFromString(job.getJobData());
				p.setProperty(JobConst.PROP_STATE, JobConst.STATE_FINISH);
				job.setJobData(CoreUtils.writePropertiesToString(p));
			} catch (IOException e) {
				logger.error("Could not parse job data!! Trying hard replace...");
				job.setJobData(job.getJobData().replaceFirst(JobConst.STATE_HTR, JobConst.STATE_FINISH));
			}
			jDao.updateJob(job);
		}
	}

}
