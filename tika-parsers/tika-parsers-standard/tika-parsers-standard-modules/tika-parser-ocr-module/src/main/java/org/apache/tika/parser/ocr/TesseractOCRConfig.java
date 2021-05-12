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

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.StringUtils;

/**
 * Configuration for TesseractOCRParser.
 * This class is not thread safe and must be synchronized externally.
 * <p>
 * This class will remember all set* field forever,
 * and on {@link #cloneAndUpdate(TesseractOCRConfig)},
 * it will update all the fields that have been set on the "update" config.
 * So, for example, if you want to change language to "fra"
 * from "eng" and then on another parse,
 * you want to change depth to 5 on the same update object,
 * but you expect the language to revert to "eng", you'll be wrong.
 * Create a new update config for each parse unless you're only changing the
 * same field(s) with every parse.
 */
public class TesseractOCRConfig implements Serializable {

    private static final long serialVersionUID = -4861942486845757891L;

    private static final Logger LOG = LoggerFactory.getLogger(TesseractOCRConfig.class);

    private static Pattern ALLOWABLE_PAGE_SEPARATORS_PATTERN =
            Pattern.compile("(?i)^[-_/\\.A-Z0-9]+$");

    private static Pattern ALLOWABLE_OTHER_PARAMS_PATTERN =
            Pattern.compile("(?i)^[-_/\\.A-Z0-9]+$");
    // Language dictionary to be used.
    private String language = "eng";
    // Tesseract page segmentation mode.
    private String pageSegMode = "1";
    // Minimum file size to submit file to ocr.
    private long minFileSizeToOcr = 0;
    // Maximum file size to submit file to ocr.
    private long maxFileSizeToOcr = Integer.MAX_VALUE;
    // Maximum time (seconds) to wait for the ocring process termination
    private int timeoutSeconds = 120;
    // The format of the ocr'ed output to be returned, txt or hocr.
    private OUTPUT_TYPE outputType = OUTPUT_TYPE.TXT;
    // enable image preprocessing with ImageMagick (optional)
    private boolean enableImagePreprocessing = false;
    // resolution of processed image (in dpi).
    private int density = 300;
    // number of bits in a color sample within a pixel.
    private int depth = 4;
    // colorspace of processed image.
    private String colorspace = "gray";
    // filter to be applied to the processed image.
    private String filter = "triangle";
    // factor by which image is to be scaled.
    // TODO: we should make this dynamic depending on the size of the image
    // The current testRotation.png takes minutes to expand 900%
    private int resize = 200;
    // See setPageSeparator.
    private String pageSeparator = "";
    // whether or not to preserve interword spacing
    private boolean preserveInterwordSpacing = false;
    // whether or not to apply rotation calculated by the rotation.py script
    private boolean applyRotation = false;
    // runtime switch to turn off OCR
    private boolean skipOcr = false;
    // See addOtherTesseractConfig.
    private Map<String, String> otherTesseractConfig = new HashMap<>();
    private Set<String> userConfigured = new HashSet<>();

    /**
     * This takes a language string, parses it and then bins individual langs into
     * valid or invalid based on regexes against the language codes
     *
     * @param language
     * @param validLangs
     * @param invalidLangs
     */
    public static void getLangs(String language, Set<String> validLangs, Set<String> invalidLangs) {
        if (StringUtils.isBlank(language)) {
            return;
        }
        // Get rid of embedded spaces
        language = language.replaceAll("\\s", "");
        // Test for leading or trailing +
        if (language.matches("\\+.*|.*\\+")) {
            throw new IllegalArgumentException(
                    "Invalid syntax - Can't start or end with +" + language);
        }
        // Split on the + sign
        final String[] langs = language.split("\\+");
        for (String lang : langs) {
            // First, make sure it conforms to the correct syntax
            if (!lang.matches(
                    "([a-zA-Z]{3}(_[a-zA-Z]{3,4}){0,2})|script(/|\\\\)[A-Z][a-zA-Z_]+")) {
                invalidLangs.add(lang + " (invalid syntax)");
            } else {
                validLangs.add(lang);
            }
        }
    }

