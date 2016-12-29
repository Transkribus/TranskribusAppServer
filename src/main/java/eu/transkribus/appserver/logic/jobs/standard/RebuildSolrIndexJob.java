package eu.transkribus.appserver.logic.jobs.standard;

import java.sql.SQLException;
import java.util.List;

import javax.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.SolrManager;

public class RebuildSolrIndexJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(RebuildSolrIndexJob.class);

	public RebuildSolrIndexJob(TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {

		SolrManager man = SolrManager.getInstance();
		setJobStatusProgress("Clearing Index...");
		man.resetIndex();
		setJobStatusProgress("Index clear");
//		CollectionManager cMan = new CollectionManager();
		DocManager dMan = new DocManager();

		List<TrpDocMetadata> docList;
		try {
			docList = dMan.getDocList();
		} catch (ReflectiveOperationException | SQLException e) {
			setJobStatusFailed("Could not get document list!", e);
			return;
		}
		
		int count = 0;
		
		logger.info("Indexing " + docList.size() + " documents.");
		
		for (TrpDocMetadata md : docList) {
			TrpDoc doc;
			try {
				doc = dMan.getDocById(md.getDocId());
			} catch (EntityNotFoundException | ReflectiveOperationException | SQLException e) {
				setJobStatusFailed("Could not get document " + md.getDocId() +"!", e);
				return;
			}
			
			if(doc.getMd().getColList().isEmpty()) {
				logger.error(doc.getId() + ": document is not linked in a collection!");
				continue;
			}
			
			try {
				man.indexDocument(doc);
			} catch (SQLException e) {
				logger.error("Could not update index flags!", e);
			}
			if(count++ == 50){
				man.optimizeIndex();
				count = 0;
			}
		}
		man.optimizeIndex();
	}

}
