package eu.transkribus.appserver.logic.jobs;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.io.LocalDocReader;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.dao.DocumentDao;
import eu.transkribus.server.logic.JobManager;


/** A simple doc import job that needs only the path to the document (no mets, no zip...)
 * @author philip
 *
 */
public class SimpleDocImportJob extends ADocImportJob {
	private static final Logger logger = LoggerFactory.getLogger(SimpleDocImportJob.class);
	private final String path;
	private final int collectionId;
	
	public SimpleDocImportJob(final int collectionId, final TrpJobStatus job, final File dir) {
		this(collectionId, job, dir.getAbsolutePath());
	}
	
	public SimpleDocImportJob(final int collectionId, final TrpJobStatus job, final String path) {
		super(job);
		job.setJobData(path);
		this.path = path;
		this.collectionId = collectionId;		
	}

	@Override
	public void run() {
		logger.info("Doc creation thread started.");
		try {
			TrpDoc doc = LocalDocReader.load(path);
			
			// check if a document already exists by this name in this collection
			if (false) {
				final String title = doc.getMd().getTitle();
				DocumentDao dDao = new DocumentDao();
				List<TrpDocMetadata> mds = dDao.getDocMdsByName(this.collectionId, title);
				if(mds.size() > 0){
					throw new Exception("A document by this name already exists!");
				}
			}
			
			super.importDoc(collectionId, doc);		
		} catch (Exception e) {
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
				logger.error(e.getMessage(), e);
			} catch (Exception e2){
				logger.error(e.getMessage(), e2);
			}
		}	
	}

}
