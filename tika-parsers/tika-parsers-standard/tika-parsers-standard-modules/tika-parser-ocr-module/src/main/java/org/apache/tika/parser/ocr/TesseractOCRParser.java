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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractExternalProcessParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * TesseractOCRParser powered by tesseract-ocr engine. To enable this parser,
 * create a {@link TesseractOCRConfig} object and pass it through a
 * ParseContext. Tesseract-ocr must be installed and on system path or the path
 * to its root folder must be provided:
 * <p>
 * TesseractOCRConfig config = new TesseractOCRConfig();<br>
 * //Needed if tesseract is not on system path<br>
 * config.setTesseractPath(tesseractFolder);<br>
 * parseContext.set(TesseractOCRConfig.class, config);<br>
 * </p>
 */
public class TesseractOCRParser extends AbstractExternalProcessParser implements Initializable {

    public static final String TESS_META = "tess:";
    public static final Property IMAGE_ROTATION = Property.externalRealSeq(TESS_META + "rotation");
    public static final Property IMAGE_MAGICK =
            Property.externalBooleanSeq(TESS_META + "image_magick_processed");
    private static final String TESSDATA_PREFIX = "TESSDATA_PREFIX";

    public static final Property
            PSM0_PAGE_NUMBER = Property.externalInteger(TESS_META + "page_number");
    public static final Property
            PSM0_ORIENTATION = Property.externalInteger(TESS_META + "orientation");
    public static final Property PSM0_ROTATE = Property.externalInteger(TESS_META + "rotate");
    public static final Property PSM0_ORIENTATION_CONFIDENCE = Property.externalReal(TESS_META +
            "orientation_confidence");
    public static final Property PSM0_SCRIPT = Property.externalText(TESS_META + "script");
    public static final Property PSM0_SCRIPT_CONFIDENCE = Property.externalReal(TESS_META +
            "script_confidence");

