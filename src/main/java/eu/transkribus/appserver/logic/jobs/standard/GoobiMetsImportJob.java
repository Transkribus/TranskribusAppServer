package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;

import javax.xml.bind.JAXBException;

import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ADocImportJob;
import eu.transkribus.core.io.GoobiMetsImporter;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.mets.Mets;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;


/** A simple doc import job that takes an already locally loaded doc and imports to the server
 * @author giorgio
 *
 */
public class GoobiMetsImportJob extends ADocImportJob {
	

	private static final Logger logger = LoggerFactory.getLogger(GoobiMetsImportJob.class);
	
	private TrpDoc doc;
	
	private final String metsPath;

	protected GoobiMetsImportJob(TrpJobStatus job) {
		super(job);
		metsPath = getProperty(JobConst.PROP_METS_PATH);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		logger.info("Doc creation thread started.");
		
		File metsFile = new File(metsPath);
		if(!metsFile.isFile()) {
			setJobStatusFailed("File not found: " + metsPath);
			return;
		}
		
		Mets mets;
		try{
			logger.debug("Reading METS file...");
			mets = JaxbUtils.unmarshal(metsFile, Mets.class, TrpDocMetadata.class);
			logger.debug("Done.");
		} catch (JAXBException | FileNotFoundException e){
			setJobStatusFailed("Could not read METS on server!", e);
			return;
		}
		
		setJobStatusProgress("Reading document from METS file...");
		
		try {
			doc = GoobiMetsImporter.loadDocFromGoobiMets(metsFile, path);
		} catch (IOException | JAXBException | SAXException e) {
			setJobStatusFailed("Could not load METS! " + e.getMessage(), e);
			return;
		}
		final String title = doc.getMd().getTitle();
		
		//check if a document already exists by this name in this collection	
//		DocumentDao dDao = new DocumentDao();
//		List<TrpDocMetadata> mds;
//		try {
//			mds = dDao.getDocMdsByName(this.colId, title);
//		} catch (SQLException | ReflectiveOperationException e) {
//			logger.error(e.getMessage(), e);
//			setJobStatusFailed("Could nor load METS! " + e.getMessage());
//			return;
//		}
//		if(mds.size() > 0){
//			setJobStatusFailed("A document by this name already exists!");
//			return;
//		}
		setJobStatusProgress("Starting ingest..");
		try {
			super.importDoc(colId, job.getDocId(), doc, job.getUserId(), title);
		} catch (AuthenticationException | SQLException | ReflectiveOperationException | IOException e) {
			setJobStatusFailed("Could not import document!", e);
			return;
		}	
	}
}
