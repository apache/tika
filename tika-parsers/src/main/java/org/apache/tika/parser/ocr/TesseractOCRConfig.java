/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.ocr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuration for TesseractOCRParser.
 *
 * This allows to enable TesseractOCRParser and set its parameters:
 * <p>
 * TesseractOCRConfig config = new TesseractOCRConfig();<br>
 * config.setTesseractPath(tesseractFolder);<br>
 * parseContext.set(TesseractOCRConfig.class, config);<br>
 * </p>
 *
 * Parameters can also be set by either editing the existing TesseractOCRConfig.properties file in,
 * tika-parser/src/main/resources/org/apache/tika/parser/ocr, or overriding it by creating your own
 * and placing it in the package org/apache/tika/parser/ocr on the classpath.
 *
 */
public class TesseractOCRConfig implements Serializable{

	private static final long serialVersionUID = -4861942486845757891L;

	// Path to tesseract installation folder, if not on system path.
	private  String tesseractPath = "";

    // Path to the 'tessdata' folder, which contains language files and config files.
    private String tessdataPath = "";

	// Language dictionary to be used.
	private  String language = "eng";

	// Tesseract page segmentation mode.
	private  String pageSegMode = "1";

	// Minimum file size to submit file to ocr.
	private  int minFileSizeToOcr = 0;

	// Maximum file size to submit file to ocr.
	private  int maxFileSizeToOcr = Integer.MAX_VALUE;

	// Maximum time (seconds) to wait for the ocring process termination
	private int timeout = 120;

	// enable image processing (optional)
	private int enableImageProcessing = 0;

	// Path to ImageMagick program, if not on system path.
	private String ImageMagickPath = "";
	
	// resolution of processed image (in dpi).
	private int density = 300;
	
	// number of bits in a color sample within a pixel.
	private int depth = 4;
	
	// colorspace of processed image.
	private String colorspace = "gray";
	
	// filter to be applied to the processed image.
	private String filter = "triangle";
	
	// factor by which image is to be scaled.
	private int resize = 900;
	
	/**
	 * Default contructor.
	 */
	public TesseractOCRConfig() {
		init(this.getClass().getResourceAsStream("TesseractOCRConfig.properties"));
	}

	/**
	 * Loads properties from InputStream and then tries to close InputStream.
	 * If there is an IOException, this silently swallows the exception
	 * and goes back to the default.
	 *
	 * @param is
	 */
	public TesseractOCRConfig(InputStream is) {
		init(is);
	}

