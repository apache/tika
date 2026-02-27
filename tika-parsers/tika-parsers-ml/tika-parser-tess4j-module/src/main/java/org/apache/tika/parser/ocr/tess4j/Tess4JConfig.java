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
package org.apache.tika.parser.ocr.tess4j;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * Configuration for {@link Tess4JParser}.
 * <p>
 * This class is not thread-safe and must be synchronized externally.
 * </p>
 */
public class Tess4JConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Language dictionary to be used. Default is "eng".
     */
    private String language = "eng";

    /**
     * Path to the tessdata directory containing language data files.
     * If empty, tess4j will try to find the tessdata directory automatically.
     */
    private String dataPath = "";

    /**
     * Tesseract page segmentation mode. Default is 1.
     * <ul>
     *   <li>0 = Orientation and script detection (OSD) only.</li>
     *   <li>1 = Automatic page segmentation with OSD.</li>
     *   <li>3 = Fully automatic page segmentation, but no OSD. (Default for Tesseract)</li>
     *   <li>6 = Assume a single uniform block of text.</li>
     * </ul>
     */
    private int pageSegMode = 1;

    /**
     * Tesseract OCR Engine mode. Default is 3 (Default, based on what is available).
     * <ul>
     *   <li>0 = Original Tesseract only.</li>
     *   <li>1 = Neural nets LSTM only.</li>
     *   <li>2 = Tesseract + LSTM.</li>
     *   <li>3 = Default, based on what is available.</li>
     * </ul>
     */
    private int ocrEngineMode = 3;

    /**
     * Maximum file size (in bytes) to submit to OCR. Default is 50 MB.
     */
    private long maxFileSizeToOcr = 50 * 1024 * 1024;

    /**
     * Minimum file size (in bytes) to submit to OCR. Default is 0.
     */
    private long minFileSizeToOcr = 0;

    /**
     * Number of Tesseract instances to keep in the pool. Default is 2.
     */
    private int poolSize = 2;

    /**
     * Maximum time (in seconds) to wait for a Tesseract instance from the pool.
     * Default is 120.
     */
    private int timeoutSeconds = 120;

    /**
     * Runtime switch to turn off OCR.
     */
    private boolean skipOcr = false;

    /**
     * DPI for image rendering. Default is 300.
     */
    private int dpi = 300;

    /**
     * Maximum total pixels (width &times; height) allowed for an image
     * before OCR is skipped.  This prevents OOM from decompressing
     * pathologically large images (e.g., a 30,000 &times; 30,000 image
     * would require ~3.6 GB of heap as a BufferedImage).
     * <p>
     * Default is 100,000,000 (100 megapixels, ~10,000 &times; 10,000).
     * Set to {@code -1} for no limit (not recommended).
     */
    private long maxImagePixels = 100_000_000L;

    /**
     * Path to the directory containing native Tesseract and Leptonica shared libraries
     * (e.g., {@code libtesseract.dylib}, {@code libtesseract.so}).
     * <p>
     * On macOS with Homebrew, this is typically {@code /opt/homebrew/lib}.
     * On Linux, it may be {@code /usr/lib} or {@code /usr/local/lib}.
     * <p>
     * If empty, JNA will search the default system library paths.
     */
    private String nativeLibPath = "";

    public String getLanguage() {
        return language;
    }

    /**
     * Set tesseract language dictionary to be used. Default is "eng".
     * Multiple languages may be specified, separated by plus characters.
     * e.g. "eng+fra"
     */
    public void setLanguage(String language) {
        Set<String> invalidCodes = new HashSet<>();
        Set<String> validCodes = new HashSet<>();
        validateLangs(language, validCodes, invalidCodes);
        if (!invalidCodes.isEmpty()) {
            throw new IllegalArgumentException("Invalid language code(s): " + invalidCodes);
        }
        this.language = language;
    }

    public String getDataPath() {
        return dataPath;
    }

    /**
     * Set the path to the tessdata directory.
     */
    public void setDataPath(String dataPath) throws TikaConfigException {
        this.dataPath = dataPath;
    }

    public int getPageSegMode() {
        return pageSegMode;
    }

    /**
     * Set tesseract page segmentation mode.
     * Default is 1.
     */
    public void setPageSegMode(int pageSegMode) {
        if (pageSegMode < 0 || pageSegMode > 13) {
            throw new IllegalArgumentException(
                    "Invalid page segmentation mode: " + pageSegMode +
                            ". Must be between 0 and 13.");
        }
        this.pageSegMode = pageSegMode;
    }

    public int getOcrEngineMode() {
        return ocrEngineMode;
    }

    /**
     * Set OCR Engine Mode.
     * Default is 3.
     */
    public void setOcrEngineMode(int ocrEngineMode) {
        if (ocrEngineMode < 0 || ocrEngineMode > 3) {
            throw new IllegalArgumentException(
                    "Invalid OCR Engine Mode: " + ocrEngineMode +
                            ". Must be between 0 and 3.");
        }
        this.ocrEngineMode = ocrEngineMode;
    }

    public long getMaxFileSizeToOcr() {
        return maxFileSizeToOcr;
    }

    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        this.maxFileSizeToOcr = maxFileSizeToOcr;
    }

    public long getMinFileSizeToOcr() {
        return minFileSizeToOcr;
    }

    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        this.minFileSizeToOcr = minFileSizeToOcr;
    }

    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Set the number of Tesseract instances to keep in the pool.
     * Default is 2. Must be at least 1.
     */
    public void setPoolSize(int poolSize) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("Pool size must be at least 1, got: " + poolSize);
        }
        this.poolSize = poolSize;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Set maximum time (seconds) to wait for a pooled Tesseract instance.
     * Default is 120.
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isSkipOcr() {
        return skipOcr;
    }

    public void setSkipOcr(boolean skipOcr) {
        this.skipOcr = skipOcr;
    }

    public int getDpi() {
        return dpi;
    }

    /**
     * Set the DPI for image rendering. Default is 300.
     */
    public void setDpi(int dpi) {
        if (dpi < 72 || dpi > 1200) {
            throw new IllegalArgumentException("DPI must be between 72 and 1200, got: " + dpi);
        }
        this.dpi = dpi;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    /**
     * Set the maximum total pixels (width &times; height) allowed for
     * an image before OCR is skipped. Default is 100,000,000
     * (100 megapixels). Set to {@code -1} for no limit (not recommended).
     */
    public void setMaxImagePixels(long maxImagePixels) {
        if (maxImagePixels < 1 && maxImagePixels != -1) {
            throw new IllegalArgumentException(
                    "maxImagePixels must be -1 (no limit) or at least 1, got: "
                            + maxImagePixels);
        }
        this.maxImagePixels = maxImagePixels;
    }

    public String getNativeLibPath() {
        return nativeLibPath;
    }

    /**
     * Set the path to the directory containing native Tesseract/Leptonica shared libraries.
     * On macOS with Homebrew this is typically {@code /opt/homebrew/lib}.
     */
    public void setNativeLibPath(String nativeLibPath) throws TikaConfigException {
        this.nativeLibPath = nativeLibPath;
    }

    /**
     * Validates language strings. Languages should conform to tesseract's expected format.
     */
    static void validateLangs(String language, Set<String> validLangs, Set<String> invalidLangs) {
        if (StringUtils.isBlank(language)) {
            return;
        }
        language = language.replaceAll("\\s", "");
        if (language.matches("\\+.*|.*\\+")) {
            throw new IllegalArgumentException(
                    "Invalid syntax - Can't start or end with +: " + language);
        }
        final String[] langs = language.split("\\+");
        for (String lang : langs) {
            if (!lang.matches(
                    "([a-zA-Z]{3}(_[a-zA-Z]{3,4}){0,2})|script(/|\\\\)[A-Z][a-zA-Z_]+")) {
                invalidLangs.add(lang + " (invalid syntax)");
            } else {
                validLangs.add(lang);
            }
        }
    }

    /**
     * Runtime-only Tess4JConfig that prevents modification of paths and
     * pool settings during parse-time configuration.
     * <p>
     * <b>Always blocked:</b> {@code dataPath}, {@code nativeLibPath},
     * {@code poolSize}.
     * <p>
     * Paths are blocked to prevent file-system access attacks.
     * Pool size is blocked because the pool is built at init time and
     * cannot be resized at runtime.
     */
    public static class RuntimeConfig extends Tess4JConfig {

        public RuntimeConfig() {
            super();
        }

        @Override
        public void setDataPath(String dataPath) throws TikaConfigException {
            if (!StringUtils.isBlank(dataPath)) {
                throw new TikaConfigException(
                        "Cannot modify dataPath at runtime. " +
                                "Paths must be configured at parser initialization time.");
            }
        }

        @Override
        public void setNativeLibPath(String nativeLibPath) throws TikaConfigException {
            if (!StringUtils.isBlank(nativeLibPath)) {
                throw new TikaConfigException(
                        "Cannot modify nativeLibPath at runtime. " +
                                "Paths must be configured at parser initialization time.");
            }
        }

        @Override
        public void setPoolSize(int poolSize) {
            throw new IllegalStateException(
                    "Cannot modify poolSize at runtime. " +
                            "The pool is created at initialization time " +
                            "and cannot be resized.");
        }

        @Override
        public void setMaxImagePixels(long maxImagePixels) {
            throw new IllegalStateException(
                    "Cannot modify maxImagePixels at runtime. " +
                            "Image size limits must be configured at " +
                            "initialization time.");
        }
    }
}
