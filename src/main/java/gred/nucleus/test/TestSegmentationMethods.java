package gred.nucleus.test;

import gred.nucleus.mainsNucelusJ.SegmentationMethods;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

import java.io.IOException;

import static ij.IJ.open;

public class TestSegmentationMethods {
    /**
     *
     * @param img
     * @param vMin
     * @param vMax
     * @param outputImgString
     */

    public static void testStupid(ImagePlus img, short vMin, short vMax, String outputImgString, boolean gift ) {
        SegmentationMethods otsuModif = new SegmentationMethods(img, vMin, vMax, outputImgString);
        otsuModif.runOneImage(gift);
    }

    /**
     *
     * @param input
     * @param output
     * @param vMin
     * @param vMax
     * @param cal
     */
    public static void testStupidSeveralImages(String input, String output, short vMin, short vMax, Calibration cal, boolean gift ) {
        SegmentationMethods otsuModif = new SegmentationMethods(input, output, vMin, vMax, cal);
        try {
            String log = otsuModif.runSeveralImages(gift);
            if(!(log.equals("")))
                System.out.println("Nuclei which didn't pass the segmentation\n"+log);
        }catch (IOException e) { e.printStackTrace();}
    }

    /**
     *
     * Main function of the package's tests.
     * @param args
     */
    public static void main(String[] args) {
        //testComponentsLabeling(wrapImaJ.test.TestCoreMethods.testImages_8bits[1]);
        String imgPathAxel = "/home/plop/Bureau/image/Col_Cot24-3_005.tif";
        String imgSegPathAxel = "/home/plop/Bureau/image/";
        String inputAxel = "/home/plop/Bureau/image/test/";
        String outputAxel = "/home/plop/Bureau/image/test_res/";

        String imgPathTristan = "";
        String imgSegPathTristan = "";
        String inputTristan = "/home/plop/Bureau/image/";
        String outputTristan = "/home/plop/Bureau/image/";
        ImagePlus img  = IJ.openImage(imgPathAxel);

        //testStupid(img,(short)6.0, (short)40.0,imgSegPathAxel,true);

        Calibration cal = new Calibration();
        cal.pixelDepth = 0.2;
        cal.pixelHeight = 0.103;
        cal.pixelWidth = 0.103;
        testStupidSeveralImages(inputAxel, outputAxel, (short)6.0, (short)300.0,cal,true);

        System.err.println("The program ended normally.");
    }
}
