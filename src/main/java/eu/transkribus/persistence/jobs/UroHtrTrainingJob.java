package eu.transkribus.persistence.jobs;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Observer;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.dea.fimgstoreclient.utils.MimeTypes;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uro.citlab.module.baseline2polygon.B2PSeamMultiOriented;
import de.uro.citlab.module.baseline2polygon.Baseline2PolygonParser;
import de.uro.citlab.module.train.TrainHtr;
import de.uro.citlab.module.util.PropertyUtil;
import eu.transkribus.core.io.DocExporter;
import eu.transkribus.core.io.DocExporter.ExportOptions;
import eu.transkribus.core.io.util.ImgPriority;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpHtr;
import eu.transkribus.core.model.beans.UroHtrTrainConfig;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.core.util.HtrUtils;
import eu.transkribus.interfaces.IBaseline2Polygon;
import eu.transkribus.interfaces.types.Image;
import eu.transkribus.persistence.DbConnection;
import eu.transkribus.persistence.TrpPersistenceConf;
import eu.transkribus.persistence.dao.UserDao;
import eu.transkribus.persistence.io.LocalStorage;
import eu.transkribus.persistence.jobs.abstractJobs.ATrpJob;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.CollectionManager;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.HtrManager;
import eu.transkribus.persistence.util.MailUtils;

public class UroHtrTrainingJob extends ATrpJob {
	private static final Logger logger = LoggerFactory.getLogger(UroHtrTrainingJob.class);
	private UroHtrTrainConfig config;
	private Integer nrOfThreads;
	
	private File workDir;
	
	private DocManager docMan = new DocManager();
	private CollectionManager colMan = new CollectionManager();
	private HtrManager htrMan = new HtrManager();
	
	private Observer jobUpdateObserver = new JobUpdateObserver();
	
