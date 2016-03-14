package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.persistence.EntityNotFoundException;
import javax.xml.bind.JAXBException;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.auth.AuthenticationException;
import org.dea.dealog.db.beans.DeaDocument;
import org.dea.dealog.db.exceptions.InvalidDatabaseObjectException;
import org.dea.dealog.db.exceptions.ValueNotFoundException;
import org.dea.dealog.db.stmts.InsertStmts;
import org.dea.dealog.db.stmts.SelectStmts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.io.DocExporter;
import eu.transkribus.core.io.LocalDocConst;
import eu.transkribus.core.io.LocalDocReader;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.enums.EditStatus;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.model.builder.FatBuilder;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.PageManager;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.server.logic.JobManager;
import eu.transkribus.server.logic.OcrManager;

public class OcrJob extends AResumableTrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(OcrJob.class);
	
	private String path;
	private int dealogDocId;
	//set initial state
	private String state = JobConst.STATE_DOWNLOAD;
	private String pages;
	
	private DocManager docMan = new DocManager();
	private TranscriptManager tMan = new TranscriptManager();
	private PageManager pMan = new PageManager();
	
	
	public OcrJob(TrpJobStatus job) {
		super(job);
	}
	
	public OcrJob(TrpJobStatus job, final String ocrPath) {
		super(job);
		this.path = ocrPath;
	}
	
	public OcrJob(TrpJobStatus job, final String ocrPath, final String pageStr) {
		this(job, ocrPath);
		this.pages = pageStr;
	}
	
	public void setPath(final String ocrPath){
		this.path = ocrPath;
	}
	
	@Override
	protected void resume(Properties params) {
		if(params.entrySet().size() < 3){
			logger.info("Resuming job as initial.");
		} else {
			this.state = params.getProperty(JobConst.PROP_STATE);
			this.path = params.getProperty(JobConst.PROP_PATH);
			this.dealogDocId = Integer.parseInt(params.getProperty(JobConst.PROP_DEALOG_DOC_ID));
			this.pages = params.getProperty(JobConst.PROP_PAGES);
		}
	}
	
	@Override
	protected Properties buildJobData(){
		Properties p = new Properties();
		p.setProperty(JobConst.PROP_STATE, state);
		p.setProperty(JobConst.PROP_PATH, path);
		p.setProperty(JobConst.PROP_DEALOG_DOC_ID, ""+dealogDocId);
		if(pages != null && !pages.isEmpty()){
			p.setProperty(JobConst.PROP_PAGES, pages);
		}
		return p;
	}
	
	@Override
	public void run() {
		updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Started OCR Job", buildJobDataStr()));
		TrpDoc doc;
		try {
			doc = docMan.getDocById(this.job.getDocId());
		} catch (EntityNotFoundException | ReflectiveOperationException | SQLException e2) {
			try {
				JobManager.getInstance().finishJob(this.job.getJobId(), e2.getMessage(), false);
			} catch (EntityNotFoundException | IllegalArgumentException | SQLException
					| ReflectiveOperationException e1) {
				logger.error("Could not update job!", e1);
			}
			return;
		}
		DeaDocument deaDoc;
		switch(state){
		case JobConst.STATE_DOWNLOAD:
			//download the document files to path
			updateStatus("Downloading document files...");
			try {
				
				DocExporter ex = new DocExporter();
				Set<Integer> pages = computePageSet(doc);
				//store files with pageID as name. Important for matching result files to original document!
				final String fileNamePattern = "${pageId}";
				File fatDoc = ex.writeFatDoc(doc, pages, this.path, true, fileNamePattern);
				String fatFilePath = this.path + "/" + FatBuilder.FAT_FILE_NAME;
				
				fatFilePath = OcrManager.getWinPath(fatFilePath);
				
				deaDoc = new DeaDocument(0, null, fatFilePath, LocalDocConst.OCR_MASTER_DIR, "(OCR)", "Start", null, doc.getNPages());
				deaDoc.setCurrent_service("OCR");
				
				dealogDocId = (Integer)InsertStmts.insertObjectReturnId(deaDoc);
			} catch (Exception e) {
				try {
					JobManager.getInstance().finishJob(this.job.getJobId(), e.getMessage(), false);
					return;
				} catch (EntityNotFoundException | IllegalArgumentException | SQLException
						| ReflectiveOperationException e1) {
					logger.error("Could not update job!", e1);
					return;
				}
			}
			if(Thread.currentThread().isInterrupted()){
				return;
			}
			//create dealog docId
			state = JobConst.STATE_OCR;
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Exported document", buildJobDataStr()));
		case JobConst.STATE_OCR:
			//wait for entry in dealog db to be set to finished
			updateStatus("Waiting for OCR to finish...");
			while(true && !Thread.currentThread().isInterrupted()){
			
				try {
					deaDoc = SelectStmts.getDeaDocument(dealogDocId);
					updateStatus("OCR: " + deaDoc.getStatus());
					if(deaDoc.getStatus().equals("Finished")){
						break;
					} else if(deaDoc.getStatus().equalsIgnoreCase("FAILED")){
						try {
							JobManager.getInstance().finishJob(this.job.getJobId(), "OCR failed", false);
							return;
						} catch (EntityNotFoundException | IllegalArgumentException | SQLException
								| ReflectiveOperationException e1) {
							logger.error("Could not update job!", e1);
							return;
						}
					}
					
				} catch (InstantiationException | IllegalAccessException
						| InvocationTargetException | SQLException | InvalidDatabaseObjectException e1) {
					//do not finish job here! do resume
					logger.error("Could not retrieve OCR job status!", e1);
				} catch (ValueNotFoundException e2) {
					try {
						JobManager.getInstance().finishJob(this.job.getJobId(), e2.getMessage(), false);
						return;
					} catch (EntityNotFoundException | IllegalArgumentException | SQLException
							| ReflectiveOperationException e1) {
						logger.error("Could not update job!", e1);
						return;
					}
				}
				
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					logger.error("OCR job sleep was interrupted!",e);
					Thread.currentThread().interrupt();
					return;
				}
			}
			
			if(Thread.currentThread().isInterrupted()){
				return;
			}			
			state = JobConst.STATE_INGEST;
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("OCR done", buildJobDataStr()));
		case JobConst.STATE_INGEST:
			//reingest the updated text
			updateStatus("Reingesting text...");
			try {
				TrpDoc newDoc = LocalDocReader.load(path);
				
				if(this.pages != null && !this.pages.isEmpty()){
					logger.debug("Updating SELECTED transcripts with OCR text");
					//OCR on selected pages. Standard case
					
					if(false){
						//Variant 1: match by page index (unsafe!! may fail if order of image names does not reflect pagination)
						List<Integer> pageList = CoreUtils.parseRangeListStrToList(this.pages, doc.getNPages());
						Collections.sort(pageList);
						if(pageList.size() != newDoc.getPages().size()){
							throw new IllegalStateException("Nr. of OCR files differs from input size!");
						}
						PageManager pMan = new PageManager();
						for(int i = 0; i < newDoc.getPages().size(); i++){
							final int pageNr = pageList.get(i)+1;
							final int pageId = pMan.getPageId(doc.getId(), pageNr);
							logger.debug("Updating page " + pageNr);
							PcGtsType pc = PageXmlUtils.unmarshal(newDoc.getPages().get(i).getCurrentTranscript().getUrl());
							tMan.updateTranscript(pageId, EditStatus.IN_PROGRESS, job.getUserId(), job.getUserName(), pc, OcrManager.OCR_ENGINE_NAME);
						}
					} else {
						//Variant 2: match by img file name (i.e. pageID)
						for(TrpPage p : newDoc.getPages()){
							//filename is pageID
							final String pageIdStr = FilenameUtils.getBaseName(p.getImgFileName());
							final int pageId = Integer.parseInt(pageIdStr);
							for(TrpPage p2 : doc.getPages()){
								if(pageId == p2.getPageId()){
									logger.debug("Updating page " + p2.getPageNr());
									PcGtsType pc = PageXmlUtils.unmarshal(p.getCurrentTranscript().getUrl());
									tMan.updateTranscript(p2.getPageId(), EditStatus.IN_PROGRESS, job.getUserId(), job.getUserName(), pc, OcrManager.OCR_ENGINE_NAME);
									break;
								}
							}
						}
					}
				} else if(job.getPageNr() < 1 && doc.getNPages() == newDoc.getNPages()){
					//whole document OCR (backwards compatibility)
					logger.debug("Updating ALL transcripts with OCR text");
					tMan.updateTranscripts(doc, newDoc, job.getUserId(), job.getUserName(), OcrManager.OCR_ENGINE_NAME);
				} else if(newDoc.getNPages() == 1 && job.getPageNr() > 0){
					logger.debug("Updating SINGLE transcript with OCR text");
					//OCR on single page (backwards compatibility)
					final TrpPage p = newDoc.getPages().get(0);
					PcGtsType pc = PageXmlUtils.unmarshal(p.getCurrentTranscript().getUrl());
					final int pageId = pMan.getPageId(doc.getId(), job.getPageNr());
					tMan.updateTranscript(pageId, EditStatus.IN_PROGRESS, job.getUserId(), job.getUserName(), pc, OcrManager.OCR_ENGINE_NAME);
				} else {
					//this should never happen
					throw new IllegalStateException("Could not match result files to original document!");
				}
					
				//delete doc from path
//				FileUtils.deleteDirectory(new File(path));
			} catch (IllegalStateException | IOException | JAXBException | AuthenticationException | SQLException | ReflectiveOperationException e) {
				logger.error("Problem during reingest of result files!", e);
				try {
					JobManager.getInstance().finishJob(this.job.getJobId(), e.getMessage(), false);
				} catch (EntityNotFoundException | IllegalArgumentException | SQLException
						| ReflectiveOperationException e2) {
					logger.error("Could not update job!", e2);
					return;
				}
			}
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Ingested results", buildJobDataStr()));
		}
		try {
			JobManager.getInstance().finishJob(this.job.getJobId(), "OCR job finished!", true);
		} catch (EntityNotFoundException | IllegalArgumentException | SQLException
				| ReflectiveOperationException e) {
			logger.error("Could not update job!", e);
			return;
		}
		return;
	}
	
	private Set<Integer> computePageSet(TrpDoc doc) throws IOException{
		Set<Integer> pageSet = new HashSet<>();
		if(job.getPageNr() > 0){
			logger.debug("OCR on single page: " + job.getPageNr());
			pageSet.add(job.getPageNr() - 1);
		} else if (this.pages != null && !this.pages.isEmpty()){
			logger.debug("OCR on page set: " + this.pages);
			pageSet = CoreUtils.parseRangeListStr(this.pages, doc.getNPages());
		} else {
			logger.debug("OCR on complete document.");
			for(int i = 0; i < doc.getNPages(); i++){
				pageSet.add(i);
			}
		}
		return pageSet;
	}
}
