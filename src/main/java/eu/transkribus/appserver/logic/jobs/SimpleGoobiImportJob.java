package eu.transkribus.appserver.logic.jobs;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.io.GoobiMetsImporter;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.mets.Mets;
import eu.transkribus.persistence.dao.DocumentDao;
import eu.transkribus.server.logic.JobManager;


/** A simple doc import job that takes an already locally loaded doc and imports to the server
 * @author giorgio
 *
 */
public class SimpleGoobiImportJob extends ADocImportJob {
	private static final Logger logger = LoggerFactory.getLogger(SimpleGoobiImportJob.class);
	private final String path;
	private final int collectionId;
	private TrpDoc doc;
	private Mets mets;
	
	public SimpleGoobiImportJob(final int collectionId, final TrpJobStatus job, final File dir, final Mets mets) {
		this(collectionId, job, dir.getAbsolutePath(), mets);
	}
	
	public SimpleGoobiImportJob(final int collectionId, final TrpJobStatus job, final String path, final TrpDoc doc) {
		super(job);
		job.setJobData(path);
		this.path = path;
		this.collectionId = collectionId;
		this.doc = doc;
	}
	
	public SimpleGoobiImportJob(final int collectionId, final TrpJobStatus job, final String path, final Mets mets) {
		super(job);
		job.setJobData(path);
		this.path = path;
		this.collectionId = collectionId;
		this.mets = mets;
	}

	@Override
	public void run() {
		logger.info("Doc creation thread started.");
		try {	
			
			if (doc == null){
				doc = GoobiMetsImporter.loadDocFromGoobiMets(mets, path);
			}
			//check if a document already exists by this name in this collection
			final String title = doc.getMd().getTitle();
			DocumentDao dDao = new DocumentDao();
			List<TrpDocMetadata> mds = dDao.getDocMdsByName(this.collectionId, title);
			if(mds.size() > 0){
				throw new Exception("A document by this name already exists!");
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