	@Override
	public void doProcess(JobExecutionContext context) throws JobExecutionException, JobCanceledException {
		
		if(config == null) {
			throw new JobExecutionException("No Config!", false);
		}
		
		//init trainer and properties
		TrainHtr trainer = new TrainHtr();
		String[] createTrainDataProps = PropertyUtil.setProperty(null, "dict", "true");
		createTrainDataProps = PropertyUtil.setProperty(createTrainDataProps, "stat", "true");
		
		docMan.addObserver(jobUpdateObserver);
		
		//init train input path
		final String trainInputPath = workDir.getAbsolutePath() + File.separator + "trainInput";
		File trainInputDir = new File(trainInputPath);
		trainInputDir.mkdir();
		
		//init traindata Path
		final String trainDataPath = workDir.getAbsolutePath() + File.separator + "trainData";
		File trainDataDir = new File(trainDataPath);
		trainDataDir.mkdir();
		
		//init htrModel base path
		String htrModelBasePath = TrpPersistenceConf.getString("htr_model_base_path");
		htrModelBasePath += "/" + DbConnection.getDbServiceName() + "/net";
		File htrModelBaseDir = new File(htrModelBasePath);
		if(!htrModelBaseDir.isDirectory()) {
			htrModelBaseDir.mkdirs();
		}
		
		//init docExporter
		DocExporter ex = new DocExporter();
		final ExportOptions opts = new ExportOptions();
		opts.dir = trainInputPath;
		opts.doWriteImages = true;
		opts.exportAltoXml = false;
		opts.exportFatXml = false;
		opts.exportPageXml = true;
		opts.pageDirName = "";
		opts.useOcrMasterDir = false;
		opts.writeMets = false;
		opts.fileNamePattern = "${pageId}";
		
		//create workdir
		try {
			workDir = LocalStorage.createHtrTempDir();
		} catch (IOException e) {
			setJobStatusFailed("Could not create workdir!", e);
			return;
		}
		final String workDirPath = workDir.getAbsolutePath();
		logger.info("Writing output to: " + workDirPath);
		
		//create gt document
		TrpDoc gt;
		try {
			gt = docMan.duplicateDocument("TRAIN_URO_" + config.getModelName(), userId, userName, config.getTrainList());
		
			colMan.addDocToCollection(gt.getId(), -1);
		} catch (Exception e2) {
			setJobStatusFailed("Could not create TRAIN GT document!", e2);
			return;
		}
		
		setJobStatusProgress("Exporting train files...");
		
		//export gt document
		try {
//			TrpDoc doc = docMan.getDocById(gt.getId());
			ex.exportDoc(gt, opts);
		} catch(Exception e){
			setJobStatusFailed("Could not export Train GT document " + gt.getId(), e);
			return;
		}
		
		//process baseline2polygon on all page XMLs	
		setJobStatusProgress("Creating train polygons...");
		IBaseline2Polygon laParser = new Baseline2PolygonParser(B2PSeamMultiOriented.class.getName());
		String[] input = null;
		try {
			input = baseline2polygon(laParser, trainInputDir);
		} catch(IOException ioe) {
			setJobStatusFailed(ioe.getMessage(), ioe);
			return;
		}
		
		String testDataPath = null;
		//create Test data if applicable
		if(config.getTestList() != null && !config.getTestList().isEmpty()) {
			
			//init path
			final String testInputPath = workDir.getAbsolutePath() + File.separator + "testInput";
			File testInputDir = new File(testInputPath);
			testInputDir.mkdir();
			
			//init path for line imgs
			testDataPath = workDir.getAbsolutePath() + File.separator + "testData";
			File testDataDir = new File(testDataPath);
			testDataDir.mkdir();
			
			//set exporter opts
			opts.dir = testInputPath;
			
			TrpDoc testGt;
			try {
				testGt = docMan.duplicateDocument("TEST_URO_" + config.getModelName(), userId, userName, 
						config.getTestList());
				
				colMan.addDocToCollection(gt.getId(), -1);
			} catch (Exception e2) {
				setJobStatusFailed("Could not create TEST GT document!", e2);
				return;
			}
			
			setJobStatusProgress("Exporting validation files...");

			try {
				ex.exportDoc(testGt, opts);
			} catch(Exception e){
				setJobStatusFailed("Could not export Train GT document " + gt.getId(), e);
				return;
			}
				
			setJobStatusProgress("Creating validation polygons...");
			//process baseline2polygon on all page XMLs
			String[] valInput;
			try {
				valInput = baseline2polygon(laParser, testInputDir);
			} catch(IOException ioe) {
				setJobStatusFailed(ioe.getMessage(), ioe);
				return;
			}
			
			setJobStatusProgress("Creating test data...");
			
			//create Test Data
			trainer.createTrainData(valInput, testDataPath, testDataPath + File.separator + HtrUtils.CHARACTER_MAP_NAME, createTrainDataProps);
		}
		
		setJobStatusProgress("Creating train data...");
		
		trainer.createTrainData(input, trainDataPath, trainDataPath + File.separator + HtrUtils.CHARACTER_MAP_NAME, createTrainDataProps);
		
		setJobStatusProgress("Creating HTR...");
        
        File htrInFile;
        final Integer baseModelId;
        if(config.getBaseModelId() == null) {
//        	String[] htrInitProps = PropertyUtil.setProperty(null, "dict", "true");
//            htrInitProps = PropertyUtil.setProperty(htrInitProps, "stat", "true");
	        htrInFile = new File(workDir.getAbsolutePath() + File.separator +  config.getModelName() + "_raw.sprnn");
	        trainer.createHtr(htrInFile.getAbsolutePath(), 
	        		trainDataPath + File.separator + HtrUtils.CHARACTER_MAP_NAME, null);
	        baseModelId = null;
	        if (!htrInFile.exists()) {
	            setJobStatusFailed("Could not create HTR file at " + htrInFile.getAbsolutePath() + "!");
	            return;
	        }
        } else {
        	TrpHtr htrIn;
        	try {
				htrIn = htrMan.getHtrById(config.getBaseModelId());
			} catch (EntityNotFoundException enfe) {
				setJobStatusFailed("Bad config! Base HTR model does not exist!");
				return;
			} catch (SQLException | ReflectiveOperationException e) {
				setJobStatusFailed("Server error. Could not retrieve base HTR model!", e);
				return;
			}
        	baseModelId = config.getBaseModelId();
        	htrInFile = new File(htrIn.getPath());
        }
  
        setJobStatusProgress("Training HTR...");
        
        File htrOutFile = new File(workDir.getAbsolutePath() + File.separator +  config.getModelName() + ".sprnn");
        String[] htrTrainProps = PropertyUtil.setProperty(null, "NumEpochs", config.getNumEpochs()); //"200"); //5;2");
        htrTrainProps = PropertyUtil.setProperty(htrTrainProps, "LearningRate", config.getLearningRate()); //"2e-3"); //5e-3;1e-3");
        htrTrainProps = PropertyUtil.setProperty(htrTrainProps, "Noise", config.getNoise()); //"no");
        htrTrainProps = PropertyUtil.setProperty(htrTrainProps, "Threads", ""+nrOfThreads);
        htrTrainProps = PropertyUtil.setProperty(htrTrainProps, "TrainSizePerEpoch", ""+config.getTrainSizePerEpoch()); //"1000");
        trainer.trainHtr(htrInFile.getAbsolutePath(), htrOutFile.getAbsolutePath(), trainDataPath, testDataPath, htrTrainProps);
		
        int htrId;
		try {
			htrId = htrMan.getNextHtrId();
		
			String filename = htrId + "_" + config.getModelName() + ".sprnn";
			
	        File htrStoreFile = new File(htrModelBasePath + File.separator 
	        		+ filename);
	        Files.move(htrOutFile.toPath(), htrStoreFile.toPath());
			
	        TrpHtr htr = new TrpHtr();
	        htr.setHtrId(htrId);
	        htr.setCreated(new java.sql.Timestamp(System.currentTimeMillis()));
	        htr.setGtDocId(gt.getId());
	        htr.setName(config.getModelName());
	        htr.setPath(filename);
	        htr.setProvider("CITlab");
	        htr.setDescription(config.getDescription());
	        htr.setBaseModelId(baseModelId);
	        
	        htrMan.storeHtr(config.getColId(), htr);
	        	        
		} catch (IOException | SQLException | ReflectiveOperationException e1) {
			setJobStatusFailed("Could not persist HTR model!", e1);
			return;
		} finally {
			workDir.delete();
		}
		
		//send mail to user
		try {	
			UserDao ud = new UserDao();
			TrpUser user = ud.getUser(this.getJobStatus().getUserId(), true);
			final String email = user.getEmail();
			if(email == null || email.isEmpty()){
				logger.warn("User has no email address! Skipping mail...");
			} else {
				String msg = "Dear " + user.getFirstname() + ",\n";
				msg += "your handwriting model \"" + config.getModelName() 
						+ "\" is now trained and ready to use for recognition processes in Transkribus!\n";
				MailUtils.sendMailFromUibkAddress(email, MailUtils.TRANSKRIBUS_EMAIL_MAIL_SERVER.getEmail(), "HTR Training is complete", msg, null, true, true);
			}
		} catch (SQLException | IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private String[] baseline2polygon(IBaseline2Polygon laParser, File inputDir) throws IOException {
		String[] pageXmls = inputDir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.equals("metadata.xml") && name.endsWith(".xml");
			}
		});
		
