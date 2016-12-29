package eu.transkribus.appserver.logic.jobs.standard;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ADocImportJob;
import eu.transkribus.core.io.LocalDocReader;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;


/** A simple doc import job that needs only the path to the document (no mets, no zip...)
 * @author philip
 *
 */
public class DocImportJob extends ADocImportJob {
	

	private static final Logger logger = LoggerFactory.getLogger(DocImportJob.class);
	
	protected DocImportJob(TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		logger.info("Doc creation thread started.");
		try {
			this.setJobStatusProgress("Reading document...");
			TrpDoc doc = LocalDocReader.load(path);
			
			// check if a document already exists by this name in this collection
//			if (false) {
//				final String title = doc.getMd().getTitle();
//				DocumentDao dDao = new DocumentDao();
//				List<TrpDocMetadata> mds = dDao.getDocMdsByName(colId, title);
//				if(mds.size() > 0){
//					throw new Exception("A document by this name already exists!");
//				}
//			}
			this.setJobStatusProgress("Importing document...");
			super.importDoc(colId, job.getDocId(), doc, job.getUserId(), job.getUserName());
		} catch (SQLException | AuthenticationException | ReflectiveOperationException e) {
			this.setJobStatusFailed(e.getMessage());
			return;
		} catch (IOException ioe){
			this.setJobStatusFailed(ioe.getMessage());
			return;
		}
	}
}
