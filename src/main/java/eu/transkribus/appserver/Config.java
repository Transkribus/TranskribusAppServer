package eu.transkribus.appserver;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.job.enums.JobImpl;
import eu.transkribus.core.model.beans.job.enums.JobType;
import eu.transkribus.persistence.TrpPersistenceConf;

public class Config {
	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	protected static Properties props = new Properties();

	static {
		props = loadProps("appserver.properties");
	}

	public static String getString(String name){
		return props.getProperty(name);
	}
	
	public static Integer getInt(String name){
		final String prop = props.getProperty(name);
		Integer value = null;
		if(prop != null){
			try{
				value = Integer.parseInt(props.getProperty(name));
			} catch (NumberFormatException nfe){
				nfe.printStackTrace();
			}
		}
		return value;
	}
	
	public static Pattern getPattern(String name){
		return Pattern.compile(props.getProperty(name));
	}
	
	public static boolean getBool(String name){
		final String value = props.getProperty(name);
		boolean bool = false;
		if(value.equals("1") || value.equalsIgnoreCase("true")){
			bool = true;
		}
		return bool;
	}

	protected static Properties loadProps(String filename){
		logger.debug("Load properties file: " + filename);
		Properties props = new Properties();

		try(InputStream is = Config.class.getClassLoader().getResourceAsStream(filename)){
			props.load(is);
		} catch (Exception e) {
			logger.debug("Could not find properties file: " + filename);
		}
		
		return props;
	}

	/**
	 * TODO
	 * @param jobTypes 
	 */
	public static void checkSetup(JobType[] jobTypes) {
		//check NAS storage availability
		final String httpUploadStoragePath = TrpPersistenceConf.getString("http_upload_storage");
		if(!new File(httpUploadStoragePath).isDirectory()){
			throw new RuntimeException("Transkribus NAS storage is not available at /mnt/transkribus!");
		}
		//check system time!? -- too inaccurate
				
		//check libraries
		for(JobType j : jobTypes) {
			for(JobImpl i : JobImpl.values()) {
				if(j.equals(i.getTask().getJobType())) {
					if(i.getLibName() != null){
						final String lib = TrpPersistenceConf.getString("lib_path") + i.getLibName();
						if(!new File(lib).exists()){
							throw new RuntimeException("Library file for " + i.getLabel() + " does not exist! " + lib);
						}
					}
				}
			}
		}
	}
}
