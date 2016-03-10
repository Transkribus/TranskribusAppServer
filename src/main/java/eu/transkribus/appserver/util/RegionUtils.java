package eu.transkribus.appserver.util;

import java.awt.Polygon;

import eu.transkribus.core.model.beans.pagecontent_trp.ITrpShapeType;
import eu.transkribus.core.model.beans.pagecontent_trp.TrpShapeTypeUtils;
import eu.transkribus.core.util.PageXmlUtils;
import eu.transkribus.interfaces.types.Region;

public class RegionUtils extends TrpShapeTypeUtils {
	public static Region toRegion(ITrpShapeType s){
		String[] properties = {};
		//TODO set properties
		String coordsStr = s.getCoordinates();
		Polygon poly = PageXmlUtils.buildPolygon(coordsStr);
		return new Region(poly, properties);
	}
}
