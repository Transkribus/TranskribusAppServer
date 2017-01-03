package eu.transkribus.appserver.logic.jobs.abstractjobs;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.xml.bind.JAXBException;

import org.apache.http.auth.AuthenticationException;
import org.dea.fimgstoreclient.FimgStoreGetClient;

import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.interfaces.ILayoutAnalysis;
import eu.transkribus.interfaces.types.Image;
import eu.transkribus.persistence.io.FimgStoreRwConnection;
import eu.transkribus.persistence.io.LocalStorage;
import eu.transkribus.persistence.logic.PageManager;
import eu.transkribus.persistence.logic.TranscriptManager;

public abstract class ALayoutAnalysisJob extends ATrpJobRunnable {

	protected final Integer transcriptId;
	protected final String[] props;
	protected final List<String> regIds;
	
	protected ALayoutAnalysisJob(TrpJobStatus job) {
		super(job);
		transcriptId = getIntProperty(JobConst.PROP_TRANSCRIPT_ID);
		regIds = getStringListProperty(JobConst.PROP_REG_IDS);
		
		// TODO
		this.props = new String[]{};
	}
	
	protected void analyze(TrpJobStatus job, ILayoutAnalysis tool, Image image, final String xmlKey) throws IOException, SQLException, ReflectiveOperationException, AuthenticationException, JAXBException{
		if(tool == null) throw new IllegalArgumentException("Tool is null!");
		if(image == null) throw new IllegalArgumentException("Image is null!");
		if(xmlKey == null || xmlKey.isEmpty()) throw new IllegalArgumentException("xmlKey is null or empty!");
		if(job == null) job = jMan.getJob(getJobId());
		
		TranscriptManager tMan = new TranscriptManager();
		
		//create temp folder
		final File tmp = LocalStorage.getTempStorage();
		final File workDir = new File(tmp.getAbsolutePath() + File.separator + "la-" + UUID.randomUUID());
		if(!workDir.mkdir()){
			throw new IOException("Could not create workdir at: " + workDir.getAbsolutePath());
		}
		
		//download xmlFile
		FimgStoreGetClient getter = FimgStoreRwConnection.getGetClient();
		File xmlFile = getter.saveFile(xmlKey, workDir.getAbsolutePath());
		
		String[] regArr;
		if(regIds == null || regIds.isEmpty()){
			regArr = null;
		} else {
			regArr = regIds.toArray(new String[regIds.size()]);
		}
		
		//do Process
		tool.process(image, xmlFile.getAbsolutePath(), regArr, props);
		//get toolname and version
		final String toolName = tool.getToolName() + " " + tool.getVersion();
		//update on filestore and DB
		PageManager pMan = new PageManager();
		int pageNr;
		try {
			pageNr = Integer.valueOf(job.getPages());
		} catch (NumberFormatException nfe){
			setJobStatusFailed("Not a single page: " + job.getPages());
			return;
		}
		
		final int pageId = pMan.getPageId(job.getDocId(), pageNr);
		tMan.updateTranscript(pageId, null, job.getUserId(), job.getUserName(), 
				PageXmlUtils.unmarshal(xmlFile), toolName, true);
	}
}
