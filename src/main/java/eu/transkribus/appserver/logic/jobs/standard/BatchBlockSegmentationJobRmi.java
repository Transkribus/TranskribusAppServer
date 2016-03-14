package eu.transkribus.appserver.logic.jobs.standard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.rmi.IRmiServer;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.server.io.LaServerConn;
import eu.transkribus.server.logic.JobManager;

public class BatchBlockSegmentationJobRmi extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(BatchBlockSegmentationJobRmi.class);
	public BatchBlockSegmentationJobRmi(final TrpJobStatus job) {
		super(job);
	}
	@Override
	public void run() {
		try {
			final int docId = job.getDocId();
			DocManager docMan = new DocManager();
			TrpDoc doc = docMan.getDocById(docId);
			
			for(TrpPage p : doc.getPages()){
			
				TrpTranscriptMetadata tmd = p.getCurrentTranscript();
				PcGtsType pc = PageXmlUtils.unmarshal(tmd.getUrl());
				final String pcGts = JaxbUtils.marshalToString(pc);
				
				updateStatus("Running block segmentation via RMI...");
				this.job.setStartTime(System.currentTimeMillis());
				this.job.setState(TrpJobStatus.RUNNING);
				
				IRmiServer laServ = LaServerConn.getRemoteObject();
				final String newPcStr = laServ.getBlockSeg(p.getKey(), pcGts, false);
				PcGtsType newPc = JaxbUtils.unmarshal(newPcStr, PcGtsType.class);
				this.updateStatus("Storing transcript...");
	
				String toolName = null;
				if(newPc.getMetadata().getCreator() != null && !newPc.getMetadata().getCreator().isEmpty()){
					toolName = newPc.getMetadata().getCreator();
				}
				
				TranscriptManager tMan = new TranscriptManager();
				tMan.updateTranscript(p.getPageId(), null, job.getUserId(), job.getUserName(), newPc, toolName);
				
			}
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
