package eu.transkribus.appserver.logic.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.model.beans.pagecontent_trp.TrpPageType;
import eu.transkribus.core.rmi.IRmiServer;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.server.io.LaServerConn;
import eu.transkribus.server.logic.JobManager;

public class BatchLineSegmentationJobRmi extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(BatchLineSegmentationJobRmi.class);
	public BatchLineSegmentationJobRmi(final TrpJobStatus job) {
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
				
				updateStatus("Running line segmentation via RMI...");
				IRmiServer laServ = LaServerConn.getRemoteObject();
				try{
					final String newPcStr = laServ.getLineSeg(p.getKey(), pcGts, null);
					PcGtsType newPc = PageXmlUtils.unmarshal(newPcStr);
					
					logger.info("Updating XML IDs");
					TrpPageType pageType = (TrpPageType)newPc.getPage();
					pageType.updateIDsAccordingToCurrentSorting();
					
					updateStatus("Storing transcript...");
					TranscriptManager tMan = new TranscriptManager();
					
					String toolName = null;
					if(newPc.getMetadata().getCreator() != null && !newPc.getMetadata().getCreator().isEmpty()){
						toolName = newPc.getMetadata().getCreator();
					}
					
					tMan.updateTranscript(p.getPageId(), null, job.getUserId(), job.getUserName(), newPc, toolName);
				} catch (Exception ex){
					logger.error("Line segmentation failed on doc " + p.getDocId() + ", page " + p.getPageNr() + "!", ex);
				}
			}
			JobManager.getInstance().finishJob(jobId, "DONE", true);
		} catch (Exception e) {
			logger.error("Error in RMI Line Segmentation!");
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
