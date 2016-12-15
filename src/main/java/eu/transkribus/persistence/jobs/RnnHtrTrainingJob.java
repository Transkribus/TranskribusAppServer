package eu.transkribus.persistence.jobs;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.ArrayList;

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
import eu.transkribus.core.util.HtrUtils;
import eu.transkribus.interfaces.IBaseline2Polygon;
import eu.transkribus.interfaces.types.Image;
import eu.transkribus.persistence.io.LocalStorage;
import eu.transkribus.persistence.jobs.abstractJobs.ATrpJob;
import eu.transkribus.persistence.jobs.htr.util.JobCanceledException;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.HtrManager;

public class RnnHtrTrainingJob extends ATrpJob {
	private static final Logger logger = LoggerFactory.getLogger(RnnHtrTrainingJob.class);
	private String modelName;
	private int[] docIds;
	private String numEpochs;
	private String learningRate;
	private String noise;
	private Integer nrOfThreads;
	private Integer trainSizePerEpoch;
	private String baseModel;
	
	private File workDir;
	
	DocManager docMan = new DocManager();

	
	@Override
	public void doProcess(JobExecutionContext context) throws JobExecutionException, JobCanceledException {
		//create workdir
		try {
			workDir = LocalStorage.createHtrTempDir();
		} catch (IOException e) {
			setJobStatusFailed("Could not create workdir!", e);
			return;
		}
		final String workDirPath = workDir.getAbsolutePath();
		logger.info("Writing output to: " + workDirPath);
		
		//check if model by this name exists
		File[] models = HtrUtils.getNetList();
		for(File f : models){
			if(FilenameUtils.getBaseName(f.getName()).equals(modelName)){
				setJobStatusFailed("A net with name \"" + modelName + "\" already exists!");
				return;
			}
		}
		
		final String inputPath = workDir.getAbsolutePath() + File.separator + "input";
		File inputDir = new File(inputPath);
		inputDir.mkdir();
		
		DocExporter ex = new DocExporter();
		ExportOptions opts = new ExportOptions();
		opts.dir = inputPath;
		opts.doWriteImages = true;
		opts.exportAltoXml = false;
		opts.exportPageXml = true;
		opts.pageDirName = "";
		opts.useOcrMasterDir = false;
		opts.writeMets = false;
		opts.fileNamePattern = "${pageId}";
		
		setJobStatusProgress("Exporting files...");
		
		for(int id : docIds){
			try {
				TrpDoc doc = docMan.getDocById(id);
				ex.exportDoc(doc, opts);
			} catch(Exception e){
				setJobStatusFailed("Could not export document " + id, e);
				return;
			}
		}
				
		String[] pageXmls = inputDir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.equals("metadata.xml") && name.endsWith(".xml");
			}
		});
		
		ArrayList<String> inputList = new ArrayList<>(pageXmls.length);
		
		setJobStatusProgress("Creating polygons...");
		
		//process baseline2polygon on all page XMLs
		IBaseline2Polygon laParser = new Baseline2PolygonParser(B2PSeamMultiOriented.class.getName());
		for(String p : pageXmls) {
			final String path = inputDir + File.separator + p;
			
			final String basename = FilenameUtils.getBaseName(p);
			
			String[] hits = inputDir.list(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					final String mime = MimeTypes.getMimeType(FilenameUtils.getExtension(name));
					//is allowed mimetype and not starts with ".", which may occur on mac
					return name.startsWith(basename) && ImgPriority.priorities.containsKey(mime);
			}});
			
			if(hits.length != 1){
				setJobStatusFailed("No image found for page XML: " + path);
				return;
			}
						
			File imgFile = new File(inputPath + File.separator + hits[0]);
			
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
		
		final String trainDataPath = workDir.getAbsolutePath() + File.separator + "trainData";
		File trainDataDir = new File(trainDataPath);
		trainDataDir.mkdir();
		
		TrainHtr trainer = new TrainHtr();
		
		setJobStatusProgress("Creating train data...");
		
		String[] props = PropertyUtil.setProperty(null, "dict", "true");
        props = PropertyUtil.setProperty(props, "stat", "true");
		trainer.createTrainData(input, trainDataPath, trainDataPath + File.separator + HtrManager.CHAR_MAP_FILENAME, props);
		
		setJobStatusProgress("Creating HTR...");
		
//		String[] props2 = PropertyUtil.setProperty(null, "dict", "true");
//        props2 = PropertyUtil.setProperty(props2, "stat", "true");
        TrainHtr instance = new TrainHtr();
        
        final File htrInFile = new File(workDir.getAbsolutePath() + File.separator +  modelName + "_raw.sprnn");
        if(baseModel != null) {
        	File baseModelFile = new File(HtrUtils.NET_PATH + File.separator +  baseModel);
        	if(!baseModelFile.exists()) {
        		setJobStatusFailed("Base model: " + baseModel + " does not exist!");
        		return;
        	}
        	try {
				Files.copy(baseModelFile.toPath(), htrInFile.toPath());
			} catch (IOException e) {
				setJobStatusFailed("Could not duplicate base model!", e);
        		return;
			}
        } else {
	        instance.createHtr(htrInFile.getAbsolutePath(), 
	        		trainDataPath + File.separator + HtrManager.CHAR_MAP_FILENAME, null);
        }
        
        if (!htrInFile.exists()) {
            setJobStatusFailed("Could not create HTR file at " + htrInFile.getAbsolutePath() + "!");
            return;
        }
        
        setJobStatusProgress("Training HTR...");
        
        File htrOutFile = new File(workDir.getAbsolutePath() + File.separator +  modelName + ".sprnn");
        String[] props3 = PropertyUtil.setProperty(null, "NumEpochs", numEpochs); //"200"); //5;2");
        props3 = PropertyUtil.setProperty(props3, "LearningRate", learningRate); //"2e-3"); //5e-3;1e-3");
        props3 = PropertyUtil.setProperty(props3, "Noise", noise); //"no");
        props3 = PropertyUtil.setProperty(props3, "Threads", ""+nrOfThreads);
        props3 = PropertyUtil.setProperty(props3, "TrainSizePerEpoch", ""+trainSizePerEpoch); //"1000");
        trainer.trainHtr(htrInFile.getAbsolutePath(), htrOutFile.getAbsolutePath(), trainDataPath, null, props3);
		
        File htrStoreFile = new File(HtrUtils.NET_PATH + File.separator +  modelName + ".sprnn");
        try {
			Files.move(htrOutFile.toPath(), htrStoreFile.toPath());
		} catch (IOException e) {
			setJobStatusFailed("Could not store HTR file!");
			return;
		}

		workDir.delete();
	}

	public void setModelName(final String modelName){
		this.modelName = modelName;
	}
	
	public void setDocIds(final int[] docIds){
		this.docIds = docIds;
	}

	public void setNumEpochs(String numEpochs) {
		this.numEpochs = numEpochs;
	}

	public void setLearningRate(String learningRate) {
		this.learningRate = learningRate;
	}

	public void setNoise(String noise) {
		this.noise = noise;
	}

	public void setNrOfThreads(Integer nrOfThreads) {
		this.nrOfThreads = nrOfThreads;
	}

	public void setTrainSizePerEpoch(Integer trainSizePerEpoch) {
		this.trainSizePerEpoch = trainSizePerEpoch;
	}
	
	public void setBaseModel(String baseModel) {
		this.baseModel = baseModel;
	}
}
