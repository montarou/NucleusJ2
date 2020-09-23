package gred.nucleus.plugins;
import gred.nucleus.core.ChromocentersEnhancement;
import gred.nucleus.dialogs.*;
import gred.nucleus.utils.FileList;
import ij.ImageStack;
import ij.plugin.GaussianBlur3D;
import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.measure.Calibration;

import java.io.File;
import java.util.ArrayList;
/**
 * Method to detect the chromocenters on batch
 * 
 * @author Tristan Dubos and Axel Poulet
 *
 */
public class ChromocenterSegmentationBatchPlugin_ implements PlugIn {
	/**
	 *  
	 * double xCalibration
	 */
	double xCalibration;
	double yCalibration;
	double zCalibration;
	String calibUnit;

	public void run(String arg) {
		ChromocenterSegmentationPipelineBatchDialog _chromocenterSegmentationPipelineBatchDialog = new ChromocenterSegmentationPipelineBatchDialog();
		while( _chromocenterSegmentationPipelineBatchDialog.isShowing()) {
			try {Thread.sleep(1);}
			catch (InterruptedException e) {e.printStackTrace();}
		}	
		if (_chromocenterSegmentationPipelineBatchDialog.isStart()) {
			FileList fileList = new FileList ();
			File[] tFileRawData =fileList.run(_chromocenterSegmentationPipelineBatchDialog.getRawDataDirectory());
			if (fileList.isDirectoryOrFileExist(".+RawDataNucleus.+",tFileRawData) &&
				fileList.isDirectoryOrFileExist(".+SegmentedDataNucleus.+",tFileRawData)) {
				if(_chromocenterSegmentationPipelineBatchDialog.getCalibrationStatus()) {
					this.xCalibration = _chromocenterSegmentationPipelineBatchDialog.getXCalibration();
					this.yCalibration = _chromocenterSegmentationPipelineBatchDialog.getYCalibration();
					this.zCalibration = _chromocenterSegmentationPipelineBatchDialog.getZCalibration();
					this.calibUnit = _chromocenterSegmentationPipelineBatchDialog.getUnit();
				}
				ArrayList<String> arrayListImageSegmenetedDataNucleus = fileList.fileSearchList(".+SegmentedDataNucleus.+",tFileRawData);
				String workDirectory = _chromocenterSegmentationPipelineBatchDialog.getWorkDirectory();
				for (int i = 0; i < arrayListImageSegmenetedDataNucleus.size(); ++i) {
					IJ.log("image"+(i+1)+" / "+arrayListImageSegmenetedDataNucleus.size());
					String pathImageSegmentedNucleus = arrayListImageSegmenetedDataNucleus.get(i);
					String pathNucleusRaw = pathImageSegmentedNucleus.replaceAll("SegmentedDataNucleus", "RawDataNucleus");
					IJ.log(pathNucleusRaw);
					if (fileList.isDirectoryOrFileExist(pathNucleusRaw,tFileRawData)) {
						ImagePlus imagePlusSegmented = IJ.openImage(pathImageSegmentedNucleus);
						ImagePlus imagePlusInput = IJ.openImage(pathNucleusRaw);
						GaussianBlur3D.blur(imagePlusInput,0.25,0.25,1);
						ImageStack imageStack= imagePlusInput.getStack();
						int max = 0;
						for(int k = 0; k < imagePlusInput.getStackSize(); ++k)
							for (int b = 0; b < imagePlusInput.getWidth(); ++b )
								for (int j = 0; j < imagePlusInput.getHeight(); ++j){
									if (max < imageStack.getVoxel(b, j, k)){
										max = (int) imageStack.getVoxel(b, j, k);
									}
								}
						IJ.setMinAndMax(imagePlusInput, 0, max);
						IJ.run(imagePlusInput, "Apply LUT", "stack");
						Calibration calibration = new Calibration();
						calibration.pixelDepth = zCalibration;
						calibration.pixelWidth = xCalibration;
						calibration.pixelHeight = yCalibration;
						calibration.setUnit(this.calibUnit);
						imagePlusSegmented.setCalibration(calibration);
						imagePlusInput.setCalibration(calibration);
						ChromocentersEnhancement chromocenterSegmentation = new ChromocentersEnhancement();
						ImagePlus imagePlusConstraste = chromocenterSegmentation.applyEnhanceChromocenters(imagePlusInput, imagePlusSegmented);
						imagePlusConstraste.setTitle(imagePlusInput.getTitle());
						saveFile (imagePlusConstraste,workDirectory+File.separator+"ConstrastDataNucleus");
					}
				}
				IJ.log("End of the chromocenter segmentation , the results are in "+_chromocenterSegmentationPipelineBatchDialog.getWorkDirectory());
			}
			else
			    IJ.showMessage("There are no the two subdirectories (See the directory name) or subDirectories are empty");
		}
	}
	
	/**
	 * saving file method
	 * 
	 * @param imagePlus imagePus to save 
	 * @param pathFile the path where save the image
	 */
	public void saveFile ( ImagePlus imagePlus, String pathFile) {
		FileSaver fileSaver = new FileSaver(imagePlus);
	    File file = new File(pathFile);
	    if (file.exists())
	    	fileSaver.saveAsTiffStack( pathFile+File.separator+imagePlus.getTitle());
	    else {
	    	file.mkdir();
	    	fileSaver.saveAsTiffStack( pathFile+File.separator+imagePlus.getTitle());
	    }
	}
}