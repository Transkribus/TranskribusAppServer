package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ADocImportJob;
import eu.transkribus.core.io.TrpDocPacker;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.io.LocalStorage;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;

public class ZipDocImportJob extends ADocImportJob {
	private static final Logger logger = LoggerFactory.getLogger(ZipDocImportJob.class);
	
	public ZipDocImportJob(TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		logger.info("Doc creation thread started.");
		
		File zipFile = new File(path);
		
		try {
			String storagePath = LocalStorage.getUploadStorage().getAbsolutePath();
			
			if(!zipFile.exists()) {
				throw new IOException("zipFile at " + zipFile.getAbsolutePath() + " does not exist.");
			}

			final String dirName = FilenameUtils.getBaseName(zipFile.getName());
			if (!storagePath.endsWith(File.separator)) {
				storagePath += File.separator;
			}
			storagePath += dirName;

			//unpack zipFile to persistent storage
			TrpDocPacker packer = new TrpDocPacker();
//			PassThroughObserver o = new PassThroughObserver();
//			packer.addObserver(o);
			setJobStatusProgress("Unpacking document files on server...");
			
			logger.debug("Unpacking zipFile " + zipFile.getName() + " -> " + storagePath);
			TrpDoc doc = packer.unpackDoc(zipFile, storagePath);
			try{
				super.importDoc(colId, job.getDocId(), doc, job.getUserId(), job.getUserName());
			} catch (JobCanceledException jce) {
				logger.error("Job is canceled!");
				return;
			}
		} catch (IOException e) {
			setJobStatusFailed(e.getMessage(), e);
			return;
		} catch (Exception e) {
			setJobStatusFailed(e.getMessage(), e);
			return;
		} finally {
			zipFile.delete();
		}
	}
}
