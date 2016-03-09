package eu.transkribus.appserver;

import eu.transkribus.core.io.util.AConf;

public class Config extends AConf {
	static {
		props = loadProps("appserver.properties");
	}
}
