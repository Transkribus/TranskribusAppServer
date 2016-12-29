package eu.transkribus.appserver.logic.jobs.standard;

import java.io.File;
import java.io.IOException;

import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ADocImportJob;
import eu.transkribus.core.io.LocalDocReader;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.mets.Mets;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;

public class MetsImportJob extends ADocImportJob {
	private static final Logger logger = LoggerFactory.getLogger(MetsImportJob.class);
	
	public MetsImportJob(TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		try {
			logger.info("Doc creation thread started.");
			
			File metsFile = new File(path);
			File docDir = metsFile.getParentFile();
			this.setJobStatusProgress("Importing METS document...");
			logger.debug("Reading METS file...");
			Mets mets = JaxbUtils.unmarshal(metsFile, Mets.class, TrpDocMetadata.class);
			logger.debug("Done.");
			TrpDoc doc = LocalDocReader.load(mets, docDir);
			
			super.importDoc(colId, job.getDocId(), doc, job.getUserId(), job.getUserName());
			
		} catch (JAXBException | IOException e) {
			this.setJobStatusFailed(e.getMessage());
			return;
		} catch (Exception e) {
			this.setJobStatusFailed(e.getMessage());
			return;
		}
	}
}
