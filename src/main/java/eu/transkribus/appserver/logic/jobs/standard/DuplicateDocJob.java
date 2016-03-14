package eu.transkribus.appserver.logic.jobs.standard;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityNotFoundException;

import org.dea.fimgstoreclient.FimgStoreDelClient;
import org.dea.fimgstoreclient.FimgStoreGetClient;
import org.dea.fimgstoreclient.FimgStorePostClient;
import org.dea.fimgstoreclient.beans.FimgStoreImg;
import org.dea.fimgstoreclient.beans.FimgStoreXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.dao.PageDao;
import eu.transkribus.persistence.dao.TranscriptDao;
import eu.transkribus.persistence.io.FimgStoreRwConnection;
import eu.transkribus.persistence.logic.CollectionManager;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.util.FimgStoreUtils;
import eu.transkribus.server.logic.JobManager;

public class DuplicateDocJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(DuplicateDocJob.class);
	
	private PageDao pDao;
	private TranscriptDao tDao;
	private static DocManager docMan;
	private static CollectionManager cMan;
	
	private String newTitle;
	private final int colId;
	
	public DuplicateDocJob(int colId, TrpJobStatus job, String newTitle) {
		super(job);
		this.colId = colId;
		this.newTitle = newTitle;
		pDao = new PageDao();
		tDao = new TranscriptDao();
		docMan = new DocManager();
		cMan = new CollectionManager();
	}

	@Override
	public void run() {
		updateStatus("Duplicating document...");
		FimgStoreGetClient getter = FimgStoreRwConnection.getGetClient();
		FimgStorePostClient poster = FimgStoreRwConnection.getPostClient();
		List<String> newKeys = new LinkedList<>();
		
		try{
			//get orig doc
			TrpDoc doc = docMan.getDocById(job.getDocId());
			List<TrpPage> pages = new ArrayList<>(doc.getNPages());
			List<TrpTranscriptMetadata> transcripts = new ArrayList<>(doc.getNPages());
			
			
			//check name and set newName if necessary
			if(newTitle == null || newTitle.isEmpty()){
				newTitle = "Copy of " + doc.getMd().getTitle();
			}
			//build new docMd
			final int newDocId = docMan.getNewDocId();
			TrpDocMetadata docMd = doc.getMd();
			docMd.setDocId(newDocId);
			docMd.setTitle(newTitle);
			docMd.setUploader(job.getUserName());
			docMd.setUploaderId(job.getUserId());
			docMd.setUploadTimestamp(System.currentTimeMillis());
			
			//duplicate all stuff on filestore and put keys to new doc
			final String fimgStoreColName = FimgStoreUtils.getFimgStoreCollectionName(newDocId, DbConnection.getDbServiceName());
			for(TrpPage p : doc.getPages()){
				updateStatus("Duplicating page " + p.getPageNr());
				//build the new page
				p.setDocId(newDocId);
				final String oldImgKey = p.getKey();
				FimgStoreImg img = getter.getImg(oldImgKey);
				final String newImgKey = poster.postFile(img.getData(), img.getFileName(), fimgStoreColName, 3);
				p.setKey(newImgKey);
				newKeys.add(newImgKey);
				final int newPageId = pDao.getNextPageId();
				p.setPageId(newPageId);
				pages.add(p);
				
				//build the new transcript
				TrpTranscriptMetadata tmd = p.getCurrentTranscript();
				tmd.setDocId(newDocId);
				tmd.setPageId(newPageId);
				final int newTsId = tDao.getNextId();
				tmd.setTsId(newTsId);
				final String oldTsKey = tmd.getKey();
				FimgStoreXml xml = getter.getXml(oldTsKey);
				final String newTsKey = poster.postFile(xml.getData(), xml.getFileName(), fimgStoreColName, 3);
				tmd.setKey(newTsKey);
				newKeys.add(newTsKey);
				transcripts.add(tmd);
			}
			docMan.insertDocument(docMd, pages, transcripts);
			cMan.addDocToCollection(docMd.getDocId(), colId);
			JobManager.getInstance().finishJob(jobId, "Done", true);
		} catch(Exception e){
			logger.error("Could not duplicate document!", e);
			FimgStoreDelClient deller = FimgStoreRwConnection.getDeleteClient();
			deller.deleteFiles(newKeys, 3);
			try {
				JobManager.getInstance().finishJob(jobId, "Could not duplicate document! " + e.getMessage(), false);
			} catch (EntityNotFoundException | IllegalArgumentException | SQLException
					| ReflectiveOperationException e1) {
				logger.error("Could not update job!", e1);
			}
		}
	}
}
