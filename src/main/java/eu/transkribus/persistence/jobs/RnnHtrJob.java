package eu.transkribus.persistence.jobs;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.xml.bind.JAXBException;

import org.apache.http.auth.AuthenticationException;
import org.dea.fimgstoreclient.FimgStoreGetClient;
import org.dea.fimgstoreclient.beans.ImgType;
import org.dea.fimgstoreclient.utils.FimgStoreUriBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uro.citlab.module.baseline2polygon.B2PSeamMultiOriented;
import de.uro.citlab.module.baseline2polygon.Baseline2PolygonParser;
import de.uro.citlab.module.htr.HTRParser;
import de.uro.citlab.module.la.B2PSeam;
import de.uro.citlab.module.util.PropertyUtil;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpDocMetadata;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.model.beans.job.TrpJobStatus;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.interfaces.IBaseline2Polygon;
import eu.transkribus.interfaces.types.Image;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.persistence.io.FimgStoreRwConnection;
import eu.transkribus.persistence.io.LocalStorage;
import eu.transkribus.persistence.jobs.abstractJobs.ATrpJob;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.TranscriptManager;
import eu.transkribus.persistence.util.MailUtils;

public class RnnHtrJob extends ATrpJob {
	private static final Logger logger = LoggerFactory.getLogger(RnnHtrJob.class);
	
	private String modelName;
	private String dictName;
//	private String state = JobConst.STATE_HTR;
	
