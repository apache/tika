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

		setTesseractPath(
				getProp(props, "tesseractPath", getTesseractPath()));
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

	}

	/** @see #setTesseractPath(String tesseractPath)*/
	public String getTesseractPath() {
		return tesseractPath;
	}
	
	/**
	 * Set tesseract installation folder, needed if it is not on system path.
	 */
	public void setTesseractPath(String tesseractPath) {
		if(!tesseractPath.isEmpty() && !tesseractPath.endsWith(File.separator))
			tesseractPath += File.separator;
		
		this.tesseractPath = tesseractPath;
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

	/** @see #setTimeout(int timeout)*/
	public int getTimeout() {
		return timeout;
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