	private void init(InputStream is) {
		if (is == null) {
			return;
		}
		Properties props = new Properties();
		try {
			props.load(is);
		} catch (IOException e) {
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					//swallow
				}
			}
		}

		// set parameters for Tesseract
		setTesseractPath(
				getProp(props, "tesseractPath", getTesseractPath()));
        setTessdataPath(
                getProp(props, "tessdataPath", getTessdataPath()));
		setLanguage(
				getProp(props, "language", getLanguage()));
		setPageSegMode(
				getProp(props, "pageSegMode", getPageSegMode()));
		setMinFileSizeToOcr(
				getProp(props, "minFileSizeToOcr", getMinFileSizeToOcr()));
		setMaxFileSizeToOcr(
				getProp(props, "maxFileSizeToOcr", getMaxFileSizeToOcr()));
		setTimeout(
                getProp(props, "timeout", getTimeout()));
		
		// set parameters for ImageMagick
		setEnableImageProcessing(
				getProp(props, "enableImageProcessing", isEnableImageProcessing()));
		setImageMagickPath(
				getProp(props, "ImageMagickPath", getImageMagickPath()));
		setDensity(
				getProp(props, "density", getDensity()));
		setDepth(
				getProp(props, "depth", getDepth()));
		setColorspace(
				getProp(props, "colorspace", getColorspace()));
		setFilter(
				getProp(props, "filter", getFilter()));
		setResize(
				getProp(props, "resize", getResize()));

	}

	/** @see #setTesseractPath(String tesseractPath)*/
	public String getTesseractPath() {
		return tesseractPath;
	}

	/**
	 * Set the path to the Tesseract executable, needed if it is not on system path.
     * <p>
     * Note that if you set this value, it is highly recommended that you also
     * set the path to the 'tessdata' folder using {@link #setTessdataPath}.
     * </p>
	 */
	public void setTesseractPath(String tesseractPath) {
		if(!tesseractPath.isEmpty() && !tesseractPath.endsWith(File.separator))
			tesseractPath += File.separator;

		this.tesseractPath = tesseractPath;
	}

    /** @see #setTessdataPath(String tessdataPath) */
    public String getTessdataPath() {
        return tessdataPath;
    }

    /**
     * Set the path to the 'tessdata' folder, which contains language files and config files. In some cases (such
     * as on Windows), this folder is found in the Tesseract installation, but in other cases
     * (such as when Tesseract is built from source), it may be located elsewhere.
     */
    public void setTessdataPath(String tessdataPath) {
        if(!tessdataPath.isEmpty() && !tessdataPath.endsWith(File.separator))
            tessdataPath += File.separator;

        this.tessdataPath = tessdataPath;
    }

	/** @see #setLanguage(String language)*/
	public String getLanguage() {
		return language;
	}

	/**
	 * Set tesseract language dictionary to be used. Default is "eng".
	 * Multiple languages may be specified, separated by plus characters.
	 */
	public void setLanguage(String language) {
		if (!language.matches("([A-Za-z](\\+?))*")) {
			throw new IllegalArgumentException("Invalid language code");
		}
		this.language = language;
	}

	/** @see #setPageSegMode(String pageSegMode)*/
	public String getPageSegMode() {
		return pageSegMode;
	}

	/**
	 * Set tesseract page segmentation mode.
	 * Default is 1 = Automatic page segmentation with OSD (Orientation and Script Detection)
	 */
	public void setPageSegMode(String pageSegMode) {
		if (!pageSegMode.matches("[1-9]|10")) {
			throw new IllegalArgumentException("Invalid language code");
		}
		this.pageSegMode = pageSegMode;
	}

	/** @see #setMinFileSizeToOcr(int minFileSizeToOcr)*/
	public int getMinFileSizeToOcr() {
		return minFileSizeToOcr;
	}

	/**
	 * Set minimum file size to submit file to ocr.
	 * Default is 0.
	 */
	public void setMinFileSizeToOcr(int minFileSizeToOcr) {
		this.minFileSizeToOcr = minFileSizeToOcr;
	}

	/** @see #setMaxFileSizeToOcr(int maxFileSizeToOcr)*/
	public int getMaxFileSizeToOcr() {
		return maxFileSizeToOcr;
	}

	/**
	 * Set maximum file size to submit file to ocr.
	 * Default is Integer.MAX_VALUE.
	 */
	public void setMaxFileSizeToOcr(int maxFileSizeToOcr) {
		this.maxFileSizeToOcr = maxFileSizeToOcr;
	}

	/**
	 * Set maximum time (seconds) to wait for the ocring process to terminate.
	 * Default value is 120s.
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/** @see #setTimeout(int timeout)
	 * @return timeout value for Tesseract */
	public int getTimeout() {
		return timeout;
	}	

	/** @see #setEnableImageProcessing(boolean)
	 * @return image processing is enabled or not */
	public int isEnableImageProcessing() {
		return enableImageProcessing;
	}

	/** 
	 * Set the value to true if processing is to be enabled.
	 * Default value is false.
	 */
	public void setEnableImageProcessing(int enableImageProcessing) {
		this.enableImageProcessing = enableImageProcessing;
	}

	/**
	 * @return the density
	 */
	public int getDensity() {
		return density;
	}

	/**
	 * @param density the density to set. Valid range of values is 150-1200.
	 * Default value is 300.
	 */
	public void setDensity(int density) {
		if(density < 150 || density > 1200) {
			throw new IllegalArgumentException("Invalid density value. Valid range of values is 150-1200.");
		}
		this.density = density;
	}

	/**
	 * @return the depth
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * @param depth the depth to set. Valid values are 2, 4, 8, 16, 32, 64, 256, 4096.
	 * Default value is 4.
	 */
	public void setDepth(int depth) {
		int[] allowedValues = {2, 4, 8, 16, 32, 64, 256, 4096};
		for (int i = 0; i < allowedValues.length; i++) {
			if(depth == allowedValues[i]) {
				this.depth = depth;
				return;
			}
		}
		throw new IllegalArgumentException("Invalid depth value. Valid values are 2, 4, 8, 16, 32, 64, 256, 4096.");
	}

	/**
	 * @return the colorspace
	 */
	public String getColorspace() {
		return colorspace;
	}

	/**
	 * @param colorspace the colorspace to set
	 * Deafult value is gray.
	 */
	public void setColorspace(String colorspace) {
		if(!colorspace.equals(null)) {
			this.colorspace = colorspace;
		} else {
			throw new IllegalArgumentException("Colorspace value cannot be null.");
		}
	}

	/**
	 * @return the filter
	 */
	public String getFilter() {
		return filter;
	}

	/**
	 * @param filter the filter to set. Valid values are point, hermite, cubic, box, gaussian, catrom, triangle, quadratic and mitchell.
	 * Default value is triangle.
	 */
	public void setFilter(String filter) {
		if(filter.equals(null)) {
			throw new IllegalArgumentException("Filter value cannot be null. Valid values are point, hermite, "
					+ "cubic, box, gaussian, catrom, triangle, quadratic and mitchell.");
		}
		
		String[] allowedFilters = {"Point", "Hermite", "Cubic", "Box", "Gaussian", "Catrom", "Triangle", "Quadratic", "Mitchell"};
		for (int i = 0; i < allowedFilters.length; i++) {
			if(filter.equalsIgnoreCase(allowedFilters[i])) {
				this.filter = filter;
				return;
			}
		}
		throw new IllegalArgumentException("Invalid filter value. Valid values are point, hermite, "
					+ "cubic, box, gaussian, catrom, triangle, quadratic and mitchell.");
	}

	/**
	 * @return the resize
	 */
	public int getResize() {
		return resize;
	}

	/**
	 * @param resize the resize to set. Valid range of values is 100-900.
	 * Default value is 900.
	 */
	public void setResize(int resize) {
		for(int i=1;i<10;i++) {
			if(resize == i*100) {
				this.resize = resize;
				return;
			}
		}
		throw new IllegalArgumentException("Invalid resize value. Valid range of values is 100-900.");
	}

	/** @see #setImageMagickPath(String ImageMagickPath)
	 * @return path to ImageMagick file. */
	public String getImageMagickPath() {
		
		return ImageMagickPath;
	}
	
	/**
	 * Set the path to the ImageMagick executable, needed if it is not on system path.
	 * @param path to ImageMagick file.
	 */
	public void setImageMagickPath(String ImageMagickPath) {
		if(!ImageMagickPath.isEmpty() && !ImageMagickPath.endsWith(File.separator))
			ImageMagickPath += File.separator;
		
		this.ImageMagickPath = ImageMagickPath;
	}
	
	/**
	 * Get property from the properties file passed in.
	 * @param properties properties file to read from.
	 * @param property the property to fetch.
	 * @param defaultMissing default parameter to use.
	 * @return the value.
	 */
	private int getProp(Properties properties, String property, int defaultMissing) {
		String p = properties.getProperty(property);
		if (p == null || p.isEmpty()){
			return defaultMissing;
		}
		try {
			return Integer.parseInt(p);
		} catch (Throwable ex) {
			throw new RuntimeException(String.format(Locale.ROOT, "Cannot parse TesseractOCRConfig variable %s, invalid integer value",
					property), ex);
		}
	}

	/**
	 * Get property from the properties file passed in.
	 * @param properties properties file to read from.
	 * @param property the property to fetch.
	 * @param defaultMissing default parameter to use.
	 * @return the value.
	 */
	private String getProp(Properties properties, String property, String defaultMissing) {
		return properties.getProperty(property, defaultMissing);
	}	
}