	@Override
	public void doProcess(JobExecutionContext context) throws JobExecutionException, JobCanceledException {

		setJobStatusProgress("Setting up workdir...");
		
		File workDir;
		try {
			workDir = LocalStorage.createHtrTempDir();
		} catch (IOException e1) {
			setJobStatusFailed(e1.getMessage(), e1);
			return;
		}
		File workDirIn = new File(workDir.getAbsolutePath() + File.separator + "in");
		File workDirOut = new File(workDir.getAbsolutePath() + File.separator + "out");
		if(!workDirIn.mkdirs() || !workDirOut.mkdirs()){
			setJobStatusFailed("Could not create temp Dir!");
			return;
		}
		
		setJobStatusProgress("Preparing model...");
		
		final String baseDir = "/mnt/dea_scratch/TRP/HTR/RNN";
		final File net = new File(baseDir + "/net/" + modelName);
		final File dict = new File(baseDir + "/dict/" + dictName);
		
		if(!net.isFile()){
			setJobStatusFailed("A net by this name does not exist: " + modelName);
			return;
		}
		if(!dict.isFile()){
			setJobStatusFailed("A dictionary by this name does not exist: " + dictName);
			return;
		}
		
		setJobStatusProgress("Loading LA module...");
		//FIXME get scratch from LocalStorage/properties
		
		
		IBaseline2Polygon laParser = new Baseline2PolygonParser(B2PSeamMultiOriented.class.getName());
//			laParser = new Baseline2PolygonParser();
//		String s = "/mnt/dea_scratch/TRP/modules/b2p/20160405_dft.bin";
//		try {
//			laParser = (IBaseline2Polygon) IO.load(new File(s));
//		} catch (ClassNotFoundException | IOException e1) {
//			setJobStatusFailed("Could not load LA module");
//			return;
//		}
		
//		System.setProperty("vendorName", "asd");
//		Object o = load(new File("/tmp/test.bin"));
//
		setJobStatusProgress("Done loading module. Running HTR...");
	
		HTRParser htrParser = new HTRParser();
		
		DocManager docMan = new DocManager();
		TrpDoc doc;
		try {
			doc = docMan.getDocById(docId);
		} catch (EntityNotFoundException | ReflectiveOperationException | SQLException e1) {
			this.setJobStatusFailed("Could not get document: + " + docId);
			return;
		}

		List<Integer> pageList;
		try {
			pageList = CoreUtils.parseRangeListStrToList(pages, doc.getNPages());
		} catch (IOException e) {
			logger.error("Could not parse page range String!");
			this.setJobStatusFailed("Could not parse page range String! " + pages);
			return;
		}
		
		TranscriptManager tMan = new TranscriptManager();
		FimgStoreGetClient getter = FimgStoreRwConnection.getGetClient();
		
		//FIXME
		final String toolName = "RNN HTR";
		
		for(Integer pageNr : pageList){
			TrpPage p = doc.getPages().get(pageNr);
			TrpTranscriptMetadata  tmd = p.getCurrentTranscript();
			
			FimgStoreUriBuilder uriBuilder = FimgStoreRwConnection.getUriBuilder();
			URL url;
			try {
				url = uriBuilder.getImgUri(p.getKey(), ImgType.orig).toURL();
			} catch (MalformedURLException | IllegalArgumentException e3) {
				setJobStatusFailed("Could not build URL for image of page " + p.getPageNr() + ": " + p.getKey(), e3);
				return;
			}
			logger.info("Reading image at: " + url.toString());
			Image i = new Image(url);
			try {
				i.getImageBufferedImage(true);
			} catch (RuntimeException e1) {
				setJobStatusFailed("Could not convert image for page " + p.getPageNr() + "! ", e1);
				return;
			} catch (UnsatisfiedLinkError e2){
				setJobStatusFailed("Could not convert image for page " + p.getPageNr() + "! ", e2);
				return;
			}
			
			File inFile;
			try {
				inFile = getter.saveFile(tmd.getKey(), workDirIn.getAbsolutePath());
			} catch (IOException e1) {
				setJobStatusFailed("Could not save image for page " + p.getPageNr() + "!", e1);
				return;
			}
			
			logger.info("Running LA");
			
			try{
				laParser.process(i, inFile.getAbsolutePath(), null, null);
			} catch (Exception e){
				setJobStatusFailed("Failed to process page "+ p.getPageNr(), e);
				return;
			}
			PcGtsType pc;
			try {
				pc = PageXmlUtils.unmarshal(inFile);
			} catch (JAXBException e) {
				setJobStatusFailed("Could not read PAGE file at " + inFile.getAbsolutePath(), e);
				return;
			}
			
			setJobStatusProgress("Running HTR on page: " + p.getPageNr());			
//			String[] setProperty = PropertyUtil.setProperty(null, "raw", r.nextBoolean() ? "true" : "false");
            
			htrParser.process(net.getAbsolutePath(), dict.getAbsolutePath(), null, i, pc,
					workDir.getAbsolutePath(), null, new String[]{});
			setJobStatusProgress("HTR done on page: " + p.getPageNr() + ". Storing...");
			
			try {
				tMan.updateTranscript(p.getPageId(), null, 43, userName, pc, toolName);
			} catch (AuthenticationException | SQLException | ReflectiveOperationException | JAXBException
					| IOException e) {
				setJobStatusFailed("Could not update transcript for page " + pageNr, e);
				return;
			}
		}
		
		try {
			//send mail to user
			UserDao ud = new UserDao();
			TrpJobStatus job = this.getJobStatus();
			TrpUser user = ud.getUser(job.getUserId(), true);
			TrpDocMetadata docMd = docMan.getDocMdById(job.getDocId());
			final String email = user.getEmail();
			if(email == null || email.isEmpty()){
				logger.warn("User has no email address! Skipping mail...");
			} else {
				String msg = "Dear " + user.getFirstname() + ",\n";
				msg += "the handwritten text recognition in your document\n" 
						+ "ID " + job.getDocId() + ", Title: " + docMd.getTitle() + "\n";
						if(job.getPageNr() > 0){
							msg += "Page: " + job.getPageNr() + "\n";
						}
				msg +="\nwas successfully completed! ";
				msg += "You can now review the result in Transkribus.";
				MailUtils.sendMailFromUibkAddress(email, MailUtils.TRANSKRIBUS_EMAIL_MAIL_SERVER.getEmail(), "HTR process is complete", msg, null, true, true);
			}
		} catch (IOException | EntityNotFoundException | SQLException | IllegalArgumentException | ReflectiveOperationException e){
			logger.error("Could not send mail!", e);
		}
	}
	
//	private File createHtrBin(File workDir, final String netName, final String dictName) throws InvalidParameterException, IOException{
//		File out = new File(workDir.getAbsolutePath() + File.separator 
//				+ "HTR.bin");
//		//TODO get storage path from LocalStorage/properties
//		final String baseDir = "/mnt/dea_scratch/TRP/HTR/RNN";
//		File net = new File(baseDir + "/net/" + netName);
//		File dict = new File(baseDir + "/dict/" + dictName);
//		
//		if(!net.isFile()){
//			throw new IOException("A net by this name does not exist: " + netName);
//		}
//		if(!dict.isFile()){
//			throw new IOException("A dictionary by this name does not exist: " + dictName);
//		}
//		
//		String[] args = (""
//			+ "-htr/dec " + de.planet.tech.langmod.LangModFullText.class.getName() + " "
//			+ "-htr/dec/wd/netfilename " + net.getAbsolutePath() + " "
//			+ "-htr/dict " + de.planet.tech.util.types.DictOccurrence.class.getName() + " "
//			+ "-htr/dict_0/dict " + dict.getAbsolutePath() + " "
//			+ "-htr/dict_0/maxanz 10000 "
//			+ "-o " + out.getAbsolutePath() + " "
//			+ "").split(" ");		
//		
//		HtrCreator instance = new HtrCreator();
//		ParamSet ps = new ParamSet();
//		ps.setCommandLineArgs(args);    // allow early parsing
//		ps = instance.getDefaultParamSet(ps);
//		ps = ParamSet.parse(ps, args, ParamSet.ParseMode.FORCE); // be strict, don't accept generic parameter
//		instance.setParamSet(ps);
//		instance.init();
//		instance.run();		
//		return out;
//	}

//	public void setState(String state) {
//		this.state = state;
//	}

	public void setModelName(final String modelName) {
		this.modelName = modelName;
	}
	public void setDictName(final String dictName) {
		this.dictName = dictName;
	}

}
