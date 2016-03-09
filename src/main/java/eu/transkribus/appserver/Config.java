package eu.transkribus.appserver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.io.util.AConf;

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
//		if(props == null){
		props = new Properties();
//		}
		InputStream is = null;
		try{
			is = AConf.class.getClassLoader().getResourceAsStream(filename);
			props.load(is);
		} catch (Exception e) {
			logger.debug("Could not find properties file: " + filename);
		}
		finally{
			try {
				is.close();
			} catch (IOException e) {
				logger.warn("Could not close resource stream");
				//ignore
			}
		}
		return props;
	}
}