		ArrayList<String> inputList = new ArrayList<>(pageXmls.length);
		for(String p : pageXmls) {
			final String path = inputDir.getAbsolutePath() + File.separator + p;
			
			final String basename = FilenameUtils.getBaseName(p);
			
			String[] hits = inputDir.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					final String mime = MimeTypes.getMimeType(FilenameUtils.getExtension(name));
					//is allowed mimetype and not starts with ".", which may occur on mac
					return name.startsWith(basename) && ImgPriority.priorities.containsKey(mime);
			}});
			
			if(hits.length != 1){
				throw new IOException("No image found for page XML: " + path);
			}
						
			File imgFile = new File(inputDir.getAbsolutePath() + File.separator + hits[0]);
			
			Image img;
			try {
				img = new Image(imgFile.toURI().toURL());
			} catch (MalformedURLException e) {
				logger.error("Could not build URL: " + imgFile.getAbsolutePath(), e);
				continue;
			}
			try { 
				laParser.process(img, path, null, null);
			} catch (Exception e){
				//TODO remove this element from pageXmls[]
				logger.error("Baseline2Polygon failed on file: " + path, e);
				File bugsStorage = new File(LocalStorage.getBugsStorage().getAbsolutePath() + File.separator + "b2p");
				try {
					FileUtils.copyFileToDirectory(new File(path), bugsStorage);
					FileUtils.copyFileToDirectory(imgFile, bugsStorage);
					File textFile = new File(bugsStorage.getAbsolutePath() + File.separator 
							+ FilenameUtils.getBaseName(imgFile.getName()) + ".txt");
					FileUtils.writeStringToFile(textFile, e.getMessage());
				} catch(Exception e1) {
					logger.error("Could not store erroneous files!", e1);
				}
				continue;
			}
			
			inputList.add(path);
		}
		String[] input = inputList.toArray(new String[inputList.size()]);
		return input;
	}

	public void setConfig(UroHtrTrainConfig config){
		this.config = config;
	}

	public void setNrOfThreads(Integer nrOfThreads) {
		this.nrOfThreads = nrOfThreads;
	}
}
