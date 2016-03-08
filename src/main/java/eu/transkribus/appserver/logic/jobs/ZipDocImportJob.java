package eu.transkribus.appserver.logic.jobs;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.io.TrpDocPacker;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.server.TrpServerConf;
import eu.transkribus.server.logic.JobManager;

public class ZipDocImportJob extends ADocImportJob {
	private static final Logger logger = LoggerFactory.getLogger(ZipDocImportJob.class);
	private final File zipFile;
	private final int collectionId;
	
	public ZipDocImportJob(final int collectionId, final TrpJobStatus job, final File zipFile) {
		super(job);
		this.zipFile = zipFile;
		this.collectionId = collectionId;
	}

	@Override
	public void run() {
		logger.info("Doc creation thread started.");
		
		try {
			String storagePath = TrpServerConf.getString("upload_storage");
			
			if (zipFile == null) {
				throw new IOException("zipFile is null");
			} else if(!zipFile.exists()) {
				throw new IOException("zipFile at " + zipFile.getAbsolutePath() + " does not exist.");
			}

			final String dirName = FilenameUtils.getBaseName(zipFile.getName());
			if (!storagePath.endsWith(File.separator)) {
				storagePath += File.separator;
			}
			storagePath += dirName;

			//unpack zipFile to persistent storage
			TrpDocPacker packer = new TrpDocPacker();
			PassThroughObserver o = new PassThroughObserver();
			packer.addObserver(o);
			logger.debug("Unpacking zipFile " + zipFile.getName() + " -> " + storagePath);
			TrpDoc doc = packer.unpackDoc(zipFile, storagePath);
			
			super.importDoc(collectionId, doc);
			
		} catch (Exception e) {
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
//				JobManager.getInstance().updateJob(jobId, TrpJob.FAILED, e.getMessage());
				logger.error(e.getMessage(), e);
			} catch (Exception e2){
				logger.error(e.getMessage(), e2);
			}
		} finally {
			zipFile.delete();
		}
	}
}
