package eu.transkribus.appserver;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.commons.io.FilenameUtils;
import org.dea.fimgstoreclient.FimgStoreGetClient;
import org.dea.fimgstoreclient.utils.MimeTypes;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.uro.citlab.module.baseline2polygon.B2PSeamMultiOriented;
import de.uro.citlab.module.baseline2polygon.Baseline2PolygonParser;
import eu.transkribus.core.io.util.ImgPriority;
import eu.transkribus.interfaces.IBaseline2Polygon;
import eu.transkribus.interfaces.types.Image;
import eu.transkribus.persistence.io.FimgStoreRwConnection;

public class B2PTest {
	private static final Logger logger = LoggerFactory.getLogger(B2PTest.class);
	public static void main(String[] args) throws IllegalArgumentException, IOException, URISyntaxException{

		
//		testUrlImage();

	}
	
	@Test
	public void testUrlImage() throws IllegalArgumentException, IOException, URISyntaxException {
		IBaseline2Polygon laParser = new Baseline2PolygonParser(B2PSeamMultiOriented.class.getName());
		
		URL imgUrl = new URL("https://dbis-thure.uibk.ac.at/f/Get?id=FZLBELNZACHFFLXDWXVRLWAT");
		URL xmlUrl = new URL("https://dbis-thure.uibk.ac.at/f/Get?id=OUBIOMPWUYIHJWDFSLPMRXXX");
		FimgStoreGetClient getter = FimgStoreRwConnection.getGetClient();
		File xml = getter.saveFile(xmlUrl.toURI(), "/tmp/");
		
		Image img = new Image(imgUrl);
		img.getImageBufferedImage(true);
		
		boolean success = false;
		try {
			laParser.process(img, xml.getAbsolutePath(), null, null);
			success = true;
		} catch (Throwable t) {
			logger.error(t.getMessage(), t);
			Assert.fail(t.getMessage());
		}
			
		
		Assert.assertTrue(success);
	}
	
	public static void testLocalImage() throws MalformedURLException {
		String imgPath = "/tmp/212038.jpg";
		String xmlPath = "/tmp/212038.xml";
		
		IBaseline2Polygon laParser = new Baseline2PolygonParser(B2PSeamMultiOriented.class.getName());
		
		Image img = new Image(new File(imgPath).toURI().toURL());
		

		laParser.process(img, xmlPath, null, null);
	}
	
	public static void runOnDir() {
		String inputDirPath = "/tmp/HTR/TRP_HTR_7650568207150595200/input/";
		File inputDir = new File(inputDirPath);
		
		String[] pageXmls = inputDir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return !name.equals("metadata.xml") && name.endsWith(".xml");
			}
		});
		
		ArrayList<String> inputList = new ArrayList<>(pageXmls.length);
		
		logger.info("Creating polygons...");
		
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
				logger.error("No image found for page XML: " + path);
				return;
			}
						
			File imgFile = new File(inputDirPath + File.separator + hits[0]);
			
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
				return;
			}
			
			inputList.add(path);
		}
	}
}
