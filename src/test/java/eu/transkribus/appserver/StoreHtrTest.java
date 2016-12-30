package eu.transkribus.appserver;

import java.sql.SQLException;

import eu.transkribus.core.model.beans.TrpHtr;
import eu.transkribus.persistence.logic.HtrManager;

public class StoreHtrTest {
	public static void main(String[] args) throws SQLException, ReflectiveOperationException {
		
		HtrManager htrMan = new HtrManager();
		
		TrpHtr htr = new TrpHtr();
        htr.setHtrId(5);
        htr.setCreated(new java.sql.Timestamp(System.currentTimeMillis()));
        htr.setGtDocId(10650);
        htr.setName("Konzilsprotokolle v1");
        htr.setPath("/mnt/transkribus/HTR/DEA/URO/5");
        htr.setProvider(HtrManager.PROVIDER_CITLAB);
        htr.setDescription("clear, professional writing in Kurrent");
        htr.setBaseHtrId(null);
        htr.setTrainJobId("36411");
        htr.setLanguage("German");
        htr.setTestGtDocId(null);
        
        htrMan.storeHtr(940, htr);
		
	}
}
