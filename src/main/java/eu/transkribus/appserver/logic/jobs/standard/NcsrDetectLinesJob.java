package eu.transkribus.appserver.logic.jobs.standard;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.sql.SQLException;

import javax.xml.bind.JAXBException;

import org.apache.http.auth.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ALayoutAnalysisJob;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.rmi.IRmiServer;
import eu.transkribus.core.rmi.util.NcsrToolException;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.io.LaServerConn;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.PageManager;
import eu.transkribus.persistence.logic.TranscriptManager;

public class NcsrDetectLinesJob extends ALayoutAnalysisJob {
	private static final Logger logger = LoggerFactory.getLogger(NcsrDetectLinesJob.class);
	
	public NcsrDetectLinesJob (TrpJobStatus job) {
		super(job);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {
		try {
			
			int pageNr;
			try {
				pageNr = Integer.valueOf(job.getPages());
			} catch (NumberFormatException nfe){
				setJobStatusFailed("Not a single page: " + job.getPages());
				return;
			}
			
			PageManager pMan = new PageManager();
			TranscriptManager tMan = new TranscriptManager();
			TrpPage p = pMan.getPageByNr(job.getDocId(), pageNr);
			
			final String imgKey = p.getKey();
			TrpTranscriptMetadata transcript = null;
			for(TrpTranscriptMetadata t : p.getTranscripts()){
				if(t.getTsId() == transcriptId){
					transcript = t;
					break;
				}
			}
			if(transcript == null){
				this.setJobStatusFailed("Could not find transcript");
				return;
			}
			this.setJobStatusProgress("Running line segmentation tool");
			
			PcGtsType pcGtsType = PageXmlUtils.unmarshal(transcript.getUrl());
			
//			LayoutManager laMan = new LayoutManager();
//			PcGtsType newPc = laMan.getLineSeg(imgKey, pcGtsType, regIds);
//			
			//RMI stuff
			final String pcGtsStr = JaxbUtils.marshalToString(pcGtsType);
//			
			IRmiServer laServ = LaServerConn.getRemoteObject();			
			final String newPcStr = laServ.getLineSeg(imgKey, pcGtsStr, regIds);
			PcGtsType newPc = PageXmlUtils.unmarshal(newPcStr);
			
			tMan.updateTranscript(p.getPageId(), null, job.getUserId(), job.getUserName(), newPc, false, transcript.getTsId(), "NCSR Line segmentation");
		} catch (SQLException | ReflectiveOperationException | JAXBException | NotBoundException | IOException | NcsrToolException | AuthenticationException e) {
			this.setJobStatusFailed(e.getMessage());
			return;
		}
	}
}
