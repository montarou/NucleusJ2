package gred.nucleus.core;

import fr.igred.omero.Client;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.roi.GenericShapeWrapper;
import fr.igred.omero.roi.ROIWrapper;
import fr.igred.omero.roi.RectangleWrapper;
import fr.igred.omero.annotations.TagAnnotationWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import gred.nucleus.files.Directory;
import gred.nucleus.imageprocessing.Thresholding;
import gred.nucleus.segmentation.SegmentationParameters;
import gred.nucleus.utils.FillingHoles;
import gred.nucleus.utils.Gradient;
import gred.nucleus.utils.Histogram;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.plugin.Filters3D;
import ij.plugin.GaussianBlur3D;
import ij.process.StackConverter;
import ij.process.StackStatistics;
import inra.ijpb.binary.BinaryImages;
import loci.common.DebugTools;
import loci.plugins.BF;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * Object segmentation method for 3D images. This segmentation used as initial threshold the method of Otsu, and then
 * select the object maximizing the sphericity of the segmented object.
 *
 * @author Tristan Dubos and Axel Poulet
 */
public class NucleusSegmentation {
	
	/** Segmentation parameters for the analyse */
	private final SegmentationParameters segmentationParameters;
	/** List of the 3D parameters computed associated to the segmented image */
	public        Measure3D              measure3D;
	/** Threshold detected by the Otsu modified method */
	private       int                    bestThreshold = -1;
	/* String stocking the file name if any nucleus is detected*/
	/** volume min of the detected object */
	private       int                    vMin;
	/** volume max of the detected object */
	private       int                    vMax;
	/** ImagePlus input to process */
	private       ImagePlus              imgRaw;
	/** ImagePlus input to process */
	private final ImagePlus              imgRawTransformed;
	/** Check if the segmentation is not in border */
	private       boolean                badCrop       = false;
	/** Current image analysed */
	private       File                   currentFile;
	/** String containing the output prefix */
	private       String                 outputFilesPrefix;
	/** Segmented image */
	private       ImagePlus[]            imageSeg;
	
	
	/**
	 * Constructor for the segmentation analyse for a single image.
	 *
	 * @param imgRaw                 raw image to analyse
	 * @param vMin                   minimum volume of detected object
	 * @param vMax                   maximum volume of detected object
	 * @param segmentationParameters list the parameters for the analyse
	 *
	 * @throws Exception
	 */
	public NucleusSegmentation(ImagePlus imgRaw,
	                           int vMin,
	                           int vMax,
	                           SegmentationParameters segmentationParameters)
	throws Exception {
		this.vMin = vMin;
		this.vMax = vMax;
		this.segmentationParameters = segmentationParameters;
		this.imgRaw = imgRaw;
		this.imgRaw = getImageChannel(0);
		this.imgRawTransformed = this.imgRaw;
	}
	
	
	/**
	 * Constructor for the segmentation analyse for a folder containing images.
	 *
	 * @param imageFile              Current image analysed
	 * @param outputFilesPrefix      prefix for the output file
	 * @param segmentationParameters list the parameters for the analyse
	 *
	 * @throws Exception
	 */
	public NucleusSegmentation(File imageFile,
	                           String outputFilesPrefix,
	                           SegmentationParameters segmentationParameters)
	throws Exception {
		this.segmentationParameters = segmentationParameters;
		this.currentFile = imageFile;
		this.imgRaw = getImageChannel(0);
		// TODO ADD CHANNEL PARAMETERS (CASE OF CHANNELS UNSPLITED)
		this.imgRaw.setTitle(imageFile.getName());
		this.imgRawTransformed = this.imgRaw.duplicate();
		this.imgRawTransformed.setTitle(imageFile.getName());
		Directory dirOutputOTSU = new Directory(this.segmentationParameters.getOutputFolder() + "OTSU");
		dirOutputOTSU.checkAndCreateDir();
		if (this.segmentationParameters.getGiftWrapping()) {
			Directory dirOutputGIFT = new Directory(this.segmentationParameters.getOutputFolder() + "GIFT");
			dirOutputGIFT.checkAndCreateDir();
		}
	}
	
	
	public NucleusSegmentation(ImageWrapper image, SegmentationParameters segmentationParameters, Client client)
	throws Exception {
		this.segmentationParameters = segmentationParameters;
		
		int[] cBound = {0, 0};
		
		this.imgRaw = image.toImagePlus(client, null, null, cBound, null, null);
		// TODO ADD CHANNEL PARAMETERS (CASE OF CHANNELS UNSPLITED)
		this.imgRaw.setTitle(image.getName());
		this.imgRawTransformed = this.imgRaw.duplicate();
		this.imgRawTransformed.setTitle(image.getName());
	}
	
	
	public NucleusSegmentation(ImageWrapper image,
	                           ROIWrapper roi,
	                           int i,
	                           SegmentationParameters segmentationParameters,
	                           Client client) throws Exception {
		this.segmentationParameters = segmentationParameters;
		
		List<RectangleWrapper> rectangles = roi.getShapes().getElementsOf(RectangleWrapper.class);
		
		RectangleWrapper rectangle = rectangles.get(0);
		
		int roiThickness = rectangles.size();
		int channel      = rectangle.getC();
		int slice        = rectangle.getZ();
		
		double[] coordinates = rectangle.getCoordinates();
		int      x           = (int) coordinates[0];
		int      y           = (int) coordinates[1];
		int      width       = (int) coordinates[2];
		int      height      = (int) coordinates[3];
		
		int[] cBound = {channel, channel};
		int[] zBound = {slice, slice + roiThickness - 1};
		int[] xBound = {x, x + width - 1};
		int[] yBound = {y, y + height - 1};
		
		this.imgRaw = image.toImagePlus(client, xBound, yBound, cBound, zBound, null);
		
		this.imgRaw.setTitle(image.getName() + "_" + i + "_C" + rectangle.getC());
		this.imgRawTransformed = this.imgRaw.duplicate();
		this.imgRawTransformed.setTitle(this.imgRaw.getTitle());
	}
	
	
	/**
	 * Method to set a specific channel image
	 *
	 * @param channelNumber channel number of the current image to analyse
	 *
	 * @return channel image
	 *
	 * @throws Exception
	 */
	public ImagePlus getImageChannel(int channelNumber) throws Exception {
		DebugTools.enableLogging("OFF");       // DEBUG INFO BIO-FORMATS OFF
		ImagePlus[] currentImage = BF.openImagePlus(this.currentFile.getAbsolutePath());
		currentImage = ChannelSplitter.split(currentImage[channelNumber]);
		return currentImage[0];
	}
	
	
	/**
	 * Method to save 3D parameters computed
	 *
	 * @return list of 3D parameter computed
	 */
	public String saveImageResult() {
		return this.measure3D.nucleusParameter3D();
	}
	
	
	/**
	 * Method to save 3D parameters computed
	 *
	 * @param segmentedImage segmented image
	 *
	 * @return
	 */
	public String saveImageResult(ImagePlus[] segmentedImage) {
		
		this.measure3D =
				new Measure3D(segmentedImage, this.imgRaw, getXCalibration(), getYCalibration(), getZCalibration());
		return this.measure3D.nucleusParameter3D();
		
		
	}
	
	
	/**
	 * Method to save segmented image
	 *
	 * @param imagePlusInput segmented image
	 * @param pathFile       path to save the image
	 */
	private void saveFile(ImagePlus imagePlusInput, String pathFile) {
		FileSaver fileSaver = new FileSaver(imagePlusInput);
		fileSaver.saveAsTiffStack(pathFile);
	}
	
	
	/**
	 * Compute of the first threshold of input image with the method of Otsu. From this initial value we will seek the
	 * better segmentation possible: for this we will take the voxels value superior at the threshold value of method of
	 * Otsu : Then we compute the standard deviation of this values voxel > threshold value determines which allows
	 * range of value we will search the better threshold value : thresholdOtsu - stdDev and thresholdOtsu + stdDev. For
	 * each threshold test; we do an opening and a closing, then run the holesFilling methods. To finish we compute the
	 * sphericity.
	 * <p>
	 * The aim of this method is to maximize the sphericity to obtain the segmented object nearest of the biological
	 * object.
	 * <p>
	 * //TODO methode a reecrire y a moyen de faire plus propre mais pas urgent
	 *
	 * @return ImagePlus Segmented image
	 */
	public void findOTSUMaximisingSphericity() {
		
		double imageVolume = getVoxelVolume() * this.imgRaw.getWidth() *
		                     this.imgRaw.getHeight() * this.imgRaw.getStackSize();
		Gradient gradient       = new Gradient(this.imgRaw);
		double   bestSphericity = -1;
		ArrayList<Integer> arrayListThreshold = computeMinMaxThreshold(
				this.imgRawTransformed);  // methode OTSU
		for (int t = arrayListThreshold.get(0); t <= arrayListThreshold.get(1); ++t) {
			ImagePlus tempSeg;
			tempSeg = generateSegmentedImage(this.imgRawTransformed, t);
			
			tempSeg = BinaryImages.componentsLabeling(tempSeg, 26, 32);
			Calibration cal = this.imgRaw.getCalibration();
			if (this.segmentationParameters.getManualParameter()) {
				//TODO ADD UNITS
				cal.setXUnit("µm");
				cal.pixelWidth = this.segmentationParameters.getXCal();
				cal.setYUnit("µm");
				cal.pixelHeight = this.segmentationParameters.getYCal();
				cal.setZUnit("µm");
				cal.pixelDepth = this.segmentationParameters.getZCal();
			} else {
				cal.setXUnit("µm");
				cal.pixelWidth = this.imgRaw.getCalibration().pixelWidth;
				cal.setYUnit("µm");
				cal.pixelHeight = this.imgRaw.getCalibration().pixelHeight;
				cal.setZUnit("µm");
				cal.pixelDepth = this.imgRaw.getCalibration().pixelDepth;
			}
			tempSeg.setCalibration(cal);
			ImagePlus[] tempSegPlus = new ImagePlus[1];
			tempSegPlus[0] = tempSeg;
			Measure3D measure3D = new Measure3D(tempSegPlus,
			                                    this.imgRawTransformed,
			                                    getXCalibration(),
			                                    getYCalibration(),
			                                    getZCalibration());
			deleteArtefact(tempSeg);
			double  volume     = measure3D.computeVolumeObject2(255);
			boolean firstStack = isVoxelThresholded(tempSeg, 255, 0);
			boolean lastStack  = isVoxelThresholded(tempSeg, 255, tempSeg.getStackSize() - 1);
			if (testRelativeObjectVolume(volume, imageVolume) &&
			    volume >= this.segmentationParameters.getMinVolumeNucleus() &&
			    volume <= this.segmentationParameters.getMaxVolumeNucleus() &&
			    !firstStack && !lastStack) {
				
				double sphericity =
						measure3D.computeSphericity(volume, measure3D.computeComplexSurface(tempSeg, gradient));
				if (sphericity > bestSphericity) {
					this.bestThreshold = t;
					bestSphericity = sphericity;
					this.bestThreshold = t;
					this.imageSeg = tempSegPlus;
					this.imageSeg[0].setTitle(this.imgRawTransformed.getTitle());
				}
			}
		}
		
		if (this.bestThreshold != -1) {
			morphologicalCorrection(this.imageSeg[0]);
			checkBorder(this.imageSeg[0]);
		}
	}
	
	
	/**
	 * Pre process ot the raw image : - Gaussian blur - LUT application
	 * <p> TODO object function image transformation
	 */
	public void preProcessImage() {
		
		GaussianBlur3D.blur(this.imgRawTransformed, 0.1, 0.1, 1);
		ImageStack imageStack = this.imgRawTransformed.getStack();
		int        max        = 0;
		for (int k = 0; k < this.imgRawTransformed.getStackSize(); ++k) {
			for (int b = 0; b < this.imgRawTransformed.getWidth(); ++b) {
				for (int j = 0; j < this.imgRawTransformed.getHeight(); ++j) {
					if (max < imageStack.getVoxel(b, j, k)) {
						max = (int) imageStack.getVoxel(b, j, k);
					}
				}
			}
		}
		IJ.setMinAndMax(this.imgRawTransformed, 0, max);
		IJ.run(this.imgRawTransformed, "Apply LUT", "stack");
		if (this.imgRaw.getType() == ImagePlus.GRAY16) {
			StackConverter stackConverter = new StackConverter(this.imgRawTransformed);
			stackConverter.convertToGray8();
		}
	}
	
	
	/**
	 * @param imagePlusInput
	 *
	 * @return
	 *
	 * @deprecated Method to apply the segmentation to find the maximum sphericity.
	 */
	public ImagePlus applySegmentation(ImagePlus imagePlusInput) {
		double       sphericityMax = -1.0;
		double       sphericity;
		double       volume;
		Calibration  calibration   = imagePlusInput.getCalibration();
		final double xCalibration  = getXCalibration();
		final double yCalibration  = getYCalibration();
		final double zCalibration  = getZCalibration();
		Measure3D    measure3D     = new Measure3D();
		Gradient     gradient      = new Gradient(imagePlusInput);
		final double imageVolume = xCalibration * imagePlusInput.getWidth() *
		                           yCalibration * imagePlusInput.getHeight() *
		                           zCalibration * imagePlusInput.getStackSize();
		ImagePlus          imagePlusSegmented = new ImagePlus();
		ArrayList<Integer> arrayListThreshold = computeMinMaxThreshold(imagePlusInput);
		for (int t = arrayListThreshold.get(0); t <= arrayListThreshold.get(1); ++t) {
			ImagePlus imagePlusSegmentedTemp = generateSegmentedImage(imagePlusInput, t);
			imagePlusSegmentedTemp = BinaryImages.componentsLabeling(imagePlusSegmentedTemp, 26, 32);
			deleteArtefact(imagePlusSegmentedTemp);
			imagePlusSegmentedTemp.setCalibration(calibration);
			volume = measure3D.computeVolumeObject(imagePlusSegmentedTemp, 255);
			imagePlusSegmentedTemp.setCalibration(calibration);
			boolean firstStack = isVoxelThresholded(imagePlusSegmentedTemp, 255, 0);
			boolean lastStack  = isVoxelThresholded(imagePlusSegmentedTemp, 255, imagePlusInput.getStackSize() - 1);
			//boolean xyBorder;
			if (testRelativeObjectVolume(volume, imageVolume) &&
			    volume >= vMin &&
			    volume <= vMax && !firstStack
			    && !lastStack) {
				sphericity = measure3D.computeSphericity(volume,
				                                         measure3D.computeComplexSurface(imagePlusSegmentedTemp,
				                                                                         gradient));
				if (sphericity > sphericityMax) {
					bestThreshold = t;
					sphericityMax = sphericity;
					StackConverter stackConverter = new StackConverter(imagePlusSegmentedTemp);
					stackConverter.convertToGray8();
					imagePlusSegmented = imagePlusSegmentedTemp.duplicate();
				}
			}
		}
		
		ImageStack imageStackInput = imagePlusSegmented.getImageStack();
		
		if (bestThreshold != -1) morphologicalCorrection(imagePlusSegmented);
		
		checkBorder(imagePlusSegmented);
		
		return imagePlusSegmented;
	}
	
	
	/**
	 * Creation of the nucleus segmented image from a OTSU threshold.
	 *
	 * @param imagePlusInput raw image
	 * @param threshold      threshold value for the segmentation
	 *
	 * @return segmented image of the nucleus
	 */
	public ImagePlus generateSegmentedImage(ImagePlus imagePlusInput, int threshold) {
		ImageStack imageStackInput    = imagePlusInput.getStack();
		ImagePlus  imagePlusSegmented = imagePlusInput.duplicate();
		imagePlusSegmented.setTitle(imagePlusInput.getTitle());
		ImageStack imageStackSegmented = imagePlusSegmented.getStack();
		for (int k = 0; k < imagePlusInput.getStackSize(); ++k) {
			for (int i = 0; i < imagePlusInput.getWidth(); ++i) {
				for (int j = 0; j < imagePlusInput.getHeight(); ++j) {
					double voxelValue = imageStackInput.getVoxel(i, j, k);
					if (voxelValue >= threshold) {
						imageStackSegmented.setVoxel(i, j, k, 255);
					} else {
						imageStackSegmented.setVoxel(i, j, k, 0);
					}
				}
			}
		}
		
		return imagePlusSegmented;
	}
	
	
	/**
	 * Method to check if the final segmented image got pixel on border of the image (filter of partial nucleus).
	 *
	 * @param imagePlusInput Segmented image with the OTSU modified threshold
	 */
	private void checkBorder(ImagePlus imagePlusInput) {
		ImageStack imageStackInput = imagePlusInput.getStack();
		for (int k = 0; k < imagePlusInput.getStackSize(); ++k) {
			if ((k == 0) || k == imagePlusInput.getStackSize() - 1) {
				for (int i = 0; i < imagePlusInput.getWidth(); i++) {
					for (int j = 0; j < imagePlusInput.getHeight(); j++) {
						if (imageStackInput.getVoxel(i, j, k) == 255.0) {
							this.badCrop = true;
						}
					}
				}
			}
			
			for (int i = 0; i < imagePlusInput.getWidth();
			     i += (imagePlusInput.getWidth()) - 1) {
				for (int j = 0; j < imagePlusInput.getHeight(); j++) {
					if (imageStackInput.getVoxel(i, j, k) == 255.0) {
						this.badCrop = true;
					}
				}
			}
			for (int j = 0; j < imagePlusInput.getHeight();
			     j += (imagePlusInput.getHeight() - 1)) {
				
				for (int i = 0; i < imagePlusInput.getWidth(); i++) {
					if (imageStackInput.getVoxel(i, j, k) == 255.0) {
						this.badCrop = true;
					}
				}
			}
		}
	}
	
	
	/**
	 * Method to check if the nucleus is truncated (last slice or border).
	 *
	 * @return True if the nucleus is partial
	 */
	public boolean isBadCrop() {
		return this.badCrop;
	}
	
	
	/**
	 * Determine of the minimum and the maximum value o find the better threshold value.
	 *
	 * @param imagePlusInput raw image
	 *
	 * @return array lis which contain at the index 0 the min valu and index 1 the max value
	 */
	private ArrayList<Integer> computeMinMaxThreshold(ImagePlus imagePlusInput) {
		ArrayList<Integer> arrayListMinMaxThreshold = new ArrayList<>();
		Thresholding       thresholding             = new Thresholding();
		int                threshold                = thresholding.computeOtsuThreshold(imagePlusInput);
		StackStatistics    stackStatistics          = new StackStatistics(imagePlusInput);
		double             stdDev                   = stackStatistics.stdDev;
		double             min                      = threshold - stdDev * 2;
		double             max                      = threshold + stdDev / 2;
		if (min < 0) {
			arrayListMinMaxThreshold.add(6);
		} else {
			arrayListMinMaxThreshold.add((int) min);
		}
		arrayListMinMaxThreshold.add((int) max);
		return arrayListMinMaxThreshold;
	}
	
	
	/**
	 * Determines the number of pixel on the stack index in input return true if the number of pixel>=10.
	 *
	 * @param imagePlusSegmented ImagePlus segmented image
	 * @param threshold          int number of pixel
	 * @param stackIndex         index of the slice of interest
	 *
	 * @return boolean true if the nb of pixel is > to threshold else false
	 */
	private boolean isVoxelThresholded(ImagePlus imagePlusSegmented,
	                                   int threshold,
	                                   int stackIndex) {
		boolean    voxelThresolded     = false;
		int        nbVoxelThresholded  = 0;
		ImageStack imageStackSegmented = imagePlusSegmented.getStack();
		for (int i = 0; i < imagePlusSegmented.getWidth(); ++i) {
			for (int j = 0; j < imagePlusSegmented.getHeight(); ++j) {
				if (imageStackSegmented.getVoxel(i, j, stackIndex) >= threshold) {
					nbVoxelThresholded++;
				}
			}
		}
		if (nbVoxelThresholded >= 10) {
			voxelThresolded = true;
		}
		return voxelThresolded;
	}
	
	
	/**
	 * method to realise morphological correction (filling holes and top hat)
	 *
	 * @param imagePlusSegmented image to be correct
	 */
	private void morphologicalCorrection(ImagePlus imagePlusSegmented) {
		FillingHoles holesFilling = new FillingHoles();
		int          temps        = imagePlusSegmented.getBitDepth();
		computeOpening(imagePlusSegmented);
		computeClosing(imagePlusSegmented);
		// TODO FIX?
		imagePlusSegmented = holesFilling.apply2D(imagePlusSegmented);
	}
	
	
	/**
	 * Compute closing with the segmented image.
	 *
	 * @param imagePlusInput image segmented
	 */
	private void computeClosing(ImagePlus imagePlusInput) {
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		imageStackInput = Filters3D.filter(imageStackInput,
		                                   Filters3D.MAX,
		                                   1,
		                                   1,
		                                   (float) 0.5);
		imageStackInput = Filters3D.filter(imageStackInput,
		                                   Filters3D.MIN,
		                                   1,
		                                   1,
		                                   (float) 0.5);
		imagePlusInput.setStack(imageStackInput);
	}
	
	
	/**
	 * Compute opening with the segmented image
	 *
	 * @param imagePlusInput image segmented
	 */
	private void computeOpening(ImagePlus imagePlusInput) {
		ImageStack imageStackInput = imagePlusInput.getImageStack();
		
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MIN, 1, 1, 0.5f);
		imageStackInput = Filters3D.filter(imageStackInput, Filters3D.MAX, 1, 1, 0.5f);
		
