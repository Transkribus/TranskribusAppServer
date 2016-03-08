package eu.transkribus.appserver.logic.jobs;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.io.LocalDocReader;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.mets.Mets;
import eu.transkribus.server.logic.JobManager;

public class DocImportJob extends ADocImportJob {
	private static final Logger logger = LoggerFactory.getLogger(DocImportJob.class);
	private final File docDir;
	private final Mets mets;
	private final int collectionId;
	
	public DocImportJob(final int collectionId, final TrpJobStatus job, Mets mets, final File docDir) {
		super(job);
		this.docDir = docDir;
		this.mets = mets;
		this.collectionId = collectionId;
	}

	@Override
	public void run() {
		logger.info("Doc creation thread started.");
		
		try {
			TrpDoc doc = LocalDocReader.load(mets, docDir);
			super.importDoc(collectionId, doc);		
		} catch (Exception e) {
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
//				JobManager.getInstance().updateJob(jobId, TrpJob.FAILED, e.getMessage());
				logger.error(e.getMessage(), e);
			} catch (Exception e2){
				logger.error(e.getMessage(), e2);
			}
		}	
	}

}
