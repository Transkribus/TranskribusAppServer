package eu.transkribus.appserver.logic.jobs.standard;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.PageManager;
import eu.transkribus.persistence.logic.SolrManager;
import eu.transkribus.persistence.logic.TranscriptManager;

public class IndexDocumentJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(IndexDocumentJob.class);
	
	public IndexDocumentJob(TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {

		SolrManager sMan = SolrManager.getInstance();
		PageManager pMan = new PageManager();
		TranscriptManager tMan = new TranscriptManager();
		
		try {
			List<TrpPage> pages = pMan.getUnindexedPages();
			setJobStatusProgress("Indexing " + pages.size() + " pages.");
			
			for(int i = 0; i < pages.size();) {
				List<TrpPage> docPages = new LinkedList<>();
				
				int currDocId = pages.get(i).getDocId();
				while(i < pages.size() && pages.get(i).getDocId() == currDocId) {
					TrpPage p = pages.get(i);
					TrpTranscriptMetadata tmd = tMan.getCurrentTranscriptMd(p.getPageId());
					p.getTranscripts().add(tmd);
					docPages.add(p);
					i++;
				}
				setJobStatusProgress("Indexing doc ID = " + currDocId);
				sMan.indexPages(currDocId, docPages);
			}
		} catch (EntityNotFoundException | ReflectiveOperationException | SQLException e) {
			setJobStatusFailed("A DB operation failed!", e);
			return;
		}
		
		sMan.optimizeIndex();
		
	}

}