    /**
     * @see #setLanguage(String language)
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Set tesseract language dictionary to be used. Default is "eng".
     * languages are either:
     * <ol>
     *   <li>Nominally an ISO-639-2 code but compound codes are allowed separated by underscore:
     *   e.g., chi_tra_vert, aze_cyrl</li>
     *   <li>A file path in the script directory.  The name starts with upper-case letter.
     *       Some of them have underscores and other upper-case letters: e.g., script/Arabic,
     *       script/HanS_vert, script/Japanese_vert, script/Canadian_Aboriginal</li>
     * </ol>
     * Multiple languages may be specified, separated by plus characters.
     * e.g. "chi_tra+chi_sim+script/Arabic"
     */
    public void setLanguage(String languageString) {
        Set<String> invalidCodes = new HashSet<>();
        Set<String> validCodes = new HashSet<>();
        getLangs(languageString, validCodes, invalidCodes);
        if (!invalidCodes.isEmpty()) {
            throw new IllegalArgumentException("Invalid language code(s): " + invalidCodes);
        }
        this.language = languageString;
        userConfigured.add("language");
    }

    /**
     * @see #setPageSegMode(String pageSegMode)
     */
    public String getPageSegMode() {
        return pageSegMode;
    }

    /**
     * Set tesseract page segmentation mode.
     * Default is 1 = Automatic page segmentation with OSD (Orientation and Script Detection)
     */
    public void setPageSegMode(String pageSegMode) {
        if (!pageSegMode.matches("[0-9]|10|11|12|13")) {
            throw new IllegalArgumentException("Invalid page segmentation mode");
        }
        this.pageSegMode = pageSegMode;
        userConfigured.add("pageSegMode");
    }

    /**
     * @see #setPageSeparator(String pageSeparator)
     */
    public String getPageSeparator() {
        return pageSeparator;
    }

    /**
     * The page separator to use in plain text output.  This corresponds to Tesseract's
     * page_separator config option.
     * The default here is the empty string (i.e. no page separators).  Note that this is also
     * the default in
     * Tesseract 3.x, but in Tesseract 4.0 the default is to use the form feed control character.
     * We are overriding
     * Tesseract 4.0's default here.
     *
     * @param pageSeparator
     */
    public void setPageSeparator(String pageSeparator) {
        Matcher m = ALLOWABLE_PAGE_SEPARATORS_PATTERN.matcher(pageSeparator);
        if (!m.find()) {
            throw new IllegalArgumentException(pageSeparator + " contains illegal characters.\n" +
                    "If you trust this value, set it with setTrustedPageSeparator");
        }
        setTrustedPageSeparator(pageSeparator);
        userConfigured.add("pageSeparator");
    }

    /**
     * Same as {@link #setPageSeparator(String)} but does not perform
     * any checks on the string.
     *
     * @param pageSeparator
     */
    public void setTrustedPageSeparator(String pageSeparator) {
        this.pageSeparator = pageSeparator;
    }

    /**
     * @return whether or not to maintain interword spacing.
     */
    public boolean isPreserveInterwordSpacing() {
        return preserveInterwordSpacing;
    }

    /**
     * Whether or not to maintain interword spacing.  Default is <code>false</code>.
     *
     * @param preserveInterwordSpacing
     */
    public void setPreserveInterwordSpacing(boolean preserveInterwordSpacing) {
        this.preserveInterwordSpacing = preserveInterwordSpacing;
        userConfigured.add("preserveInterwordSpacing");
    }

    /**
     * @see #setMinFileSizeToOcr(long minFileSizeToOcr)
     */
    public long getMinFileSizeToOcr() {
        return minFileSizeToOcr;
    }