    private static final String OCR = "ocr-";
    private static final Logger LOG = LoggerFactory.getLogger(TesseractOCRParser.class);
    private static final Object[] LOCK = new Object[0];
    private static final long serialVersionUID = -8167538283213097265L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    new MediaType[]{MediaType.image(OCR + "png"), MediaType.image(OCR + "jpeg"),
                            MediaType.image(OCR + "tiff"), MediaType.image(OCR + "bmp"),
                            MediaType.image(OCR + "gif"),
                            //these are not currently covered by other parsers
                            MediaType.image("jp2"), MediaType.image("jpx"),
                            MediaType.image("x-portable-pixmap"),
                            //add the ocr- versions as well
                            MediaType.image(OCR + "jp2"), MediaType.image(OCR + "jpx"),
                            MediaType.image(OCR + "x-portable-pixmap"),

                    })));
    private static volatile boolean HAS_WARNED = false;
    //if a user specifies a custom tess path or tessdata path
    //load the available languages at initialization time
    private final Set<String> langs = new HashSet<>();
    private final TesseractOCRConfig defaultConfig = new TesseractOCRConfig();
    private String tesseractPath = "";
    private String tessdataPath = "";
    private String imageMagickPath = "";
    //if set to true, this will run --list-langs
    //at initialization and then check langs
    //at parse time
    private boolean preloadLangs = false;
    private boolean hasTesseract;
    private boolean hasImageMagick;
    private ImagePreprocessor imagePreprocessor;

    public static String getImageMagickProg() {
        return System.getProperty("os.name").startsWith("Windows") ? "magick" : "convert";
    }

    public static String getTesseractProg() {
        return System.getProperty("os.name").startsWith("Windows") ? "tesseract.exe" : "tesseract";
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        // If Tesseract is installed, offer our supported image types
        TesseractOCRConfig config = context.get(TesseractOCRConfig.class);
        if (hasTesseract) {
            if (config == null || !config.isSkipOcr()) {
                return SUPPORTED_TYPES;
            }
        }
        // Otherwise don't advertise anything, so the other image parsers
        //  can be selected instead
        return Collections.emptySet();
    }

    private void setEnv(ProcessBuilder pb) {
        Map<String, String> env = pb.environment();

        if (!StringUtils.isBlank(getTessdataPath())) {
            env.put(TESSDATA_PREFIX, getTessdataPath());
        } else if (!StringUtils.isBlank(getTesseractPath())) {
            //adding tessdata is required for at least >= 4.x
            env.put(TESSDATA_PREFIX, getTesseractPath() + "tessdata");
        }
    }

    public boolean hasTesseract() throws TikaConfigException {
        // Fetch where the config says to find Tesseract
        String tesseract = getTesseractPath() + getTesseractProg();

        if (!StringUtils.isBlank(tesseractPath) && !Files.isDirectory(Paths.get(tesseractPath))) {
            throw new TikaConfigException("tesseractPath (" + tesseractPath + ") " +
                    "doesn't point to an existing directory");
        }

        // Try running Tesseract from there, and see if it exists + works
        String[] checkCmd = {tesseract};
        boolean hasTesseract = ExternalParser.check(checkCmd);
        LOG.debug("hasTesseract (path: " + Arrays.toString(checkCmd) + "): " + hasTesseract);
        return hasTesseract;
    }

    boolean hasImageMagick() throws TikaConfigException {
        // Fetch where the config says to find ImageMagick Program
        String fullImageMagickPath = imageMagickPath + getImageMagickProg();

        //check that directory exists
        if (!StringUtils.isBlank(imageMagickPath) &&
                !Files.isDirectory(Paths.get(imageMagickPath))) {
            throw new TikaConfigException("imageMagickPath (" + imageMagickPath + ") " +
                    "doesn't point to an existing directory");
        }

        // Try running ImageMagick program from there, and see if it exists + works
        String[] checkCmd = {fullImageMagickPath};
        boolean hasImageMagick = ExternalParser.check(checkCmd);
        if (!hasImageMagick) {
            LOG.debug("ImageMagick does not appear to be installed " + "(commandline: " +
                    fullImageMagickPath + ")");
        }

        return hasImageMagick;

    }

    public void parse(Image image, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try (TemporaryResources tmp = new TemporaryResources()) {
            int w = image.getWidth(null);
            int h = image.getHeight(null);
            BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            File file = tmp.createTemporaryFile();
            try (OutputStream fos = new FileOutputStream(file)) {
                ImageIO.write(bImage, "png", fos);
            }
            try (TikaInputStream tis = TikaInputStream.get(file)) {
                parse(tis, handler, metadata, context);
            }
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext parseContext) throws IOException, SAXException, TikaException {

        TesseractOCRConfig userConfig = parseContext.get(TesseractOCRConfig.class);
        TesseractOCRConfig config = defaultConfig;
        if (userConfig != null) {
            config = defaultConfig.cloneAndUpdate(userConfig);
        }
        // If Tesseract is not on the path with the current config, do not try to run OCR
        // getSupportedTypes shouldn't have listed us as handling it, so this should only
        //  occur if someone directly calls this parser, not via DefaultParser or similar
        if (!hasTesseract || (config != null && config.isSkipOcr())) {
            return;
        }


        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp, metadata);

            //trigger the spooling to a tmp file if the stream wasn't
            //already a TikaInputStream that contained a file
            tikaStream.getPath();
            //this is the text output file name specified on the tesseract
            //commandline.  The actual output file name will have a suffix added.
            File tmpOCROutputFile = tmp.createTemporaryFile();
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            parse(tikaStream, tmpOCROutputFile, xhtml, metadata, parseContext, config);
            xhtml.endDocument();
        }
    }

    private void parse(TikaInputStream tikaInputStream, File tmpOCROutputFile,
                       ContentHandler xhtml,
                       Metadata metadata, ParseContext parseContext, TesseractOCRConfig config)
            throws IOException, SAXException, TikaException {
        warnOnFirstParse();
        validateLangString(config.getLanguage());

        File tmpTxtOutput = null;
        try {
            Path input = tikaInputStream.getPath();
            long size = tikaInputStream.getLength();

            if (size >= config.getMinFileSizeToOcr() && size <= config.getMaxFileSizeToOcr()) {

                // Process image
                if (config.isEnableImagePreprocessing() || config.isApplyRotation()) {
                    if (!hasImageMagick) {
                        LOG.warn(
                                "User has selected to preprocess images, " +
                                        "but I can't find ImageMagick." +
                                        "Backing off to original file.");
                        doOCR(input.toFile(), tmpOCROutputFile, config, parseContext);
                    } else {
                        // copy the contents of the original input file into a temporary file
                        // which will be preprocessed for OCR

                        try (TemporaryResources tmp = new TemporaryResources()) {
                            Path tmpFile = tmp.createTempFile();
                            Files.copy(input, tmpFile, StandardCopyOption.REPLACE_EXISTING);
                            imagePreprocessor.process(tmpFile, tmpFile, metadata, config);
                            doOCR(tmpFile.toFile(), tmpOCROutputFile, config, parseContext);
                        }
                    }
                } else {
                    doOCR(input.toFile(), tmpOCROutputFile, config, parseContext);
                }

                String extension = config.getPageSegMode().equals("0") ? "osd" :
                        config.getOutputType().toString().toLowerCase(Locale.US);
                // Tesseract appends the output type (.txt or .hocr or .osd) to output file name
                tmpTxtOutput = new File(tmpOCROutputFile.getAbsolutePath() +
                        "." + extension);

                if (tmpTxtOutput.exists()) {
                    try (InputStream is = new FileInputStream(tmpTxtOutput)) {
                        if (config.getPageSegMode().equals("0")) {
                            extractOSD(is, metadata);
                        } else if (config.getOutputType().equals(TesseractOCRConfig.OUTPUT_TYPE.HOCR)) {
                            extractHOCROutput(is, parseContext, xhtml);
                        } else {
                            extractOutput(is, xhtml);
                        }
                    }
                }
            }
        } finally {
            if (tmpTxtOutput != null) {
                tmpTxtOutput.delete();
            }
        }
    }

    private void extractOSD(InputStream is, Metadata metadata) throws IOException {
        Matcher matcher = Pattern.compile("^([^:]+):\\s+(.*)").matcher("");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is,
                UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                if (matcher.reset(line).find()) {
                    String k = matcher.group(1);
                    String v = matcher.group(2);
                    switch (k) {
                        case "Page number":
                            metadata.set(PSM0_PAGE_NUMBER, Integer.parseInt(v));
                            break;
                        case "Orientation in degrees":
                            metadata.set(PSM0_ORIENTATION, Integer.parseInt(v));
                            break;
                        case "Rotate":
                            metadata.set(PSM0_ROTATE, Integer.parseInt(v));
                            break;
                        case "Orientation confidence":
                            metadata.set(PSM0_ORIENTATION_CONFIDENCE, Double.parseDouble(v));
                            break;
                        case "Script":
                            metadata.set(PSM0_SCRIPT, v);
                            break;
                        case "Script confidence":
                            metadata.set(PSM0_SCRIPT_CONFIDENCE, Double.parseDouble(v));
                            break;
                        default:
                            LOG.warn("I regret I don't know how to parse {} with value {}", k, v);
                    }
                }
                line = reader.readLine();
            }
        }
    }

    private void warnOnFirstParse() {
        if (!hasWarned()) {
            warn();
        }
    }

    /**
     * Run external tesseract-ocr process.
     *
     * @param input  File to be ocred
     * @param output File to collect ocr result
     * @param config Configuration of tesseract-ocr engine
     * @throws TikaException if the extraction timed out
     * @throws IOException   if an input error occurred
     */
    private void doOCR(File input, File output, TesseractOCRConfig config, ParseContext parseContext)
            throws IOException, TikaException {

        ArrayList<String> cmd = new ArrayList<>(
                Arrays.asList(getTesseractPath() + getTesseractProg(), input.getPath(),
                        output.getPath(), "--psm", config.getPageSegMode()));
        //if --psm == 0, don't add anything else to the command line
        if (! "0".equals(config.getPageSegMode())) {
            if (!StringUtils.isBlank(config.getLanguage())) {
                cmd.add("-l");
                cmd.add(config.getLanguage());
            }
            for (Map.Entry<String, String> entry : config.getOtherTesseractConfig().entrySet()) {
                cmd.add("-c");
                cmd.add(entry.getKey() + "=" + entry.getValue());
            }
            cmd.addAll(Arrays.asList("-c", "page_separator=" + config.getPageSeparator(), "-c",
                    (config.isPreserveInterwordSpacing()) ? "preserve_interword_spaces=1" :
                            "preserve_interword_spaces=0",
                    config.getOutputType().name().toLowerCase(Locale.US)));
        }
        LOG.debug("Tesseract command: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        setEnv(pb);

        Process process = null;
        String id = null;
        long timeoutMillis = TikaTaskTimeout.getTimeoutMillis(parseContext,
                config.getTimeoutSeconds() * 1000);
        try {
            process = pb.start();
            id = register(process);
            runOCRProcess(process, timeoutMillis);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            if (id != null) {
                release(id);
            }
        }
    }

    private void runOCRProcess(Process process, long timeoutMillis) throws IOException,
            TikaException {
        process.getOutputStream().close();
        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();
        StringBuilder outBuilder = new StringBuilder();
        StringBuilder errBuilder = new StringBuilder();
        Thread outThread = logStream(out, outBuilder);
        Thread errThread = logStream(err, errBuilder);
        outThread.start();
        errThread.start();

        int exitValue = Integer.MIN_VALUE;
        try {
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new TikaException("TesseractOCRParser timeout");
            }
            exitValue = process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TikaException("TesseractOCRParser interrupted", e);
        } catch (IllegalThreadStateException e) {
            //this _should_ never be thrown
            throw new TikaException("TesseractOCRParser timeout");
        }
        if (exitValue > 0) {
            try {
                //make sure this thread is actually done
                errThread.join(1000);
            } catch (InterruptedException e) {
                //swallow
            }
            throw new TikaException(
                    "TesseractOCRParser bad exit value " + exitValue + " err msg: " +
                            errBuilder.toString());
        }

    }

    /**
     * Reads the contents of the given stream and write it to the given XHTML
     * content handler. The stream is closed once fully processed.
     *
     * @param stream Stream where is the result of ocr
     * @param xhtml  XHTML content handler
     * @throws SAXException if the XHTML SAX events could not be handled
     * @throws IOException  if an input error occurred
     */
    private void extractOutput(InputStream stream, ContentHandler xhtml)
            throws SAXException, IOException {
        //        <div class="ocr"
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", "class", "class", "CDATA", "ocr");
        xhtml.startElement(XHTML, "div", "div", attrs);
        try (Reader reader = new InputStreamReader(stream, UTF_8)) {
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                if (n > 0) {
                    xhtml.characters(buffer, 0, n);
                }
            }
        }
        xhtml.endElement(XHTML, "div", "div");
    }

    private void extractHOCROutput(InputStream is, ParseContext parseContext, ContentHandler xhtml)
            throws TikaException, IOException, SAXException {
        if (parseContext == null) {
            parseContext = new ParseContext();
        }

//        <div class="ocr"
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", "class", "class", "CDATA", "ocr");
        xhtml.startElement(XHTML, "div", "div", attrs);
        XMLReaderUtils.parseSAX(is, new HOCRPassThroughHandler(xhtml),
                parseContext);
        xhtml.endElement(XHTML, "div", "div");

    }

    /**
     * Starts a thread that reads the contents of the standard output or error
     * stream of the given process to not block the process. The stream is closed
     * once fully processed.
     */
    private Thread logStream(final InputStream stream, final StringBuilder out) {
        return new Thread(() -> {
            Reader reader = new InputStreamReader(stream, UTF_8);
            char[] buffer = new char[1024];
            try {
                for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                    out.append(buffer, 0, n);
                }
            } catch (IOException e) {
                //swallow
            } finally {
                IOUtils.closeQuietly(stream);
            }

            LOG.debug("{}", out);
        });
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        hasTesseract = hasTesseract();
        hasImageMagick = hasImageMagick();
        if (preloadLangs) {
            preloadLangs();
            if (!StringUtils.isBlank(defaultConfig.getLanguage())) {
                validateLangString(defaultConfig.getLanguage());
            }
        }
        imagePreprocessor = new ImagePreprocessor(getImageMagickPath() + getImageMagickProg());
    }

    private void validateLangString(String language) throws TikaConfigException {
        Set<String> invalidlangs = new HashSet<>();
        Set<String> validLangs = new HashSet<>();
        TesseractOCRConfig.getLangs(language, validLangs, invalidlangs);
        if (invalidlangs.size() > 0) {
            throw new TikaConfigException("Invalid language code(s): " + invalidlangs);
        }
        if (langs.size() > 0) {
            for (String lang : validLangs) {
                if (!langs.contains(lang)) {
                    throw new TikaConfigException(
                            "tesseract does not have " + lang + " available. I see only: " + langs);
                }
            }
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

        if (langs.size() > 0 && !StringUtils.isBlank(defaultConfig.getLanguage())) {
            if (!langs.contains(defaultConfig.getLanguage())) {
                throw new TikaConfigException("It doesn't look like tesseract has lang data for " +
                        defaultConfig.getLanguage() + ". " + "I see only: " + langs);
            }
        }
    }

    public Set<String> getLangs() {
        return langs;
    }

    protected boolean hasWarned() {
        if (HAS_WARNED) {
            return true;
        }
        synchronized (LOCK) {
            if (HAS_WARNED) {
                return true;
            }
            return false;
        }
    }

    protected void warn() {
        LOG.info("Tesseract is installed and is being invoked. " +
                "This can add greatly to processing time.  If you do not want tesseract " +
                "to be applied to your files see: " +
                "https://cwiki.apache.org/confluence/display/TIKA/TikaOCR#TikaOCR-disable-ocr");
        HAS_WARNED = true;
    }

    public String getTesseractPath() {
        return tesseractPath;
    }

    /**
     * Set the path to the Tesseract executable's directory, needed if it is not on system path.
     * <p>
     * Note that if you set this value, it is highly recommended that you also
     * set the path to (and including) the 'tessdata' folder using {@link #setTessdataPath}.
     * </p>
     */
    @Field
    public void setTesseractPath(String tesseractPath) {
        tesseractPath = FilenameUtils.normalize(tesseractPath);
        if (!tesseractPath.isEmpty() && !tesseractPath.endsWith(File.separator)) {
            tesseractPath += File.separator;
        }
        this.tesseractPath = tesseractPath;
    }

    public String getTessdataPath() {
        return this.tessdataPath;
    }

    /**
     * Set the path to the 'tessdata' folder, which contains language files and config files. In
     * some cases (such
     * as on Windows), this folder is found in the Tesseract installation, but in other cases
     * (such as when Tesseract is built from source), it may be located elsewhere.
     * <p/>
     * Make sure to include the 'tessdata' folder in this path: '/blah/de/blah/tessdata'
     */
    @Field
    public void setTessdataPath(String tessdataPath) {
        tessdataPath = FilenameUtils.normalize(tessdataPath);
        if (!tessdataPath.isEmpty() && !tessdataPath.endsWith(File.separator)) {
            tessdataPath += File.separator;
        }

        this.tessdataPath = tessdataPath;
    }

    public String getImageMagickPath() {
        return imageMagickPath;
    }

    /**
     * Set the path to the ImageMagick executable directory, needed if it is not on system path.
     *
     * @param imageMagickPath to ImageMagick executable directory.
     */
    @Field
    public void setImageMagickPath(String imageMagickPath) {
        imageMagickPath = FilenameUtils.normalize(imageMagickPath);
        if (!imageMagickPath.isEmpty() && !imageMagickPath.endsWith(File.separator)) {
            imageMagickPath += File.separator;
        }
        this.imageMagickPath = imageMagickPath;
    }

    @Field
    public void setOtherTesseractSettings(List<String> settings) throws TikaConfigException {
        for (String s : settings) {
            String[] bits = s.trim().split("\\s+");
            if (bits.length != 2) {
                throw new TikaConfigException(
                        "Expected space delimited key value pair." + " However, I found " +
                                bits.length + " bits.");
            }
            defaultConfig.addOtherTesseractConfig(bits[0], bits[1]);
        }
    }

    public List<String> getOtherTesseractSettings() {
        List<String> settings = new ArrayList<>();
        Map<String, String> sorted = new TreeMap<>(defaultConfig.getOtherTesseractConfig());
        for (Map.Entry<String, String> e :sorted.entrySet()) {
            settings.add(e.getKey() + " " + e.getValue());
        }
        return settings;
    }

    @Field
    public void setSkipOCR(boolean skipOCR) {
        defaultConfig.setSkipOcr(skipOCR);
    }

    public boolean isSkipOCR() {
        return defaultConfig.isSkipOcr();
    }

    @Field
    public void setLanguage(String language) {
        defaultConfig.setLanguage(language);
    }

    public String getLanguage() {
        return defaultConfig.getLanguage();
    }

    @Field
    public void setPageSegMode(String pageSegMode) {
        defaultConfig.setPageSegMode(pageSegMode);
    }

    public String getPageSegMode() {
        return defaultConfig.getPageSegMode();
    }
    @Field
    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        defaultConfig.setMaxFileSizeToOcr(maxFileSizeToOcr);
    }

    public long getMaxFileSizeToOcr() {
        return defaultConfig.getMaxFileSizeToOcr();
    }

    @Field
    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        defaultConfig.setMinFileSizeToOcr(minFileSizeToOcr);
    }

    public long getMinFileSizeToOcr() {
        return defaultConfig.getMinFileSizeToOcr();
    }

    /**
     * Set default timeout in seconds.  This can be overridden per parse
     * with {@link TikaTaskTimeout} sent in via the {@link ParseContext}
     * at parse time.
     *
     * @param timeout
     */
    @Field
    public void setTimeout(int timeout) {
        defaultConfig.setTimeoutSeconds(timeout);
    }

    public int getTimeout() {
        return defaultConfig.getTimeoutSeconds();
    }

    @Field
    public void setOutputType(String outputType) {
        defaultConfig.setOutputType(outputType);
    }

    public String getOutputType() {
        return defaultConfig.getOutputType().name();
    }

    @Field
    public void setPreserveInterwordSpacing(boolean preserveInterwordSpacing) {
        defaultConfig.setPreserveInterwordSpacing(preserveInterwordSpacing);
    }

    public boolean isPreserveInterwordSpacing() {
        return defaultConfig.isPreserveInterwordSpacing();
    }

    @Field
    public void setEnableImagePreprocessing(boolean enableImagePreprocessing) {
        defaultConfig.setEnableImagePreprocessing(enableImagePreprocessing);
    }

    public boolean isEnableImagePreprocessing() {
        return defaultConfig.isEnableImagePreprocessing();
    }
    @Field
    public void setDensity(int density) {
        defaultConfig.setDensity(density);
    }

    public int getDensity() {
        return defaultConfig.getDensity();
    }

    @Field
    public void setDepth(int depth) {
        defaultConfig.setDepth(depth);
    }

    public int getDepth() {
        return defaultConfig.getDepth();
    }
    @Field
    public void setColorspace(String colorspace) {
        defaultConfig.setColorspace(colorspace);
    }

    public String getColorspace() {
        return defaultConfig.getColorspace();
    }
    @Field
    public void setFilter(String filter) {
        defaultConfig.setFilter(filter);
    }

    public String getFilter() {
        return defaultConfig.getFilter();
    }

    @Field
    public void setResize(int resize) {
        defaultConfig.setResize(resize);
    }

    public int getResize() {
        return defaultConfig.getResize();
    }

    @Field
    public void setApplyRotation(boolean applyRotation) {
        defaultConfig.setApplyRotation(applyRotation);
    }

    public boolean isApplyRotation() {
        return defaultConfig.isApplyRotation();
    }
    /**
     * If set to <code>true</code> and if tesseract is found, this will load the
     * langs that result from --list-langs. At parse time, the
     * parser will verify that tesseract has the requested lang
     * available.
     * <p>
     * If set to <code>false</code> (the default) and tesseract is found, if a user
     * requests a language that tesseract does not have data for,
     * a TikaException will be thrown with tesseract's native exception
     * message, which is a bit less readable.
     *
     * @param preloadLangs
     */
    @Field
    public void setPreloadLangs(boolean preloadLangs) {
        this.preloadLangs = preloadLangs;
    }

    public boolean isPreloadLangs() {
        return this.preloadLangs;
    }
    public TesseractOCRConfig getDefaultConfig() {
        return defaultConfig;
    }

    private void preloadLangs() {
        String[] args = new String[]{getTesseractPath() + getTesseractProg(), "--list-langs"};

        ProcessBuilder pb = new ProcessBuilder(args);

        setEnv(pb);

        Process process = null;
        try {
            process = pb.start();
            getLangs(process, defaultConfig.getTimeoutSeconds());
        } catch (TikaException | IOException e) {
            LOG.warn("Problem preloading langs", e);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private void getLangs(Process process, int timeoutSeconds) throws IOException, TikaException {
        process.getOutputStream().close();
        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();
        StringBuilder outBuilder = new StringBuilder();
        StringBuilder errBuilder = new StringBuilder();
        Thread outThread = logStream(out, outBuilder);
        Thread errThread = logStream(err, errBuilder);
        outThread.start();
        errThread.start();

        int exitValue = Integer.MIN_VALUE;
        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                throw new TikaException("TesseractOCRParser timeout");
            }
            exitValue = process.exitValue();
            outThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TikaException("TesseractOCRParser interrupted", e);
        } catch (IllegalThreadStateException e) {
            //this _should_ never be thrown
            throw new TikaException("TesseractOCRParser timeout");
        }
        if (exitValue > 0) {
            throw new TikaException(
                    "TesseractOCRParser bad exit value " + exitValue + " err msg: " +
                            errBuilder.toString());
        }
        for (String line : outBuilder.toString().split("[\r\n]+")) {
            if (line.startsWith("List of available")) {
                continue;
            }
            langs.add(line.trim());
        }
    }

    private static class HOCRPassThroughHandler extends DefaultHandler {
        public static final Set<String> IGNORE =
                unmodifiableSet("html", "head", "title", "meta", "body");
        private final ContentHandler xhtml;

        public HOCRPassThroughHandler(ContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        private static Set<String> unmodifiableSet(String... elements) {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(elements)));
        }

        /**
         * Starts the given element. Table cells and list items are automatically
         * indented by emitting a tab character as ignorable whitespace.
         */
        @Override
        public void startElement(String uri, String local, String name, Attributes attributes)
                throws SAXException {
            if (!IGNORE.contains(name)) {
                xhtml.startElement(uri, local, name, attributes);
            }
        }

        /**
         * Ends the given element. Block elements are automatically followed
         * by a newline character.
         */
        @Override
        public void endElement(String uri, String local, String name) throws SAXException {
            if (!IGNORE.contains(name)) {
                xhtml.endElement(uri, local, name);
            }
        }

        /**
         * @see <a href="https://issues.apache.org/jira/browse/TIKA-210">TIKA-210</a>
         */
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            xhtml.characters(ch, start, length);
        }
    }
}

