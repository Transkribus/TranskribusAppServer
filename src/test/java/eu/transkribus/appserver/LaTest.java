package eu.transkribus.appserver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.FileUtils;

import eu.transkribus.interfaces.ILayoutAnalysis;
import eu.transkribus.interfaces.native_wrapper.NativeLayoutAnalysisProxy;
import eu.transkribus.interfaces.types.Image;
import eu.transkribus.interfaces.types.util.SysPathUtils;

public class LaTest {
	
	public static void testLayoutAnalysis(ILayoutAnalysis la) throws MalformedURLException {
		Image i = new Image(new URL("http://www.austriatraveldirect.com/files/INNNORD01041.jpg"));
		System.out.println(la.usage());
		System.out.println(la.getProvider());
		System.out.println(la.getToolName());
		System.out.println(la.getVersion());
		
		la.process(i, "bla1", null, null);
	}
	
	public static void main(String[] args) throws MalformedURLException{
		
		System.out.println(System.getProperty("user.dir"));
		
		
		String resourcesDir = System.getProperty("user.dir")+"/src/main/resources/";
		SysPathUtils.addDirToPath(resourcesDir);
		System.out.println("libpath = "+SysPathUtils.getPath());
		
		System.loadLibrary("TranskribusInterfacesWrapper");
//		System.load(System.getProperty("user.dir")+"/src/main/resources/libTranskribusInterfaces.so");
		
		ILayoutAnalysis la = new  NativeLayoutAnalysisProxy(
				"/home/philip/programme/NCSRTextLineSegmentation/libNCSR_TextLineSegmentation.so", new String[]{});

		testLayoutAnalysis(la);
	}
}
