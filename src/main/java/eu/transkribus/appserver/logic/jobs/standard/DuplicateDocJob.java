package eu.transkribus.appserver.logic.jobs.standard;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.auth.AuthenticationException;
import org.dea.fimgstoreclient.FimgStoreDelClient;
import org.dea.fimgstoreclient.FimgStoreGetClient;
import org.dea.fimgstoreclient.FimgStorePostClient;
import org.dea.fimgstoreclient.beans.FimgStoreXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.model.beans.EdFeature;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.dao.PageDao;
import eu.transkribus.persistence.dao.TranscriptDao;
import eu.transkribus.persistence.io.FimgStoreRwConnection;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.CollectionManager;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.EditDeclManager;
import eu.transkribus.persistence.util.FimgStoreUtils;

public class DuplicateDocJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(DuplicateDocJob.class);
	
	private PageDao pDao =new PageDao();
	private TranscriptDao tDao = new TranscriptDao();
	private static DocManager docMan = new DocManager();
	private static CollectionManager cMan = new CollectionManager();
	private static EditDeclManager eMan = new EditDeclManager();
	
	//the title for the duplicate
	private String title;
	//the collection where the duplicate is created
	private final int colId;

	protected DuplicateDocJob(TrpJobStatus job) {
		super(job);
		title = getProperty(JobConst.PROP_TITLE);
		colId = getIntProperty(JobConst.PROP_COLLECTION_ID);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		logger.info("Duplicating document...");
		FimgStoreGetClient getter = FimgStoreRwConnection.getGetClient();
		FimgStorePostClient poster = FimgStoreRwConnection.getPostClient();
		List<String> newKeys = new LinkedList<>();
		Connection conn = null;
		final int newDocId;
		try {
			conn = DbConnection.getConnection();
			conn.setAutoCommit(false);
			this.setJobStatusProgress("Duplicating Document...");
			//get orig doc
			TrpDoc doc = docMan.getDocById(job.getDocId());
			List<TrpPage> pages = new ArrayList<>(doc.getNPages());
			List<TrpTranscriptMetadata> transcripts = new ArrayList<>(doc.getNPages());
			
			
			//check name and set newName if necessary
			if(title == null || title.isEmpty()){
				title = "Copy of " + doc.getMd().getTitle();
			}
			//build new docMd
			newDocId = docMan.getNewDocId();
			TrpDocMetadata docMd = doc.getMd();
			docMd.setDocId(newDocId);
			docMd.setOrigDocId(job.getDocId());
			docMd.setTitle(title);
			docMd.setUploader(job.getUserName());
			docMd.setUploaderId(job.getUserId());
			docMd.setUploadTimestamp(System.currentTimeMillis());
			
			//duplicate all stuff on filestore and put keys to new doc
			final String fimgStoreColName = FimgStoreUtils.getFimgStoreCollectionName(newDocId, DbConnection.getDbServiceName());
			for(TrpPage p : doc.getPages()){
				logger.info("Duplicating page " + p.getPageNr());
				this.setJobStatusProgress("Duplicating page " + p.getPageNr());
				//build the new page
				p.setDocId(newDocId);
				final int newPageId = pDao.getNextPageId();
				p.setPageId(newPageId);
				p.setIndexed(false);
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
			docMan.insertDocument(conn, docMd, pages, null, transcripts);
			cMan.addDocToCollection(conn, docMd.getDocId(), colId);
			
			List<EdFeature> editDecl = eMan.getEditDecl(job.getDocId());
			eMan.storeEditDecl(conn, newDocId, editDecl);
			
			conn.commit();
			
		} catch(SQLException | ReflectiveOperationException | AuthenticationException | IOException e){
			try {
				conn.rollback();
			} catch (SQLException e1) {
				logger.error("Rollback did not work!", e1);
			}
			FimgStoreDelClient deller = FimgStoreRwConnection.getDeleteClient();
			try {
				deller.deleteFiles(newKeys, 4);
			} catch(Exception ex) {
				logger.error("Deleting files did not work: " + ex.getMessage(),ex);
			}
			this.setJobStatusFailed(e.getMessage());
			return;
		} finally {
			try {
				conn.close();
			} catch (SQLException e) {}
		}
	}
}
