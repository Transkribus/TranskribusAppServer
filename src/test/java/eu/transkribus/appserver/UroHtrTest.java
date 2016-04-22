package eu.transkribus.appserver;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.SQLException;

import javax.persistence.EntityNotFoundException;
import javax.xml.bind.JAXBException;

import org.apache.http.auth.AuthenticationException;
import org.dea.fimgstoreclient.FimgStoreGetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.achteck.misc.exception.InvalidParameterException;

import de.uro.citlab.module.htr.HTRParser;
import de.uro.citlab.module.util.IO;
import eu.transkribus.core.model.beans.TrpDoc;
import eu.transkribus.core.model.beans.TrpPage;
import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.core.model.beans.pagecontent.PcGtsType;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.interfaces.IBaseLine2Coords;
import eu.transkribus.interfaces.types.Image;
import eu.transkribus.interfaces.types.Image.Type;
import eu.transkribus.persistence.io.FimgStoreRwConnection;
import eu.transkribus.persistence.logic.DocManager;
import eu.transkribus.persistence.logic.TranscriptManager;

public class UroHtrTest {
	private static final Logger logger = LoggerFactory.getLogger(UroHtrTest.class);
	public static void main(String[] args) throws IOException, InvalidParameterException, EntityNotFoundException, ReflectiveOperationException, SQLException, JAXBException, AuthenticationException {
		logger.info("Loading module...");

		String s = "/home/philip/programme/gundram/module-0.0.1/modules/b2p/20160405_dft.bin";
		IBaseLine2Coords laParser = (IBaseLine2Coords) IO.load(new File(s));
		
//		System.setProperty("vendorName", "asd");
//		Object o = load(new File("/tmp/test.bin"));
//
		logger.info("Done loading module!");
//
//		HtrModule htr = (HtrModule) o;

		HTRParser hTRParser = new HTRParser();
		
		DocManager docMan = new DocManager();
		TrpDoc doc = docMan.getDocById(62);

		TranscriptManager tMan = new TranscriptManager();
		
		File workDir = new File("/tmp/test");
		File workDirIn = new File(workDir.getAbsolutePath() + File.separator + "in");
		File workDirOut = new File(workDir.getAbsolutePath() + File.separator + "out");
		workDirIn.mkdirs();
		workDirOut.mkdirs();
		
		FimgStoreGetClient getter = FimgStoreRwConnection.getGetClient();
		
		final String toolName = "URO HTR test";
		
		for(TrpPage p : doc.getPages()) {
			TrpTranscriptMetadata  tmd = p.getCurrentTranscript();
			
			if(tmd.getToolName() != null && tmd.getToolName().equals(toolName)){
				logger.info("Page was already processed..");
				continue;
			}
			
//			BufferedImage bi = ImageUtils.convertToBufferedImage(p.getUrl());
			Image i = new Image(p.getUrl());
			i.getImageBufferedImage(true);
			
			File inFile = getter.saveFile(tmd.getKey(), workDirIn.getAbsolutePath());
			
			logger.info("Running LA");
			
			File outFile = new File(workDirOut.getAbsolutePath() + File.separator + inFile.getName());
			try{
				laParser.process(i, inFile.getAbsolutePath(), outFile.getAbsolutePath());
			} catch (RuntimeException e){
				logger.error("Failed to process page "+ p.getPageNr(),e);
				continue;
			}
			PcGtsType pc = PageXmlUtils.unmarshal(outFile);
			
			logger.info("Running HTR on page: " + p.getPageNr());
			hTRParser.process("/tmp/bentham.bin", i, pc, "/tmp", null, null);
			logger.info("HTR done. Storing...");
			
			tMan.updateTranscript(p.getPageId(), null, 43, "philip", pc, toolName);
		}
	}

//	public static Object load(java.io.File f) throws IOException, ClassNotFoundException {
//		ObjectInputStream in = null;
//		Object o = null;
//		try {
//			in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(f), 32768));
//			o = in.readObject();
//		} finally {
//			if (in != null) {
//				in.close();
//			}
//		}
//		return o;
//	}
}