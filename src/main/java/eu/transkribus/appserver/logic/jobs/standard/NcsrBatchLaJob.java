package eu.transkribus.appserver.logic.jobs.standard;

import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.xml.bind.JAXBException;

import org.apache.http.auth.AuthenticationException;

import eu.transkribus.appserver.logic.jobs.abstractjobs.ALayoutAnalysisJob;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.rest.JobConst;
import eu.transkribus.core.rmi.IRmiServer;
import eu.transkribus.core.rmi.util.NcsrToolException;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.core.util.JaxbUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.persistence.io.LaServerConn;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.TranscriptManager;

public class NcsrBatchLaJob extends ALayoutAnalysisJob {
	protected final boolean doBlockSeg;
	protected final boolean doLineSeg;

	public NcsrBatchLaJob(TrpJobStatus job) {
		super(job);
		doBlockSeg = getBoolProperty(JobConst.PROP_DO_BLOCK_SEG);
		doLineSeg = getBoolProperty(JobConst.PROP_DO_LINE_SEG);
	}
	
	@Override
	public void doProcess() throws JobCanceledException {

		if (!doBlockSeg && !doLineSeg) {
			return;
		}

		DocManager man = new DocManager();
		TranscriptManager tMan = new TranscriptManager();
		IRmiServer laServ;
		try {
			laServ = LaServerConn.getRemoteObject();
		} catch (MalformedURLException | RemoteException | NotBoundException e2) {
			setJobStatusFailed("Could not connect to Layout Analysis server!", e2);
			return;
		}
		TrpDoc doc;
		try {
			doc = man.getDocById(job.getDocId());
		} catch (EntityNotFoundException | ReflectiveOperationException | SQLException e2) {
			setJobStatusFailed("Could not retrieve document " + job.getDocId(), e2);
			return;
		}

		List<Integer> pageList;
		try {
			pageList = CoreUtils.parseRangeListStrToList(job.getPages(), doc.getNPages());
		} catch (IOException e1) {
			setJobStatusFailed("Could not parse page range: " + job.getPages());
			return;
		}

		for (int pi : pageList) {
			try {
				TrpPage page = doc.getPages().get(pi);
				TrpTranscriptMetadata transcript = page.getCurrentTranscript();
				final String imgKey = page.getKey();

				PcGtsType pcGtsType = PageXmlUtils.unmarshal(transcript.getUrl());
				String pcGtsStr = JaxbUtils.marshalToString(pcGtsType);
				
				if (doBlockSeg) {
					this.setJobStatusProgress("Running block segmentation tool. Page: " + page.getPageNr());
					pcGtsStr = laServ.getBlockSeg(imgKey, pcGtsStr, false);
				}

				if (doLineSeg) {
					this.setJobStatusProgress("Running line segmentation tool. Page: " + page.getPageNr());
					pcGtsStr = laServ.getLineSeg(imgKey, pcGtsStr, regIds);
				}
				
				PcGtsType newPc = PageXmlUtils.unmarshal(pcGtsStr);

				tMan.updateTranscript(page.getPageId(), null, job.getUserId(), job.getUserName(), newPc, false, transcript.getTsId(),
						"NCSR Layout Analysis");

			} catch (SQLException | ReflectiveOperationException | JAXBException | IOException
					| NcsrToolException | AuthenticationException e) {
				this.setJobStatusFailed(e.getMessage());
				return;
			}
		}
	}
}
