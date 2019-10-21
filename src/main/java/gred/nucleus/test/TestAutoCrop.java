package gred.nucleus.test;

import gred.nucleus.autocrop.AutocropParameters;
import gred.nucleus.exceptions.fileInOut;
import gred.nucleus.autocrop.AutoCropCalling;

import loci.formats.FormatException;


import java.io.IOException;
import java.util.ArrayList;


/**
 * Class dedicated to examples and test of methods in the package.
 *
 * @author Remy Malgouyres, Tristan Dubos and Axel Poulet
 */
public class TestAutoCrop {

    /**
     * Test for labeling connected components of a binarized image.
     * Only connected components with no voxel on the image's boundary
     * are kept in the filtering process.
     * <p>
     * Connected components with a volume below some threshold are
     * also removed.
     * <p>
     * a constant random gray level is set on each connected component.
     *
     * @param imageSourceFile the input image file on disk
     */

    static ArrayList<String> m_test;

    public static void testStupid(String imageSourceFile, String output) throws IOException, FormatException, Exception {
        AutocropParameters autocropParameters = new AutocropParameters(imageSourceFile, output);
        AutoCropCalling autoCrop = new AutoCropCalling();
        autoCrop.run();
    }

    public static void runAutoCrop(String imageSourceFile, String output) throws IOException, FormatException, fileInOut, Exception {
        //AutocropParameters autocropParameters= new AutocropParameters(imageSourceFile,output);
        AutocropParameters autocropParameters = new AutocropParameters(imageSourceFile, output, 40, 40, 20, 40, 30, 0, 1, 1000000000);
        AutoCropCalling autoCrop = new AutoCropCalling(autocropParameters);
        autoCrop.run();
    }
    public static void runAutoCropConfigFile(String imageSourceFile, String output,String configFile) throws IOException, FormatException, fileInOut, Exception {
        //AutocropParameters autocropParameters= new AutocropParameters(imageSourceFile,output);
        AutocropParameters autocropParameters = new AutocropParameters(imageSourceFile, output,configFile);
        AutoCropCalling autoCrop = new AutoCropCalling(autocropParameters);
        autoCrop.run();
    }

    /**
     * Main function of the package's tests.
     *
     * @param args
     */
    public static void main(String[] args) throws IOException, FormatException, Exception {

        System.err.println("start prog");
        long maxMemory = Runtime.getRuntime().maxMemory();
        /* Maximum amount of memory the JVM will attempt to use */
        System.out.println("Maximum memory (bytes): " +
                (maxMemory == Long.MAX_VALUE ? "no limit" : maxMemory * 1e-9));
        String inputOneImageAxel = "/home/plop/Bureau/image/wideField/Z_c1c4_cot11&12&13-_w11 DAPI SIM variable_s4.TIF";
        String inputDirAxel = "/home/plop/Bureau/image/wideField/";
        String outputAxel = "/home/plop/Bureau/image/wideField/test";

        // TODO AJOUTER WARNING QUAND LE THRESHOLD EST TROP BAS (VERIFIER LES NOYAUX )
        //String inputOneImageTristan = "/home/tridubos/Bureau/AUTOCROP_TEST/raw/Z_c1c4_cot11&12&13-_w11 DAPI SIM variable_s9.TIF";
        //String inputDirTristan = "/home/tridubos/Bureau/Demo_Autocrop/Out/";

        // String outputTristan = "/home/titus/Bureau/data/test_autocrop/";

        String ExpectedResult = "/home/tridubos/Bureau/TEST_AUTOCROP/Results_checked";
        String inputOneImageTristan = "/home/tridubos/Bureau/TEST_AUTOCROP/Test_Version";

        String outputTristan = "/home/tridubos/Bureau/TEST_AUTOCROP/out_test_Version";

        //OuputFileVerification fw = new OuputFileVerification();
        //fw.GetFileResultExpeted(ExpectedResult);
        //fw.GetFilesOutputFolder(outputTristan);
        //testStupid(inputOneImageTristan, outputTristan);
        runAutoCropConfigFile("/media/tridubos/DATA1/Raw_KAKU_GIFT/Raw_Croped", "/media/tridubos/DATA1/Raw_KAKU_GIFT/New_autocrop","/media/tridubos/DATA1/Raw_KAKU_GIFT/config_file_test");
        //fw.GetFilesResultingOfAnalysis(outputTristan);
        //fw.CompareAnalysisResult();
		/*
		String inputOneImageTristan = "/home/tridubos/Bureau/TEST_READING_METADATA/";
		ImporterOptions options = new ImporterOptions();
		options.setId(inputOneImageTristan);
		options.setAutoscale(true);
		options.setCrop(true);
		options.setCropRegion(0, new Region(150, 150 ,50, 50));
		options.setColorMode(ImporterOptions.COLOR_MODE_COMPOSITE);
		ImagePlus[] imps = BF.openImagePlus(options);
		ImagePlus sort = new ImagePlus();
		sort = new Duplicator().run(imps[0],1,10);

		saveFile(sort, "/home/tridubos/Bureau/TEST_READING_METADATA/cetruc.tif");
		*/
        //testStupid(inputOneImageTristan, outputTristan);
        System.err.println("The program ended normally.");

        System.out.println("Total memory (bytes): " +
                Runtime.getRuntime().totalMemory() * 1e-9);
    }


}
