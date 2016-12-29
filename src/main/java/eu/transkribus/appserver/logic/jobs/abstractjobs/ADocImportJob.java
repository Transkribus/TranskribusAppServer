package eu.transkribus.appserver.logic.jobs.abstractjobs;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.ITrpFile;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.TrpImage;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata.TrpTranscriptStatistics;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.ImgUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.TrpPersistenceConf;
import eu.transkribus.persistence.dao.ImageDao;
import eu.transkribus.persistence.dao.PageDao;
import eu.transkribus.persistence.dao.TranscriptDao;
import eu.transkribus.persistence.io.FimgStoreRwConnection;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.CollectionManager;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.util.FimgStoreUtils;

public abstract class ADocImportJob extends ATrpJobRunnable{
	

	private static final Logger logger = LoggerFactory.getLogger(ADocImportJob.class);
	private PageDao pDao = new PageDao();
	private TranscriptDao tDao = new TranscriptDao();
	private ImageDao iDao = new ImageDao();

	protected final String path;
	protected final int colId;
	
	protected final List<Integer> additionalColIds;
	
	protected ADocImportJob(TrpJobStatus job) {
		super(job);
		this.path = getProperty(JobConst.PROP_PATH);
		this.colId = getIntProperty(JobConst.PROP_COLLECTION_ID);
		additionalColIds = getIntListProperty(JobConst.PROP_ADDITIONAL_COL_IDS);
	}
	
	protected void importDoc(final int colId, final int newDocId, TrpDoc doc, 
			final int userId, final String userName) 
					throws SQLException, ReflectiveOperationException, IOException, AuthenticationException, JobCanceledException {
		TrpDocMetadata docMd = doc.getMd();
		
		//set doc ownership
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
		
		//images to upload
		List<TrpImage> imgs = new ArrayList<>(pages.size());

		//Build a list of initial transcripts on the fly
		List<TrpTranscriptMetadata> transcripts = new ArrayList<>(pages.size());

		//upload files and update objects
		try {
			setJobStatusProgress("Storing files...");
		} catch (JobCanceledException e1) {
			return;
		}
		try {
			for (int i = 0; i < pages.size(); i++) {
				TrpPage page = pages.get(i);
				final int newPageId = pDao.getNextPageId();
				page.setPageId(newPageId);
				
				final int imageId = iDao.getNextImageId();
				TrpImage img = page.getImage();
				img.setImageId(imageId);
				page.setImageId(imageId);
				try {
					Dimension d = ImgUtils.readImageDimensionsWithExiftool(new File(img.getUrl().toURI()));
					img.setHeight(new Double(Math.floor(d.getHeight())).intValue());
					img.setWidth(new Double(Math.floor(d.getWidth())).intValue());
				} catch (Exception e) {
					logger.error("Could not read image dimensions!", e);
				}
				img.setCreated(new java.sql.Timestamp(System.currentTimeMillis()));
				
				final String imgKey = FimgStoreUtils.uploadFile(storeColl, (ITrpFile) img, TrpPersistenceConf.getInt("fimgstore_nr_of_retries"));
				img.setKey(imgKey);
				setJobStatusProgress("Uploaded image of page " + page.getPageNr());
				imgs.add(img);
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
				final File pageFile = FileUtils.toFile(ts.getUrl());
				PcGtsType pc = PageXmlUtils.unmarshal(pageFile);
				TrpTranscriptStatistics stats = PageXmlUtils.extractStats(pc);
				ts.setStats(stats);		
//				pc.getMetadata().setLastChange(XmlUtils.getXmlGregCal());
//				pc.getPage().setImageFilename(imgFileName);
//				pc.getPage().setImageHeight(dim.height);
//				pc.getPage().setImageWidth(dim.width);
//				PageXmlUtils.marshalToFile(pc, pageFile);
//				logger.info("Updated PAGE XML: ImgFileName = " + imgFileName 
//						+ ", dim = " + dim.width + "x" + dim.height);
				
				final String xmlKey = FimgStoreUtils.uploadFile(storeColl, (ITrpFile) ts, TrpPersistenceConf.getInt("fimgstore_nr_of_retries"));
				
				setJobStatusProgress("Uploaded PAGE XML for page " + page.getPageNr());
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
		} catch (JAXBException je) {
			logger.error("Could not read a PAGE XML!", je);
			throw new IOException("Could not read a PAGE XML!", je);
		} catch (IOException | SQLException | AuthenticationException e) {
			logger.error("Error during file upload: " + e.getMessage());
			logger.error("Deleting " + fileKeys.size() + " uploaded files from fimagestore.");
			FimgStoreRwConnection.getDeleteClient().deleteFiles(fileKeys, TrpPersistenceConf.getInt("fimgstore_nr_of_retries"));
			throw e;
		}

		//insert into DB
		Connection conn = DbConnection.getConnection();
		conn.setAutoCommit(false);
		try {

			docMan.insertDocument(conn, docMd, pages, imgs, transcripts);

			try {
				setJobStatusProgress("Stored document data in DB");
			} catch (JobCanceledException e) {
				throw new IOException("Job canceled. Cleaning up...");
			}
//			jobMan.updateJob(job.getJobId(), "Database insert", "Stored document data in DB");
			
			CollectionManager cMan = new CollectionManager();
			cMan.addDocToCollection(conn, docMd.getDocId(), colId);
			logger.info("DocPermission set: user with id=" + userId + " is Owner.");
			//add to additional collections
			if(additionalColIds != null && !additionalColIds.isEmpty()) {
				for(Integer id : additionalColIds) {
					if(id != null && id != 0) {
						cMan.addDocToCollection(conn, docMd.getDocId(), id);
					}
				}
			}
			
			conn.commit();
		} catch (Exception e) {
			conn.rollback();
			logger.error("Doc insert failed.", e);
			logger.error("Deleting " + fileKeys.size() + " uploaded files from fimagestore.");
			FimgStoreRwConnection.getDeleteClient().deleteFiles(fileKeys, TrpPersistenceConf.getInt("fimgstore_nr_of_retries"));
			throw e;
		} finally {
			conn.close();
		}
		
//		JobManager.getInstance().finishJob(jobId, "DONE", true);			
		logger.debug("Stored!");		
	
	}
}