		imagePlusInput.setStack(imageStackInput);
	}
	
	
	/**
	 * getter: return the threshold value computed
	 *
	 * @return the final threshold value
	 */
	public int getBestThreshold() {
		return bestThreshold;
	}
	
	
	/**
	 * Method to detected if the object is superior or equal at 70% of the image return false.
	 *
	 * @param objectVolume double volume of the object
	 *
	 * @return boolean if ratio object/image > 70% return false else return true
	 */
	private boolean testRelativeObjectVolume(double objectVolume, double imageVolume) {
		final double ratio = (objectVolume / imageVolume) * 100;
		return !(ratio >= 70);
	}
	
	
	/**
	 * Keep the bigger object in the image at 255 put the other at 0.
	 *
	 * @param imgSeg ImagePlus of the segmented image
	 */
	private void deleteArtefact(ImagePlus imgSeg) {
		double     voxelValue;
		double     mode            = getLabelOfLargestObject(imgSeg);
		ImageStack imageStackInput = imgSeg.getStack();
		for (int k = 0; k < imgSeg.getNSlices(); ++k) {
			for (int i = 0; i < imgSeg.getWidth(); ++i) {
				for (int j = 0; j < imgSeg.getHeight(); ++j) {
					voxelValue = imageStackInput.getVoxel(i, j, k);
					if (voxelValue == mode) {
						imageStackInput.setVoxel(i, j, k, 255);
					} else {
						imageStackInput.setVoxel(i, j, k, 0);
					}
				}
			}
		}
	}
	
	
	/**
	 * Detection of the label of the biggest object segmented in the image
	 *
	 * @param imgSeg ImagePlus segmented img
	 *
	 * @return double the label of the bigger object
	 */
	private double getLabelOfLargestObject(ImagePlus imgSeg) {
		Histogram histogram = new Histogram();
		histogram.run(imgSeg);
		double labelMax   = 0;
		double nbVoxelMax = -1;
		for (Entry<Double, Integer> entry : histogram.getHistogram().entrySet()) {
			double label   = entry.getKey();
			int    nbVoxel = entry.getValue();
			if (nbVoxel > nbVoxelMax) {
				nbVoxelMax = nbVoxel;
				labelMax = label;
			}
		}
		return labelMax;
	}
	
	
	/**
	 * Method to get X calibration if it's present in parameters of analyse or get the metadata of the image.
	 * <p> TODO verifier cette methode si elle est au bon endroit
	 *
	 * @return X calibration
	 */
	public double getXCalibration() {
		double xCal;
		if (this.segmentationParameters.manualParameter) {
			xCal = this.segmentationParameters.getXCal();
		} else {
			xCal = this.imgRawTransformed.getCalibration().pixelWidth;
		}
		return xCal;
	}
	
	
	/**
	 * Method to get Y calibration if it's present in parameters of analyse or get the metadata of the image.
	 * <p> TODO verifier cette methode si elle est au bonne endroit
	 *
	 * @return Y calibration
	 */
	public double getYCalibration() {
		double yCal;
		if (this.segmentationParameters.manualParameter) {
			yCal = this.segmentationParameters.getYCal();
		} else {
			yCal = this.imgRawTransformed.getCalibration().pixelHeight;
		}
		return yCal;
	}
	
	
	/**
	 * Method to get Y calibration if it's present in parameters of analyse or get the metadata of the image.
	 * <p> TODO verifier cette methode si elle est à sa place
	 *
	 * @return Z calibration
	 */
	public double getZCalibration() {
		double zCal;
		if (this.segmentationParameters.getManualParameter()) {
			zCal = this.segmentationParameters.getZCal();
		} else {
			zCal = this.imgRawTransformed.getCalibration().pixelDepth;
		}
		return zCal;
	}
	
	
	/**
	 * Method to compute the voxel volume : if it's present in parameters of analyse or get the metadata of the image.
	 * TODO verifier cette methode si elle est à sa place
	 *
	 * @return Z calibration
	 */
	public double getVoxelVolume() {
		double calibration;
		if (this.segmentationParameters.manualParameter) {
			calibration = segmentationParameters.getVoxelVolume();
		} else {
			Calibration cal = this.imgRawTransformed.getCalibration();
			calibration = cal.pixelDepth * cal.pixelWidth * cal.pixelHeight;
		}
		
		return calibration;
	}
	
	
	/**
	 * Method to move bad crop (truncated nucleus) to badcrop folder.
	 * <p> TODO verifier cette methode si elle est à sa place
	 *
	 * @param inputPathDir folder of the input to create badcrop folder.
	 */
	public void checkBadCrop(String inputPathDir) {
		if ((this.badCrop) || (this.bestThreshold == -1)) {
			File file          = new File(inputPathDir);
			File BadCropFolder = new File(inputPathDir + File.separator + "BadCrop");
			System.out.println("et du coup on est dedans ou quoi ...........\n" +
			                   BadCropFolder +
			                   ".....................................");
			
			if (BadCropFolder.exists() || BadCropFolder.mkdir()) {
				File    fileToMove = new File(inputPathDir + File.separator + this.imgRawTransformed.getTitle());
				File    newFile    = new File(BadCropFolder + File.separator + this.imgRawTransformed.getTitle());
				boolean renamed    = fileToMove.renameTo(newFile);
				if (!renamed) System.err.println("File not renamed: " + fileToMove.getAbsolutePath());
			} else {
				System.err.println("Directory does not exist and could not be created: " + BadCropFolder);
			}
		}
	}
	
	
	public void checkBadCrop(ImageWrapper image, Client client) {
		if ((this.badCrop) || (this.bestThreshold == -1)) {
			List<TagAnnotationWrapper> tags;
			TagAnnotationWrapper       tagBadCrop;
			
			try {
				tags = client.getTags("BadCrop");
			} catch (Exception e) {
				System.err.println("Could not get list of \"BadCrop\" tags");
				e.printStackTrace();
				return;
			}
			
			if (tags.size() == 0) {
				try {
					tagBadCrop = new TagAnnotationWrapper(client, "BadCrop", "");
				} catch (Exception e) {
					System.err.println("Could not create new \"BadCrop\" tag");
					e.printStackTrace();
					return;
				}
			} else {
				try {
					tagBadCrop = tags.get(0);
				} catch (Exception e) {
					System.err.println("Could not retrieve a \"BadCrop\" tag");
					e.printStackTrace();
					return;
				}
			}
			
			System.out.println("Adding Bad Crop tag");
			try {
				image.addTag(client, tagBadCrop);
			} catch (Exception e) {
				System.out.println("Tag already added");
			}
		}
	}
	
	
	public void checkBadCrop(ROIWrapper roi, Client client) {
		if ((this.badCrop) || (this.bestThreshold == -1)) {
			for (GenericShapeWrapper<?> shape : roi.getShapes()) {
				shape.setStroke(Color.RED);
			}
		}
		try {
			roi.saveROI(client);
		} catch (Exception e) {
			System.out.println("Could not save bad crop ROI id: " + roi.getId());
		}
	}
	
	
	/**
	 * Method to save the OTSU segmented image.
	 * <p> TODO verifier cette methode si elle est à ca place
	 */
	public void saveOTSUSegmented() {
		if (!badCrop && bestThreshold != -1) {
			String pathSegOTSU = this.segmentationParameters.getOutputFolder() +
			                     "OTSU" +
			                     File.separator +
			                     this.imageSeg[0].getTitle();
			saveFile(this.imageSeg[0], pathSegOTSU);
			
		}
	}
	
	
	/**
	 * Method to save the OTSU segmented image.
	 * <p> TODO verifier cette methode si elle est à ca place
	 */
	public void saveOTSUSegmentedOMERO(Client client, Long id)
	throws Exception {
		if (!badCrop && bestThreshold != -1) {
			String path = new java.io.File(".").getCanonicalPath() + File.separator + this.imageSeg[0].getTitle();
			saveFile(this.imageSeg[0], path);
			
			ProjectWrapper       project  = client.getProject(id);
			List<DatasetWrapper> datasets = project.getDatasets("OTSU");
			
			DatasetWrapper otsu;
			if (datasets.isEmpty()) {
				otsu = new DatasetWrapper("OTSU", "");
				Long datasetId = project.addDataset(client, otsu).getId();
				otsu = client.getDataset(datasetId);
			} else {
				otsu = datasets.get(0);
			}
			
			otsu.importImages(client, path);
			
			File    file    = new File(path);
			boolean deleted = file.delete();
			if (!deleted) System.err.println("File not deleted: " + path);
		}
	}
	
	
	/**
	 * Method to save the OTSU segmented image.
	 * <p> TODO verifier cette methode si elle est à sa place
	 */
	public void saveGiftWrappingSeg() {
		
		if (!badCrop && bestThreshold != -1
		    && this.segmentationParameters.getGiftWrapping()) {
			ConvexHullSegmentation nuc = new ConvexHullSegmentation();
			this.imageSeg[0] = nuc.runGIFTWrapping(this.imageSeg[0], this.segmentationParameters);
			String pathSegGIFT =
					this.segmentationParameters.getOutputFolder() + "GIFT" + File.separator + this.imgRaw.getTitle();
			this.imageSeg[0].setTitle(pathSegGIFT);
			saveFile(this.imageSeg[0], pathSegGIFT);
		}
	}
	
	
	/**
	 * Method to save the OTSU segmented image.
	 * <p> TODO verifier cette methode si elle est à sa place
	 */
	public void saveGiftWrappingSegOMERO(Client client, Long id)
	throws Exception {
		if (!badCrop && bestThreshold != -1
		    && this.segmentationParameters.getGiftWrapping()) {
			ConvexHullSegmentation nuc = new ConvexHullSegmentation();
			
			this.imageSeg[0] = nuc.runGIFTWrapping(this.imageSeg[0], this.segmentationParameters);
			
			String path = new java.io.File(".").getCanonicalPath() + File.separator + this.imageSeg[0].getTitle();
			saveFile(this.imageSeg[0], path);
			
			ProjectWrapper       project  = client.getProject(id);
			List<DatasetWrapper> datasets = project.getDatasets("GIFT");
			
			DatasetWrapper gift;
			if (datasets.size() == 0) {
				gift = new DatasetWrapper("GIFT", "");
				Long datasetId = project.addDataset(client, gift).getId();
				gift = client.getDataset(datasetId);
			} else {
				gift = datasets.get(0);
			}
			
			gift.importImages(client, path);
			
			File    file    = new File(path);
			boolean deleted = file.delete();
			if (!deleted) System.err.println("File not deleted: " + path);
		}
	}
	
	
	/**
	 * Method to get the parameter of the 3D parameters for OTSU segmented image if the object can't be segmented return
	 * -1 for parameters.
	 * <p> TODO verifier cette methode si elle est à sa place
	 *
	 * @return
	 */
	public String getImageCropInfoOTSU() {
		if (!badCrop && bestThreshold != -1) {
			return saveImageResult(this.imageSeg) + "\t" + this.bestThreshold + "\n";
		} else {
			return this.imgRaw.getTitle() + "\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n";
		}
	}
	
	
	/**
	 * Method to get the parameter of the 3D parameters for Gift wrapping segmented image if the object can't be
	 * segmented return -1 for parameters.
	 * <p> TODO verifier DUPLICATION DE getImageCropInfoOTSU à sa place ?
	 *
	 * @return
	 */
	public String getImageCropInfoGIFT() {
		if (!badCrop && bestThreshold != -1) {
			return saveImageResult(this.imageSeg) + "\t" + this.bestThreshold + "\n";
		} else {
			return this.imgRaw.getTitle() + "\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\t-1\n";
		}
	}
	
}
