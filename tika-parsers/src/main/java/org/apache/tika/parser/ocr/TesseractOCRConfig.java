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
import java.io.Serializable;

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
	
	/** @see #setTesseractPath(String tesseractPath)*/
	public String getTesseractPath() {
		return tesseractPath;
	}
	
	/**
	 * Set tesseract installation folder, needed if it is not on system path.
	 */
	public void setTesseractPath(String tesseractPath) {
		if(!tesseractPath.endsWith(File.separator))
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
	
}
