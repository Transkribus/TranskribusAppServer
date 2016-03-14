package eu.transkribus.appserver.logic.jobs.standard;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.rmi.IRmiServer;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.server.io.LaServerConn;
import eu.transkribus.server.logic.JobManager;

public class DetectBaselinesJobRmi extends DetectBaselinesJob {
	private static final Logger logger = LoggerFactory.getLogger(DetectBaselinesJobRmi.class);
	
	public DetectBaselinesJobRmi(final TrpJobStatus job, final TrpPage page, PcGtsType pc, List<String> regIds) {
		super(job, page, pc, regIds);
	}
	public DetectBaselinesJobRmi(final TrpJobStatus job, final TrpPage page, PcGtsType pc) {
		super(job, page, pc);
	}

	@Override
	public void run() {
		try {
			
			final String pcGts = JaxbUtils.marshalToString(pc);
			
			updateStatus("Running line segmentation via RMI...");
			IRmiServer laServ = LaServerConn.getRemoteObject();			
			final String newPcStr = laServ.addBaselines(imgKey, pcGts, regIds);
			PcGtsType newPc = PageXmlUtils.unmarshal(newPcStr);
			
			updateStatus("Storing transcript...");
			TranscriptManager tMan = new TranscriptManager();
			
			String toolName = null;
			if(newPc.getMetadata().getCreator() != null && !newPc.getMetadata().getCreator().isEmpty()){
				toolName = newPc.getMetadata().getCreator();
			}
			
			tMan.updateTranscript(page.getPageId(), null, job.getUserId(), job.getUserName(), newPc, toolName);
			
			JobManager.getInstance().finishJob(jobId, "DONE", true);
		} catch (Exception e) {
			logger.error("Error in RMI Baseline tool!");
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
			} catch (Exception ex) {
				logger.error("Could not update job in DB! " + jobId);
				ex.printStackTrace();
			}
			logger.error(e.getMessage(), e);
			e.printStackTrace();
		}	
	}
}
