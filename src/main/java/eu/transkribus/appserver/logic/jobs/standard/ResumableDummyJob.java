package eu.transkribus.appserver.logic.jobs.standard;

import java.sql.SQLException;
import java.util.Properties;

import javax.persistence.EntityNotFoundException;

import org.dea.dealog.db.beans.DeaDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.server.logic.JobManager;

public class ResumableDummyJob extends AResumableTrpJobRunnable {
	private static final Logger logger = LoggerFactory.getLogger(ResumableDummyJob.class);
	private final static String STATE_DOWNLOAD = "download";
	private final static String STATE_OCR = "ocr";
	private final static String STATE_INGEST = "ingest";
	
	private String path;
	private int dealogDocId;
	//set initial state
	private String state = STATE_DOWNLOAD;
	
	private DocManager docMan = new DocManager();
	private TranscriptManager tMan = new TranscriptManager();
	
	public ResumableDummyJob(TrpJobStatus job) {
		super(job);
	}
	
	public ResumableDummyJob(TrpJobStatus job, final String ocrPath) {
		super(job);
		
		//set ocr path
		this.path = ocrPath;
	}

	@Override
	protected void resume(Properties props) {
		if(props.entrySet().size() != 3){
			throw new IllegalArgumentException("OCR job needs 3 params to resume!");
		}
		this.state = props.getProperty("state");
		this.path = props.getProperty("path");
		this.dealogDocId = Integer.parseInt(props.getProperty("dealogDocId"));
		logger.info("state = " + state);
		logger.info("path = " + path);
		logger.info("dealogDocId = " + dealogDocId);
	}
	
	@Override
	protected Properties buildJobData(){
		Properties p = new Properties();
		p.setProperty("state", this.state);
		p.setProperty("path", this.path);
		p.setProperty("dealogDocId", ""+dealogDocId);
		return p;
	}
	
	@Override
	public void run() {
		TrpDoc doc;
		updateStatus("Started dummy job");
		
		try {
			doc = docMan.getDocById(this.job.getDocId());
		} catch (EntityNotFoundException | ReflectiveOperationException | SQLException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			return;
		}
		DeaDocument deaDoc;
		
		logger.info("State is " + state);
		
		switch(state){
		case STATE_DOWNLOAD:
			//download the document files to path
			
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//create dealog docId
			state = STATE_OCR;

			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Exported document", buildJobDataStr()));
			
		case STATE_OCR:
			//wait for entry in dealog db to be set to finished
			
			
				
				
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			state = STATE_INGEST;
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("OCR done", buildJobDataStr()));
		case STATE_INGEST:
			//reingest the updated text
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			updateStatus(JobManager.getInstance().new TrpJobStatusUpdate("Ingested results", buildJobDataStr()));
		}
		try {
			JobManager.getInstance().finishJob(this.job.getJobId(), "Document is updated!", true);
		} catch (EntityNotFoundException | IllegalArgumentException | SQLException
				| ReflectiveOperationException e) {
			logger.error("Could not update job!", e);
			return;
		}
		return;
	}

}
