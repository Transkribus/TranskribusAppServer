package eu.transkribus.appserver.logic.jobs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import eu.transkribus.core.model.beans.ITrpFile;
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
import eu.transkribus.server.TrpServerConf;
import eu.transkribus.server.logic.JobManager;

public abstract class ADocImportJob extends ATrpJobRunnable {
	private PageDao pDao = new PageDao();
	private TranscriptDao tDao = new TranscriptDao();
	protected ADocImportJob(TrpJobStatus job) {
		super(job);
	}

	protected void importDoc(final int colId, TrpDoc doc) throws Exception{
		TrpDocMetadata docMd = doc.getMd();
		
		int newDocId = job.getDocId();
		//set doc ownership
		final int userId = job.getUserId();
		final String userName = job.getUserName();
		docMd.setUploader(userName);
		docMd.setUploaderId(userId);
		
		doc.getMd().setDocId(newDocId);
		logger.debug(doc.toString());
				
		DocManager docMan = new DocManager();
		
		final long ulTime = new Date().getTime();
		
		//add timestamp
		docMd.setUploadTimestamp(ulTime);
		logger.info("Assigned timestamp: " + new Date(ulTime).toString());

		//build a name for the document collection in fimagestore
		final String storeColl = FimgStoreUtils.getFimgStoreCollectionName(newDocId, DbConnection.getDbServiceName());
		docMd.setFimgStoreColl(storeColl);
		logger.info("Collection name in fimagestore: " + storeColl);

		//Maintain a list of fileKeys that have to be deleted in case of an error/rollback
		List<String> fileKeys = new LinkedList<>();

		//upload pages
		List<TrpPage> pages = doc.getPages();

		//Build a list of initial transcripts on the fly
		List<TrpTranscriptMetadata> transcripts = new ArrayList<>(pages.size());

		//upload files and update objects
		logger.info("Store files...");
		try {
			for (int i = 0; i < pages.size(); i++) {
				TrpPage page = pages.get(i);
				final int newPageId = pDao.getNextPageId();
				page.setPageId(newPageId);
				
				final String imgKey = FimgStoreUtils.uploadFile(storeColl, (ITrpFile) page, TrpServerConf.getInt("fimgstore_nr_of_retries"));
				
				updateStatus("Uploaded image of page " + page.getPageNr());
//				jobMan.updateJob(job.getJobId(), "File storage", "Uploaded image of page " + page.getPageNr());
				
				page.setDocId(newDocId);
				fileKeys.add(imgKey);

				//Check the transcripts
				List<TrpTranscriptMetadata> currTsList = page.getTranscripts();
				//we only accept exactly one transcript here!
				if (currTsList == null || currTsList.size() != 1) {
					throw new IOException("There is either no or many transcripts for page "
							+ page.getPageNr() + "!");
				}

				//upload files and update metadata
				TrpTranscriptMetadata ts = currTsList.get(0);
				
				//set img file name and dimensions in page XML
				//FIXME not working on server. This is done in LocalDocReader anyway
//				Dimension dim = ImgUtils.readImageDimensions(FileUtils.toFile(page.getUrl()));
//				final String imgFileName = page.getImgFileName();
//				final File pageFile = FileUtils.toFile(ts.getUrl());
//				PcGtsType pc = PageXmlUtils.unmarshal(pageFile);
//				pc.getMetadata().setLastChange(XmlUtils.getXmlGregCal());
//				pc.getPage().setImageFilename(imgFileName);
//				pc.getPage().setImageHeight(dim.height);
//				pc.getPage().setImageWidth(dim.width);
//				PageXmlUtils.marshalToFile(pc, pageFile);
//				logger.info("Updated PAGE XML: ImgFileName = " + imgFileName 
//						+ ", dim = " + dim.width + "x" + dim.height);
				
				final String xmlKey = FimgStoreUtils.uploadFile(storeColl, (ITrpFile) ts, TrpServerConf.getInt("fimgstore_nr_of_retries"));
				
				updateStatus("Uploaded PAGE XML for page " + page.getPageNr());
//				jobMan.updateJob(job.getJobId(), "File storage", );
				final int newTsId = tDao.getNextId();
				//set the pageId!
				ts.setTsId(newTsId);
				ts.setPageId(newPageId);
				ts.setDocId(newDocId);
				ts.setUserId(userId);
				ts.setUserName(userName);
				ts.setTimestamp(ulTime);
				fileKeys.add(xmlKey);

				transcripts.add(ts);
			}
		} catch (Exception e) {
			logger.error("Error during file upload: " + e.getMessage());
			logger.error("Deleting " + fileKeys.size() + " uploaded files from fimagestore.");
			FimgStoreRwConnection.getDeleteClient().deleteFiles(fileKeys, TrpServerConf.getInt("fimgstore_nr_of_retries"));
			throw e;
		}

		//insert into DB
		try {
			docMan.insertDocument(docMd, pages, transcripts);
			
			updateStatus("Stored document data in DB");
//			jobMan.updateJob(job.getJobId(), "Database insert", "Stored document data in DB");
			
			CollectionManager cMan = new CollectionManager();
			cMan.addDocToCollection(docMd.getDocId(), colId);
			
			updateStatus("DocPermission set: user with id=" + userId + " is Admin.");
			
		} catch (Exception e) {
			logger.error("Doc insert failed.", e);
			logger.error("Deleting " + fileKeys.size() + " uploaded files from fimagestore.");
			FimgStoreRwConnection.getDeleteClient().deleteFiles(fileKeys, TrpServerConf.getInt("fimgstore_nr_of_retries"));
			throw e;
		}
		JobManager.getInstance().finishJob(jobId, "DONE", true);			
		logger.debug("Stored!");		
	
	}
}
