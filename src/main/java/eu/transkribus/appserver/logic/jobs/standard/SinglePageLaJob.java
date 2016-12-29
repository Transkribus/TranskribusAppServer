package eu.transkribus.appserver.logic.jobs.standard;
//package eu.transkribus.persistence.jobs;
//
//import java.io.IOException;
//import java.net.URI;
//import java.sql.SQLException;
//
//import javax.xml.bind.JAXBException;
//
//import org.apache.http.auth.AuthenticationException;
//import org.dea.fimgstoreclient.beans.ImgType;
//import org.dea.fimgstoreclient.utils.FimgStoreUriBuilder;
//import org.quartz.JobExecutionContext;
//import org.quartz.JobExecutionException;
//
//import eu.transkribus.core.model.beans.TrpPage;
//import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
//import eu.transkribus.interfaces.ILayoutAnalysis;
//import eu.transkribus.interfaces.native_wrapper.NativeLayoutAnalysisProxy;
//import eu.transkribus.interfaces.types.Image;
//import eu.transkribus.persistence.TrpPersistenceConf;
//import eu.transkribus.persistence.io.FimgStoreRwConnection;
//import eu.transkribus.persistence.jobs.abstractJobs.ALayoutAnalysisJob;
//import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
//import eu.transkribus.persistence.logic.PageManager;
//
//public class SinglePageLaJob extends ALayoutAnalysisJob {
//	
//	@Override
//	public void doProcess(JobExecutionContext context) throws JobExecutionException, JobCanceledException {
//		try {
//			
//			PageManager pMan = new PageManager();
//			TrpPage p = pMan.getPageByNr(docId, pageNr);
//			
//			final String imgKey = p.getKey();
//			TrpTranscriptMetadata transcript = null;
//			for(TrpTranscriptMetadata t : p.getTranscripts()){
//				if(t.getTsId() == transcriptId){
//					transcript = t;
//					break;
//				}
//			}
//			if(transcript == null){
//				this.setJobStatusFailed("Could not find transcript");
//				return;
//			}
//			this.setJobStatusProgress("Running LA tool: " + getJobStatus().getJobImpl().getLibName());
//			
//			FimgStoreUriBuilder ub = FimgStoreRwConnection.getUriBuilder();
//			URI uri = ub.getImgUri(imgKey, ImgType.bin);
//			Image image = new Image(uri.toURL());
//			
//			final String lib = TrpPersistenceConf.getString("lib_path") + getJobStatus().getJobImpl().getLibName();
//			
//			ILayoutAnalysis tool = new  NativeLayoutAnalysisProxy(lib, new String[]{});			
//			super.analyze(getJobStatus(), tool, image, transcript.getKey());
//			
//		} catch (SQLException | ReflectiveOperationException | JAXBException | IOException | AuthenticationException e) {
//			this.setJobStatusFailed(e.getMessage());
//			return;
//		}
//	}
//}