    /**
     * Set minimum file size to submit file to ocr.
     * Default is 0.
     */
    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        this.minFileSizeToOcr = minFileSizeToOcr;
        userConfigured.add("minFileSizeToOcr");
    }

    /**
     * @see #setMaxFileSizeToOcr(long maxFileSizeToOcr)
     */
    public long getMaxFileSizeToOcr() {
        return maxFileSizeToOcr;
    }

    /**
     * Set maximum file size to submit file to ocr.
     * Default is Integer.MAX_VALUE.
     */
    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        this.maxFileSizeToOcr = maxFileSizeToOcr;
        userConfigured.add("maxFileSizeToOcr");
    }

    /**
     * @return timeout value for Tesseract
     * @see #setTimeoutSeconds(int timeout)
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Set maximum time (seconds) to wait for the ocring process to terminate.
     * Default value is 120s.
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        userConfigured.add("timeoutSeconds");
    }

    /**
     * @see #setOutputType(OUTPUT_TYPE outputType)
     */
    public OUTPUT_TYPE getOutputType() {
        return outputType;
    }

    /**
     * Set output type from ocr process.  Default is "txt", but can be "hocr".
     * Default value is {@link OUTPUT_TYPE#TXT}.
     */
    public void setOutputType(OUTPUT_TYPE outputType) {
        this.outputType = outputType;
        userConfigured.add("outputType");
    }

    public void setOutputType(String outputType) {
        if (outputType == null) {
            throw new IllegalArgumentException("outputType must not be null");
        }
        String lc = outputType.toLowerCase(Locale.US);
        if ("txt".equals(lc)) {
            setOutputType(OUTPUT_TYPE.TXT);
        } else if ("hocr".equals(lc)) {
            setOutputType(OUTPUT_TYPE.HOCR);
        } else {
            throw new IllegalArgumentException("outputType must be either 'txt' or 'hocr'");
        }
    }

    /**
     * @return image processing is enabled or not
     * @see #setEnableImagePreprocessing(boolean)
     */
    public boolean isEnableImagePreprocessing() {
        return enableImagePreprocessing;
    }

    /**
     * Set the value to true if processing is to be enabled.
     * Default value is false.
     */
    public void setEnableImagePreprocessing(boolean enableImagePreprocessing) {
        this.enableImagePreprocessing = enableImagePreprocessing;
        userConfigured.add("enableImagePreprocessing");
    }

    /**
     * @return the density
     */
    public int getDensity() {
        return density;
    }

    /**
     * @param density the density to set. Valid range of values is 150-1200.
     *                Default value is 300.
     */
    public void setDensity(int density) {
        if (density < 150 || density > 1200) {
            throw new IllegalArgumentException(
                    "Invalid density value. Valid range of values is 150-1200.");
        }
        this.density = density;
        userConfigured.add("density");
    }

    /**
     * @return the depth
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @param depth the depth to set. Valid values are 2, 4, 8, 16, 32, 64, 256, 4096.
     *              Default value is 4.
     */
    public void setDepth(int depth) {
        int[] allowedValues = {2, 4, 8, 16, 32, 64, 256, 4096};
        for (int allowedValue : allowedValues) {
            if (depth == allowedValue) {
                this.depth = depth;
                userConfigured.add("depth");
                return;
            }
        }
        throw new IllegalArgumentException(
                "Invalid depth value. Valid values are 2, 4, 8, 16, 32, 64, 256, 4096.");
    }

    /**
     * @return the colorspace
     */
    public String getColorspace() {
        return colorspace;
    }

    /**
     * @param colorspace the colorspace to set
     *                   Deafult value is gray.
     */
    public void setColorspace(String colorspace) {
        if (colorspace == null) {
            throw new IllegalArgumentException("Colorspace value cannot be null.");
        }
        if (!colorspace.matches("(?i)^[-_A-Z0-9]+$")) {
            throw new IllegalArgumentException(
                    "colorspace must match this pattern: (?i)^[-_A-Z0-9]+$");
        }
        this.colorspace = colorspace;
        userConfigured.add("colorspace");
    }

    /**
     * @return the filter
     */
    public String getFilter() {
        return filter;
    }

    /**
     * @param filter the filter to set. Valid values are point, hermite, cubic, box, gaussian,
     *               catrom, triangle, quadratic and mitchell.
     *               Default value is triangle.
     */
    public void setFilter(String filter) {
        if (filter.equals(null)) {
            throw new IllegalArgumentException(
                    "Filter value cannot be null. Valid values are point, hermite, " +
                            "cubic, box, gaussian, catrom, triangle, quadratic and mitchell.");
        }

        String[] allowedFilters =
                {"Point", "Hermite", "Cubic", "Box", "Gaussian", "Catrom", "Triangle", "Quadratic",
                        "Mitchell"};
        for (String allowedFilter : allowedFilters) {
            if (filter.equalsIgnoreCase(allowedFilter)) {
                this.filter = filter;
                userConfigured.add("filter");
                return;
            }
        }
        throw new IllegalArgumentException(
                "Invalid filter value. Valid values are point, hermite, " +
                        "cubic, box, gaussian, catrom, triangle, quadratic and mitchell.");
    }

    public boolean isSkipOcr() {
        return skipOcr;
    }

    /**
     * If you want to turn off OCR at run time for a specific file,
     * set this to <code>true</code>
     *
     * @param skipOcr
     */
    public void setSkipOcr(boolean skipOcr) {
        this.skipOcr = skipOcr;
        userConfigured.add("skipOcr");
    }

    /**
     * @return the resize
     */
    public int getResize() {
        return resize;
    }

    /**
     * @param resize the resize to set. Valid range of values is 100-900.
     *               Default value is 900.
     */
    public void setResize(int resize) {
        for (int i = 1; i < 10; i++) {
            if (resize == i * 100) {
                this.resize = resize;
                userConfigured.add("resize");
                return;
            }
        }
        throw new IllegalArgumentException(
                "Invalid resize value. Valid range of values is 100-900.");
    }

    /**
     * @return Whether or not a rotation value should be calculated and passed to ImageMagick
     * before performing OCR.
     */
    public boolean isApplyRotation() {
        return this.applyRotation;
    }

    /**
     * Sets whether or not a rotation value should be calculated and passed to ImageMagick.
     *
     * @param applyRotation to calculate and apply rotation, false to skip.  Default is false
     */
    public void setApplyRotation(boolean applyRotation) {
        this.applyRotation = applyRotation;
        userConfigured.add("applyRotation");
    }

    /**
     * @see #addOtherTesseractConfig(String, String)
     */
    public Map<String, String> getOtherTesseractConfig() {
        return otherTesseractConfig;
    }

    /**
     * Add a key-value pair to pass to Tesseract using its -c command line option.
     * To see the possible options, run tesseract --print-parameters.
     * <p>
     * You may also add these parameters in TesseractOCRConfig.properties; any
     * key-value pair in the properties file where the key contains an underscore
     * is passed directly to Tesseract.
     *
     * @param key
     * @param value
     */
    public void addOtherTesseractConfig(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        Matcher m = ALLOWABLE_OTHER_PARAMS_PATTERN.matcher(key);
        if (!m.find()) {
            throw new IllegalArgumentException("Key contains illegal characters: " + key);
        }
        m.reset(value);
        if (!m.find()) {
            throw new IllegalArgumentException("Value contains illegal characters: " + value);
        }
        otherTesseractConfig.put(key.trim(), value.trim());
        userConfigured.add("otherTesseractConfig");
    }

    public TesseractOCRConfig cloneAndUpdate(TesseractOCRConfig updates) throws TikaException {
        TesseractOCRConfig updated = new TesseractOCRConfig();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (Modifier.isFinal(field.getModifiers())) {
                continue;
            } else if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if ("userConfigured".equals(field.getName())) {
                continue;
            }
            if ("otherTesseractConfig".equals(field.getName()) &&
                    updates.userConfigured.contains(field.getName())) {
                //deep copy
                for (Map.Entry<String, String> e : updates.getOtherTesseractConfig().entrySet()) {
                    updated.addOtherTesseractConfig(e.getKey(), e.getValue());
                }
                continue;
            }
            if (updates.userConfigured.contains(field.getName())) {
                try {
                    field.set(updated, field.get(updates));
                } catch (IllegalAccessException e) {
                    throw new TikaException("can't update " + field.getName(), e);
                }
            } else {
                try {
                    field.set(updated, field.get(this));
                } catch (IllegalAccessException e) {
                    throw new TikaException("can't update " + field.getName(), e);
                }
            }
        }
        return updated;
    }

    public enum OUTPUT_TYPE {
        TXT, HOCR
    }
}
