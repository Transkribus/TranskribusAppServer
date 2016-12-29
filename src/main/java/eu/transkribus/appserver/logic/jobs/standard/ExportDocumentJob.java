package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Set;

import javax.persistence.EntityNotFoundException;
import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.apache.http.auth.AuthenticationException;
import org.dea.fimgstoreclient.FimgStorePostClient;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.DocumentException;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ATrpJobRunnable;
import eu.transkribus.core.io.DocExporter;
import eu.transkribus.core.io.DocExporter.ExportOptions;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.builder.ExportUtils;
import eu.transkribus.core.model.builder.tei.TeiExportPars;
import eu.transkribus.core.model.builder.tei.TeiExportPars.TeiExportMode;
import eu.transkribus.core.model.builder.tei.TeiExportPars.TeiLinebreakMode;
import eu.transkribus.core.rest.RESTConst;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.core.util.ZipUtils;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.persistence.io.FimgStoreRwConnection;
import eu.transkribus.persistence.io.LocalStorage;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.util.FimgStoreUtils;
import eu.transkribus.persistence.util.MailUtils;

public class ExportDocumentJob extends ATrpJobRunnable {
	
	private static final Logger logger = LoggerFactory.getLogger(ExportDocumentJob.class);
	//This object is set by Quartz. Do not rename!
	private final ExportOptions options;
	
	//all args
	private final Boolean doWriteMets;
	private final Boolean doWriteImages;
	private final Boolean doExportPageXml;
	private final Boolean doExportAltoXml;
	private final Boolean splitIntoWordsInAltoXml;
	private final Boolean doWritePdf;
	private final Boolean doWriteTei;
	private final Boolean doWriteDocx;
	private final Boolean doWriteTagsXlsx;
	private final Boolean doWriteTablesXlsx;
	private final Boolean doPdfImagesOnly;
	private final Boolean doPdfImagesPlusText;
	private final Boolean doPdfWithTextPages;
	private final Boolean doPdfWithTags;
	private final Boolean doTeiWithNoZones;
	private final Boolean doTeiWithZonePerRegion;
	private final Boolean doTeiWithZonePerLine;
	private final Boolean doTeiWithZonePerWord;
	private final Boolean doTeiWithLineTags;
	private final Boolean doTeiWithLineBreaks;
	private final Boolean doDocxWithTags;
	private final Boolean doDocxPreserveLineBreaks;
	private final Boolean doDocxMarkUnclear;
	private final Boolean doDocxKeepAbbrevs;
	private final Boolean doDocxExpandAbbrevs;
	private final Boolean doDocxSubstituteAbbrevs;
	private final Boolean doWordBased;
	private final Boolean doBlackening;
	private final Boolean doCreateTitle;
	private final String useVersionStatus;
	
