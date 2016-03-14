package eu.transkribus.appserver.logic.jobs.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.rmi.IRmiServer;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.server.io.LaServerConn;
import eu.transkribus.server.logic.JobManager;

public class BlockSegmentationJobRmi extends BlockSegmentationJob {
	private static final Logger logger = LoggerFactory.getLogger(BlockSegmentationJobRmi.class);
	public BlockSegmentationJobRmi(final TrpJobStatus job, final TrpPage page, PcGtsType pc, boolean usePrintspaceOnly) {
		super(job, page, pc, usePrintspaceOnly);
	}
	@Override
	public void run() {
		try {
			
			final String pcGts = JaxbUtils.marshalToString(pc);
			
			updateStatus("Running block segmentation via RMI...");
			this.job.setStartTime(System.currentTimeMillis());
			this.job.setState(TrpJobStatus.RUNNING);
			
			IRmiServer laServ = LaServerConn.getRemoteObject();
			final String newPcStr = laServ.getBlockSeg(imgKey, pcGts, usePrintspaceOnly);
			PcGtsType newPc = JaxbUtils.unmarshal(newPcStr, PcGtsType.class);
			this.updateStatus("Storing transcript...");

			String toolName = null;
			if(newPc.getMetadata().getCreator() != null && !newPc.getMetadata().getCreator().isEmpty()){
				toolName = newPc.getMetadata().getCreator();
			}
			
			TranscriptManager tMan = new TranscriptManager();
			tMan.updateTranscript(page.getPageId(), null, job.getUserId(), job.getUserName(), newPc, toolName);
			JobManager.getInstance().finishJob(jobId, "DONE", true);
		} catch (Exception e) {
			logger.error("Error in RMI Block Segmentation!");
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
			} catch (Exception ex) {
				logger.error("Could not update job in DB! " + jobId);
				ex.printStackTrace();
			}
			logger.error(e.getMessage(), e);
		}	
	}
}
