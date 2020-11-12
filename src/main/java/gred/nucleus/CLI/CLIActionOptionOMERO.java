package gred.nucleus.CLI;

import ome.model.units.Conversion;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import java.util.ArrayList;
import static org.apache.commons.lang.Validate.isTrue;


/**
 * Herited class to handle OMERO command line option
 */
public class CLIActionOptionOMERO extends CLIActionOptions {
    /**
     * Host name server
     */
    Option m_hostname = Option.builder("ho")
            .longOpt("hostname")
            .required()
            .type(String.class)
            .desc("Hostname of the OMERO serveur")
            .numberOfArgs(1)
            .build();
    /**
     * Server port connexion
     */
    Option m_port = Option.builder("pt")
            .longOpt("port")
            .required()
            .type(Conversion.Int.class)
            .desc("Port used by OMERO")
            .numberOfArgs(1)
            .build();
    /**
     * username connexion
     */
    Option m_username = Option.builder("u")
            .longOpt("username")
            .type(String.class)
            .desc("Username in OMERO")
            .numberOfArgs(1)
            .build();
    /**
     * OMERO password connexion
     */
    Option m_password = Option.builder("p")
            .longOpt("password")
            .type(String.class)
            .desc("Password in OMERO")
            .numberOfArgs(1)
            .build();
    /**
     * Group user connexion
     */
    Option m_group = Option.builder("g")
            .longOpt("group")
            .required()
            .type(String.class)
            .desc("Group in OMERO")
            .numberOfArgs(1)
            .build();
    /**
     * OMERO activate
     */
    Option m_omero = Option.builder("ome")
            .longOpt("omero")
            .required()
            .type(boolean.class)
            .desc("Use of NucleuJ2.0 in OMERO\n")
            .numberOfArgs(0)
            .build();

    /**
     * Constructor
      * @throws Exception
     */
    public CLIActionOptionOMERO() throws Exception {

    }
    /**
     Constructor with argument
     * @param argument : list of command line argument
     * @throws Exception
     */
    public CLIActionOptionOMERO(String[] argument) throws Exception {
        super(argument);
        this.m_options.addOption(this.m_action);
        this.m_options.addOption(this.m_omero);
        this.m_options.addOption(this.m_port);
        this.m_options.addOption(this.m_hostname);
        this.m_options.addOption(this.m_username);
        this.m_options.addOption(this.m_password);
        this.m_options.addOption(this.m_group);
        this.m_imputFolder.setDescription(this.m_imputFolder.getDescription()+
                "OMERO  inputs 2 information separated with slash separator :  " +
                "Type input: dataset, project, image, tag " +
                "Input id number" + "\n" +
                "Exemple : "+ "\n" +
                "          dataset/1622");
        try {
            this.m_cmd = this.m_parser.parse(this.m_options, argument);
            isTrue(OMEROAvailableAction(this.m_cmd.getOptionValue("action")));

        } catch (ParseException exp) {
            System.out.println(exp.getMessage() + "\n");
            System.out.println(getHelperInfos());
            System.exit(1);
        }
        catch (Exception exp) {
            System.out.println("Action option \""+
                    this.m_cmd.getOptionValue("action")+
                    "\" not available"+ "\n");
            System.out.println(getHelperInfos());
            System.exit(1);
        }


    }

    /**
     * Method to get command line info
     * @return header info
     */
    public String getHelperInfos() {
        return "More details :\n" +
                "java -jar NucleusJ_2-"+NJversion+".jar -hOme \n" +
                "or \n"+
                "java -jar NucleusJ_2-"+NJversion+".jar -helpOmero \n";
    }

    /**
     * Method to check action parameter
     * @param action nucleusJ2.0 action to run
     * @return boolean existing action
     */
    public static boolean  OMEROAvailableAction(String action) {
        ArrayList<String > actionAvailableInOmero= new ArrayList<>();
        actionAvailableInOmero.add("autocrop");
        actionAvailableInOmero.add("segmentation");
        return actionAvailableInOmero.contains(action);
    }

}

//        if(cmd.getOptionValue("action").equals("autocrop")) {
/*
ArrayList<String> actionList = new ArrayList<>();
        actionList.add("autocrop");
        actionList.add("segmentation");
        actionList.add("computeParameters");
        actionList.add("computeParametersDL");
        actionList.add("generateProjection");
        actionList.add("CropFromCoordinate");
        actionList.add("GenerateOverlay");

 */