	protected ExportDocumentJob(TrpJobStatus job) {
		super(job);
		options = null; // TODO
		
		//all args
		doWriteMets = getBoolProperty(RESTConst.WRITE_METS_PARAM);
		doWriteImages = getBoolProperty(RESTConst.DO_WRITE_IMAGES_PARAM);
		doExportPageXml = getBoolProperty(RESTConst.DO_EXPORT_PAGE_PARAM);
		doExportAltoXml = getBoolProperty(RESTConst.DO_EXPORT_ALTO_PARAM);
		splitIntoWordsInAltoXml = getBoolProperty(RESTConst.DO_SPLIT_WORDS_IN_ALTO_PARAM);
		doWritePdf = getBoolProperty(RESTConst.WRITE_PDF_PARAM);
		doWriteTei = getBoolProperty(RESTConst.WRITE_TEI_PARAM);
		doWriteDocx = getBoolProperty(RESTConst.WRITE_DOCX_PARAM);
		doWriteTagsXlsx = getBoolProperty(RESTConst.WRITE_TAGS_EXCEL_PARAM);
		doWriteTablesXlsx = getBoolProperty(RESTConst.WRITE_TABLES_EXCEL_PARAM);
		doPdfImagesOnly = getBoolProperty(RESTConst.DO_PDF_IMAGES_ONLY_PARAM);
		doPdfImagesPlusText = getBoolProperty(RESTConst.DO_PDF_IMAGES_PLUS_TEXT_PARAM);
		doPdfWithTextPages = getBoolProperty(RESTConst.DO_PDF_EXTRA_TEXT_PARAM);
		doPdfWithTags = getBoolProperty(RESTConst.DO_PDF_HIGHLIGHT_TAGS_PARAM);
		doTeiWithNoZones = getBoolProperty(RESTConst.DO_TEI_NO_ZONES_PARAM);
		doTeiWithZonePerRegion = getBoolProperty(RESTConst.DO_TEI_REGION_ZONE_PARAM);
		doTeiWithZonePerLine = getBoolProperty(RESTConst.DO_TEI_LINE_ZONE_PARAM);
		doTeiWithZonePerWord = getBoolProperty(RESTConst.DO_TEI_WORD_ZONE_PARAM);
		doTeiWithLineTags = getBoolProperty(RESTConst.DO_TEI_LINE_TAGS_PARAM);
		doTeiWithLineBreaks = getBoolProperty(RESTConst.DO_TEI_LINE_BREAKS_PARAM);
		doDocxWithTags = getBoolProperty(RESTConst.DO_DOCX_EXPORT_TAGS);
		doDocxPreserveLineBreaks = getBoolProperty(RESTConst.DO_DOCX_PRESERVE_BREAKS_PARAM);
		doDocxMarkUnclear = getBoolProperty(RESTConst.DO_DOCX_MARK_UNCLEAR_PARAM);
		doDocxKeepAbbrevs = getBoolProperty(RESTConst.DO_DOCX_KEEP_ABBREVS_PARAM);
		doDocxExpandAbbrevs = getBoolProperty(RESTConst.DO_DOCX_EXPAND_ABBREVS_PARAM);
		doDocxSubstituteAbbrevs = getBoolProperty(RESTConst.DO_DOCX_SUBSTITUTE_ABBREVS_PARAM);
		doWordBased = getBoolProperty(RESTConst.DO_WORD_BASED_EXPORT_PARAM);
		doBlackening = getBoolProperty(RESTConst.DO_BLACKENING_PARAM);
		doCreateTitle = getBoolProperty(RESTConst.DO_CREATE_TITLE_PARAM);
		useVersionStatus = getProperty(RESTConst.USE_VERSION_STATUS_PARAM);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		logger.info("Exporting document...");
		File workDir = null;
		try {
			
			//create temporary directory on transkribus storage
			workDir = LocalStorage.getExportTempDir();
			DocManager docMan = new DocManager();
			TrpDoc doc = docMan.getDocById(job.getDocId());
			Set<Integer> pageSet = CoreUtils.parseRangeListStr(job.getPages(), doc.getNPages());
			
			//logger.debug("pageSet size " + pageSet.size());

			DocExporter docExporter = new DocExporter();
			
			String docName = doc.getMd().getTitle();
			String adjustedDocName = ExportUtils.getAdjustedDocTitle(docName);
			String docID = Integer.toString(doc.getMd().getDocId());
			
			String exportDir = workDir.getAbsolutePath() + "/" + docID;
			String zipFilename = workDir.getAbsolutePath() + "/" + adjustedDocName + ".zip";
			
			if(!new File(exportDir).mkdir()) {
				throw new IOException("Could not create ExportDir!");
			}
			/*
			 * here we store the page transcripts for all later exports regarding to the wished version status
			 * if status can not be found -> we get the latest one, so values 
			 *  
			 */
			ExportUtils.storePageTranscripts4Export(doc, pageSet, null, useVersionStatus, -1, null);
			// ====================================== 
			// ====== EXPORT HERE TO WORKDIR ========
			// ======================================
			if (doWriteMets){
				logger.debug("doWriteImages = " + doWriteImages + "\n" 
						+ "doExportPageXml = "  + doExportPageXml + "\n"
						+ "doExportAltoXml = " + doExportAltoXml + "\n"
						+ "splitIntoWordsInAltoXml = " + splitIntoWordsInAltoXml);
				docExporter.writeRawDoc(doc, exportDir + "/" + adjustedDocName, true, pageSet, doWriteImages, doExportPageXml, doExportAltoXml, splitIntoWordsInAltoXml);
			}
			if(doWritePdf){
				docExporter.writePDF(doc, exportDir + "/" + adjustedDocName + ".pdf", pageSet, doPdfWithTextPages, doPdfImagesOnly, doPdfWithTags, doWordBased, doBlackening, doCreateTitle);
			}
			if (doWriteTei){
				final TeiExportPars pars = new TeiExportPars();
				pars.mode = TeiExportMode.ZONE_PER_PAR;
				if (doTeiWithNoZones){
					pars.mode = TeiExportMode.SIMPLE;
				}
				else if(doTeiWithZonePerRegion){
					pars.mode = TeiExportMode.ZONE_PER_PAR;
				}
				else if(doTeiWithZonePerLine){
					pars.mode = TeiExportMode.ZONE_PER_LINE;
				}
				else if(doTeiWithZonePerWord){
					pars.mode = TeiExportMode.ZONE_PER_WORD;
				}
				
				pars.linebreakMode = TeiLinebreakMode.LINE_TAG;
				if (doTeiWithLineBreaks){
					pars.linebreakMode = TeiLinebreakMode.LINE_BREAKS;
				}
				else if (doTeiWithLineTags){
					pars.linebreakMode = TeiLinebreakMode.LINE_TAG;
				}
				
				pars.writeTextOnWordLevel = doWordBased;
				pars.doBlackening = doBlackening;
				pars.pageIndices = pageSet;
				
				docExporter.writeTEI(doc, exportDir + "/" + adjustedDocName  + ".tei", pars);
			}
			if(doWriteDocx){
				docExporter.writeDocx(doc, exportDir + "/" + adjustedDocName  + ".docx", pageSet, doDocxWithTags, doWordBased, doBlackening, doCreateTitle, doDocxMarkUnclear, doDocxExpandAbbrevs, doDocxSubstituteAbbrevs, doDocxPreserveLineBreaks);
			}
			if(doWriteTagsXlsx){
				docExporter.writeTagExcel(doc, exportDir + "/" + adjustedDocName  + ".xlsx", pageSet, doWordBased);
			}
			if(doWriteTablesXlsx){
				docExporter.writeTableExcel(doc, exportDir + "/" + adjustedDocName  + "_tables.xlsx", pageSet);
			}
			
			//zip all stuff to this file in workdir
			File zippedExportFile = ZipUtils.createZipFromFolder(exportDir , zipFilename);
			
			//ExportUtils.createZipFromFolder(workDir.getAbsolutePath(), destZipFile);
			
			//post file to fimagestore
			FimgStorePostClient poster = FimgStoreRwConnection.getPostClient();
			final String key = poster.postFile(zippedExportFile, 
					FimgStoreUtils.getFimgStoreCollectionName(job.getDocId(), DbConnection.getDbServiceName()), 4);
			
			//build the URI from the key
			URI exportUri = FimgStoreRwConnection.getUriBuilder().getFileUri(key);
			
			//write the export URI to the job table in DB
			super.setJobStatusResult(exportUri.toString());
			
			//send mail to user
			try {	
				UserDao ud = new UserDao();
				TrpUser user = ud.getUser(this.getJobStatus().getUserId(), true);
				final String email = user.getEmail();
				if(email == null || email.isEmpty()){
					logger.warn("User has no email address! Skipping mail...");
				} else {
					String msg = "Dear " + user.getFirstname() + ",\n";
					msg += "your document \"" + doc.getMd().getTitle() + "\" is now exported and "
							+ "you can download the result at:\n"
							+ exportUri.toString();
					MailUtils.sendMailFromUibkAddress(email, MailUtils.TRANSKRIBUS_EMAIL_MAIL_SERVER.getEmail(), 
							"Document export is complete", msg, null, true, true);
				}
			} catch (SQLException | IOException e) {
				logger.error(e.getMessage(), e);
			}
			
			//exit workflow
		} catch (IOException e) {
			setJobStatusFailed("An IO error occured!", e);
		} catch (AuthenticationException e) {
			setJobStatusFailed("Could not authenticate with filestore!", e);
		} catch (EntityNotFoundException e) {
			setJobStatusFailed("An entity not found exception occured!", e);
		} catch (ReflectiveOperationException e) {
			setJobStatusFailed("A ReflectiveOperationException occured!", e);
		} catch (SQLException e) {
			setJobStatusFailed("A SQLException occured!", e);
		} catch (IllegalArgumentException e) {
			setJobStatusFailed("An IllegalArgumentException occured!", e);
		} catch (URISyntaxException e) {
			setJobStatusFailed("Not a valid URI!", e);
		} catch (JAXBException e) {
			setJobStatusFailed("An JAXBException occured!", e);
		} catch (TransformerException e) {
			setJobStatusFailed("XML transformation fails!", e);
		} catch (DocumentException e) {
			setJobStatusFailed("PDF document creation failure!", e);
		} catch (InterruptedException e) {
			setJobStatusFailed("Thread was interrupted!", e);
		} catch (Docx4JException e) {
			setJobStatusFailed("Docx document creation failure!", e);
		} catch (Exception e) {
			setJobStatusFailed("Unknown exception!", e);
		} finally {
			workDir.delete();
		}
	}
}
