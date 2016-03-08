package eu.transkribus.appserver.logic.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.laserver.logic.LayoutManager;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.server.logic.JobManager;

public class BlockSegmentationJob extends ATrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(BlockSegmentationJob.class);
	private LayoutManager lm = null;
	protected final String imgKey;
	protected final TrpPage page;
	protected PcGtsType pc;
	protected final boolean usePrintspaceOnly;
	public BlockSegmentationJob(TrpJobStatus job, final TrpPage page, PcGtsType pc, boolean usePrintspaceOnly) {
		super(job);
		this.page = page;
		this.imgKey = page.getKey();
		this.pc = pc;
		this.usePrintspaceOnly = usePrintspaceOnly;
	}
	@Override
	public void run() {
		try {
			PassThroughObserver o = new PassThroughObserver();
			lm = new LayoutManager();
			lm.addObserver(o);
			PcGtsType newPc = lm.getBlockSeg(imgKey, pc, usePrintspaceOnly);
			updateStatus("Storing transcript...");
			TranscriptManager tMan = new TranscriptManager();
			
			String toolName = null;
			if(newPc.getMetadata().getCreator() != null && !newPc.getMetadata().getCreator().isEmpty()){
				toolName = newPc.getMetadata().getCreator();
			}
			
			tMan.updateTranscript(page.getPageId(), null, job.getUserId(), job.getUserName(), newPc, toolName);
			JobManager.getInstance().finishJob(jobId, "DONE", true);
		} catch (Exception e) {
			try {
				JobManager.getInstance().finishJob(jobId, e.getMessage(), false);
			} catch (Exception ex) {
				logger.error("Could not update job in DB! " + jobId);
				ex.printStackTrace();
			}
			logger.error(e.getMessage(), e);
			e.printStackTrace();
		} finally {
			if(lm != null){
				lm.destroy();
			}
		}		
	}
}
