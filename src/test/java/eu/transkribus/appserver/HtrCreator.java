 package eu.transkribus.appserver;

import com.achteck.misc.exception.InvalidParameterException;
import com.achteck.misc.log.Logger;
import com.achteck.misc.param.ParamSet;
import com.achteck.misc.types.ParamAnnotation;
import com.achteck.misc.types.ParamTreeOrganizer;
import com.achteck.misc.util.IO;
import de.uro.citlab.module.htr.HtrModule;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author gundram
 */
public class HtrCreator extends ParamTreeOrganizer {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(HtrCreator.class.getName());
    @ParamAnnotation(descr = "output of generated htr engine")
    private String o = "";

    @ParamAnnotation(descr = "class for language model", member = "htrImpl", addMemberParam = false)
    private String htr = HtrModule.class.getName();
    private HtrModule htrImpl;

    public HtrCreator() {
        addReflection(this, HtrCreator.class);
    }

    public void run() throws IOException {
        IO.save(htrImpl, new File(o));
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InvalidParameterException, IOException, ClassNotFoundException {
        args = (""
                + "-htr/dec " + de.planet.tech.langmod.LangModFullText.class.getName() + " "
//                + "-htr/dec/wd/netfilename /home/philip/programme/gundram/module-0.0.1/planet/htr/meganet_hist_01_crx.sprnn "
//                + "-htr/dict " + de.planet.tech.util.types.DictOccurrence.class.getName() + " "
//                + "-htr/dict_0/dict /home/philip/programme/gundram/module-0.0.1/extern/deutsch.dict "

                + "-htr/dec/wd/netfilename /home/philip/programme/gundram/module-0.0.1/planet/htr/20160408_htrts_midfinal_11.sprnn "
                + "-htr/dict " + de.planet.tech.util.types.DictOccurrence.class.getName() + " "
                + "-htr/dict_0/dict /home/philip/programme/gundram/module-0.0.1/extern/htrts15_all_sorted.dict "

				+ "-htr/dict_0/maxanz 10000 "
                + "-o /tmp/bentham.bin "
//                                + "--help"
                + "").split(" ");
        HtrCreator instance = new HtrCreator();
        ParamSet ps = new ParamSet();
        ps.setCommandLineArgs(args);    // allow early parsing
        ps = instance.getDefaultParamSet(ps);
        ps = ParamSet.parse(ps, args, ParamSet.ParseMode.FORCE); // be strict, don't accept generic parameter
        instance.setParamSet(ps);
        instance.init();
        instance.run();
        
    }

}