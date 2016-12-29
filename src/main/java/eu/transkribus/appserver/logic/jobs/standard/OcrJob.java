package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.EntityNotFoundException;
import javax.xml.bind.JAXBException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.auth.AuthenticationException;
import org.dea.dealog.db.beans.DeaDocument;
import org.dea.dealog.db.exceptions.InvalidDatabaseObjectException;
import org.dea.dealog.db.exceptions.ValueNotFoundException;
import org.dea.dealog.db.stmts.InsertStmts;
import org.dea.dealog.db.stmts.SelectStmts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.io.DocExporter;
import eu.transkribus.core.io.DocExporter.ExportOptions;
import eu.transkribus.core.io.LocalDocConst;
import eu.transkribus.core.io.LocalDocReader;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.enums.EditStatus;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.model.builder.FatBuilder;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.rest.RESTConst;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.OcrManager;
import eu.transkribus.persistence.logic.TranscriptManager;


public class OcrJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(OcrJob.class);
	
	private final String path;
	private int dealogDocId;
	
	private final String language;
	private final String typeFace;
	
	//set initial state
	private String state = JobConst.STATE_DOWNLOAD;
	
	private DocManager docMan = new DocManager();
	private TranscriptManager tMan = new TranscriptManager();
//	private PageManager pMan = new PageManager();
	
	public OcrJob(TrpJobStatus job) {
		super(job);
		path = getProperty(JobConst.PROP_PATH);
		language = getProperty(RESTConst.LANGUAGE_PARAM);
		typeFace = getProperty(RESTConst.TYPE_FACE_PARAM);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		this.setJobStatusProgress("Started OCR Job");
		
		TrpDoc doc;
		try {
			doc = docMan.getDocById(job.getDocId());
		} catch (EntityNotFoundException | ReflectiveOperationException | SQLException e2) {
			this.setJobStatusFailed("Could not find document: " + job.getDocId());
			return;
		}
		DeaDocument deaDoc;
		switch(state){
		case JobConst.STATE_DOWNLOAD:
			//download the document files to path
			this.setJobStatusProgress("Downloading document files...");
			
			try {
				logger.info("Downloading to path: " + path);
				DocExporter ex = new DocExporter();
				Set<Integer> pages = computePageSet(doc);
				//store files with pageID as name. Important for matching result files to original document!
				
				ExportOptions opts = new ExportOptions();
				opts.dir = this.path;
				opts.doOverwrite = true;
				opts.writeMets = false;
				opts.useOcrMasterDir = true;
				opts.doWriteImages = true;
				opts.exportPageXml = false;
				opts.exportAltoXml = false;
				opts.pageIndices = pages;
				opts.fileNamePattern = "${pageId}";
				File outputDir = ex.exportDoc(doc, opts);
				
				String fatFilePath = FatBuilder.writeFatXml(outputDir, language, typeFace).getAbsolutePath();
				
				fatFilePath = OcrManager.getWinPath(fatFilePath);
				
				deaDoc = new DeaDocument(0, null, fatFilePath, LocalDocConst.OCR_MASTER_DIR, "(OCR)", "Start", null, doc.getNPages());
				deaDoc.setCurrent_service("OCR");
				
				this.dealogDocId = (Integer)InsertStmts.insertObjectReturnId(deaDoc);

			} catch (Exception e) {
				this.setJobStatusFailed("Could not download document " 
						+ job.getDocId(), e);
			}
			
			//create dealog docId
			state = JobConst.STATE_OCR;
			
			this.setJobStatusProgress("Exported document");
			
		case JobConst.STATE_OCR:
			//wait for entry in dealog db to be set to finished
			this.setJobStatusProgress("Waiting for OCR to finish...");
			
			while(true && !Thread.currentThread().isInterrupted()){
			
				try {
					deaDoc = SelectStmts.getDeaDocument(dealogDocId);
					this.setJobStatusProgress("OCR: " + deaDoc.getStatus());
					if(deaDoc.getStatus().equals("Finished")){
						break;
					} else if(deaDoc.getStatus().equalsIgnoreCase("FAILED")){
						this.setJobStatusFailed("OCR failed!");
						return;
					}
					
				} catch (InstantiationException | IllegalAccessException
						| InvocationTargetException | SQLException 
						| InvalidDatabaseObjectException e1) {
					//do not finish job here! do resume
					logger.error("Could not retrieve OCR job status!", e1);
				} catch (ValueNotFoundException e2) {
					this.setJobStatusFailed("Could not find DEA document: " + dealogDocId, e2);
					return;
				}
				
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					logger.error("OCR job sleep was interrupted!",e);
					Thread.currentThread().interrupt();
					return;
				}
			}
			state = JobConst.STATE_INGEST;
			
			this.setJobStatusProgress("OCR finished. Reingesting...");
			
		case JobConst.STATE_INGEST:
			//reingest the updated text
			try {
				TrpDoc newDoc = LocalDocReader.load(path);
				
				if(job.getPages() != null && !job.getPages().isEmpty()){
					logger.debug("Updating SELECTED transcripts with OCR text");
					//OCR on selected pages. Standard case
					
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
				} else {
					//this should never happen
					logger.error("Could not match result files to original document!");
					return;
				}
					
				//delete doc from path
				FileUtils.deleteDirectory(new File(path));
			} catch (IOException | JAXBException | AuthenticationException | SQLException | ReflectiveOperationException e) {
				this.setJobStatusFailed("Problem during reingest of result files!", e);
				return;
			}
			break;
		default:
				throw new IllegalStateException();
		}
	}
	
	private Set<Integer> computePageSet(TrpDoc doc) throws IOException{
		Set<Integer> pageSet = new HashSet<>();
		logger.debug("OCR on page set: " + job.getPages());
		pageSet = CoreUtils.parseRangeListStr(job.getPages(), doc.getNPages());
		return pageSet;
	}
}
