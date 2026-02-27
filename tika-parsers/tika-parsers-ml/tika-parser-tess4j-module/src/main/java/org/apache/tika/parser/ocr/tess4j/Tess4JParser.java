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

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.config.TikaProgressTracker;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/**
 * OCR parser using <a href="https://github.com/nguyenq/tess4j">Tess4J</a>,
 * which provides a Java JNA wrapper around the native Tesseract library.
 * <p>
 * Unlike the command-line {@code TesseractOCRParser}, this parser calls Tesseract
 * in-process via JNA, eliminating the per-file process-spawn overhead.
 * <p>
 * Because the native Tesseract handle is <b>not thread-safe</b>, this parser
 * maintains a configurable pool of {@link Tesseract} instances.  The pool size
 * is controlled by {@link Tess4JConfig#setPoolSize(int)}.
 * <p>
 * Configuration key: {@code "tess4j-parser"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "tess4j-parser")
public class Tess4JParser implements Parser, Initializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(Tess4JParser.class);

    private static final String OCR = "ocr-";

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    MediaType.image(OCR + "png"),
                    MediaType.image(OCR + "jpeg"),
                    MediaType.image(OCR + "tiff"),
                    MediaType.image(OCR + "bmp"),
                    MediaType.image(OCR + "gif"),
                    MediaType.image("jp2"),
                    MediaType.image("jpx"),
                    MediaType.image("x-portable-pixmap"),
                    MediaType.image(OCR + "jp2"),
                    MediaType.image(OCR + "jpx"),
                    MediaType.image(OCR + "x-portable-pixmap")
            )));

    private static volatile boolean HAS_WARNED = false;
    private static final Object[] LOCK = new Object[0];

    private Tess4JConfig defaultConfig;
    private transient BlockingQueue<Tesseract> pool;
    private volatile boolean initialized = false;

    public Tess4JParser() throws TikaConfigException {
        this.defaultConfig = new Tess4JConfig();
        initialize();
    }

    public Tess4JParser(Tess4JConfig config) throws TikaConfigException {
        this.defaultConfig = config;
        initialize();
    }

    public Tess4JParser(JsonConfig jsonConfig) throws TikaConfigException {
        this(ConfigDeserializer.buildConfig(jsonConfig, Tess4JConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        if (!initialized) {
            return Collections.emptySet();
        }
        Tess4JConfig config = context.get(Tess4JConfig.class);
        if (config != null && config.isSkipOcr()) {
            return Collections.emptySet();
        }
        if (defaultConfig.isSkipOcr()) {
            return Collections.emptySet();
        }
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext parseContext)
            throws IOException, SAXException, TikaException {

        Tess4JConfig config = getConfig(parseContext);

        if (!initialized || config.isSkipOcr()) {
            return;
        }

        warnOnFirstParse();

        long size = tis.getLength();
        if (size >= 0 && (size < config.getMinFileSizeToOcr() ||
                size > config.getMaxFileSizeToOcr())) {
            return;
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, parseContext);
        xhtml.startDocument();

        Tesseract tesseract = null;
        long timeoutMillis = TimeoutLimits.getProcessTimeoutMillis(
                parseContext, config.getTimeoutSeconds() * 1000L);
        try {
            tesseract = borrowTesseract(timeoutMillis);
            if (tesseract == null) {
                throw new TikaException("Timed out waiting for a Tesseract instance from the pool");
            }

            // Apply per-request config if different from defaults
            applyConfig(tesseract, config);

            // Check image dimensions before full decode to prevent OOM
            long maxPixels = config.getMaxImagePixels();
            if (maxPixels > 0) {
                tis.mark((int) Math.min(tis.getLength() + 1, 1024 * 1024));
                try {
                    long pixels = getImagePixels(tis);
                    if (pixels > maxPixels) {
                        LOG.warn("Image has {} pixels, exceeding maxImagePixels={}. "
                                + "Skipping OCR.", pixels, maxPixels);
                        xhtml.endDocument();
                        return;
                    }
                } finally {
                    tis.reset();
                }
            }

            BufferedImage image = readImage(tis);
            if (image == null) {
                LOG.warn("Could not read image from stream");
                xhtml.endDocument();
                return;
            }

            String ocrResult = tesseract.doOCR(image);
            TikaProgressTracker.update(parseContext);

            // Emit the text as XHTML
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", "class", "class", "CDATA", "ocr");
            xhtml.startElement(XHTML, "div", "div", attrs);
            if (ocrResult != null && !ocrResult.isEmpty()) {
                xhtml.characters(ocrResult.toCharArray(), 0, ocrResult.length());
            }
            xhtml.endElement(XHTML, "div", "div");

        } catch (TesseractException e) {
            throw new TikaException("Tess4J OCR failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TikaException("Interrupted while waiting for Tesseract instance", e);
        } finally {
            if (tesseract != null) {
                returnTesseract(tesseract);
            }
        }

        xhtml.endDocument();
    }

    @Override
    public void initialize() throws TikaConfigException {
        if (defaultConfig.isSkipOcr()) {
            initialized = false;
            return;
        }
        try {
            configureNativeLibPath();
            initPool();
            initialized = true;
            LOG.info("Tess4J parser initialized with pool size {}", defaultConfig.getPoolSize());
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            LOG.warn("Tess4J native library not available: {}. " +
                    "Tess4JParser will be disabled.", e.getMessage());
            initialized = false;
        } catch (Exception e) {
            LOG.warn("Failed to initialize Tess4J: {}. " +
                    "Tess4JParser will be disabled.", e.getMessage());
            initialized = false;
        }
    }

    /**
     * If a native library path is configured, prepend it to the JNA library search path
     * so that JNA can find libtesseract and libleptonica on non-Windows platforms.
     */
    private void configureNativeLibPath() {
        String nativeLibPath = defaultConfig.getNativeLibPath();
        if (!StringUtils.isBlank(nativeLibPath)) {
            String existing = System.getProperty("jna.library.path", "");
            if (existing.isEmpty()) {
                System.setProperty("jna.library.path", nativeLibPath);
            } else if (!existing.contains(nativeLibPath)) {
                System.setProperty("jna.library.path",
                        nativeLibPath + System.getProperty("path.separator") + existing);
            }
            LOG.debug("jna.library.path set to: {}", System.getProperty("jna.library.path"));
        }
    }

    /**
     * Creates the pool of {@link Tesseract} instances based on the default config.
     */
    private void initPool() {
        int size = defaultConfig.getPoolSize();
        pool = new ArrayBlockingQueue<>(size);
        for (int i = 0; i < size; i++) {
            Tesseract tesseract = createTesseract(defaultConfig);
            pool.add(tesseract);
        }
        // Tess4J loads the native library lazily on first doOCR call.
        // Force it now so UnsatisfiedLinkError is caught by initialize().
        Tesseract probe = pool.peek();
        if (probe != null) {
            try {
                BufferedImage tiny = new BufferedImage(1, 1,
                        BufferedImage.TYPE_BYTE_GRAY);
                probe.doOCR(tiny);
            } catch (TesseractException e) {
                // Expected — OCR on a 1x1 image may fail,
                // but the native library loaded successfully
            }
        }
    }

    /**
     * Creates and configures a new {@link Tesseract} instance.
     */
    private Tesseract createTesseract(Tess4JConfig config) {
        Tesseract tesseract = new Tesseract();
        applyConfig(tesseract, config);
        return tesseract;
    }

    /**
     * Applies the given configuration to a {@link Tesseract} instance.
     */
    private void applyConfig(Tesseract tesseract, Tess4JConfig config) {
        if (!StringUtils.isBlank(config.getDataPath())) {
            tesseract.setDatapath(config.getDataPath());
        }
        tesseract.setLanguage(config.getLanguage());
        tesseract.setPageSegMode(config.getPageSegMode());
        tesseract.setOcrEngineMode(config.getOcrEngineMode());
    }

    /**
     * Borrows a {@link Tesseract} instance from the pool, waiting up to the
     * specified timeout.
     *
     * @param timeoutMillis maximum time to wait in milliseconds
     * @return a Tesseract instance, or null if the timeout elapsed
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    private Tesseract borrowTesseract(long timeoutMillis) throws InterruptedException {
        return pool.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns a {@link Tesseract} instance to the pool.
     */
    private void returnTesseract(Tesseract tesseract) {
        if (!pool.offer(tesseract)) {
            // pool is full (shouldn't happen in normal operation) - just discard
            LOG.warn("Tesseract pool is full; discarding instance");
        }
    }

    /**
     * Reads a {@link BufferedImage} from the input stream.
     */
    private BufferedImage readImage(InputStream is) throws IOException {
        return ImageIO.read(is);
    }

    /**
     * Reads only the image header to determine width &times; height
     * without decoding the full raster. Returns {@code -1} if dimensions
     * cannot be determined.
     */
    private long getImagePixels(InputStream is) throws IOException {
        try (javax.imageio.stream.ImageInputStream iis =
                     ImageIO.createImageInputStream(is)) {
            if (iis == null) {
                return -1;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers =
                    ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return -1;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                return w * h;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Resolves the effective config: JSON config > ParseContext config > default.
     */
    private Tess4JConfig getConfig(ParseContext parseContext)
            throws TikaConfigException, IOException {

        if (parseContext.hasJsonConfig("tess4j-parser")) {
            // Validate no paths in runtime config
            Tess4JConfig.RuntimeConfig runtimeConfig = ParseContextConfig.getConfig(
                    parseContext,
                    "tess4j-parser",
                    Tess4JConfig.RuntimeConfig.class,
                    new Tess4JConfig.RuntimeConfig());

            if (runtimeConfig.isSkipOcr()) {
                return runtimeConfig;
            }

            return ParseContextConfig.getConfig(
                    parseContext,
                    "tess4j-parser",
                    Tess4JConfig.class,
                    defaultConfig);
        }

        Tess4JConfig userConfig = parseContext.get(Tess4JConfig.class);
        if (userConfig != null) {
            return userConfig;
        }
        return defaultConfig;
    }

    private void warnOnFirstParse() {
        if (!HAS_WARNED) {
            synchronized (LOCK) {
                if (!HAS_WARNED) {
                    LOG.info("Tess4J OCR is being invoked. " +
                            "This can add greatly to processing time. " +
                            "If you do not want OCR to be applied to your files, " +
                            "configure skipOcr=true.");
                    HAS_WARNED = true;
                }
            }
        }
    }

    // -- Delegating getters/setters for parser-level configuration --

    public String getLanguage() {
        return defaultConfig.getLanguage();
    }

    public void setLanguage(String language) {
        defaultConfig.setLanguage(language);
    }

    public String getDataPath() {
        return defaultConfig.getDataPath();
    }

    public void setDataPath(String dataPath) throws TikaConfigException {
        defaultConfig.setDataPath(dataPath);
    }

    public int getPageSegMode() {
        return defaultConfig.getPageSegMode();
    }

    public void setPageSegMode(int pageSegMode) {
        defaultConfig.setPageSegMode(pageSegMode);
    }

    public int getOcrEngineMode() {
        return defaultConfig.getOcrEngineMode();
    }

    public void setOcrEngineMode(int ocrEngineMode) {
        defaultConfig.setOcrEngineMode(ocrEngineMode);
    }

    public long getMaxFileSizeToOcr() {
        return defaultConfig.getMaxFileSizeToOcr();
    }

    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        defaultConfig.setMaxFileSizeToOcr(maxFileSizeToOcr);
    }

    public long getMinFileSizeToOcr() {
        return defaultConfig.getMinFileSizeToOcr();
    }

    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        defaultConfig.setMinFileSizeToOcr(minFileSizeToOcr);
    }

    public int getPoolSize() {
        return defaultConfig.getPoolSize();
    }

    public void setPoolSize(int poolSize) {
        defaultConfig.setPoolSize(poolSize);
    }

    public int getTimeoutSeconds() {
        return defaultConfig.getTimeoutSeconds();
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        defaultConfig.setTimeoutSeconds(timeoutSeconds);
    }

    public boolean isSkipOcr() {
        return defaultConfig.isSkipOcr();
    }

    public void setSkipOcr(boolean skipOcr) {
        defaultConfig.setSkipOcr(skipOcr);
    }

    public int getDpi() {
        return defaultConfig.getDpi();
    }

    public void setDpi(int dpi) {
        defaultConfig.setDpi(dpi);
    }

    public String getNativeLibPath() {
        return defaultConfig.getNativeLibPath();
    }

    public void setNativeLibPath(String nativeLibPath) throws TikaConfigException {
        defaultConfig.setNativeLibPath(nativeLibPath);
    }

    public long getMaxImagePixels() {
        return defaultConfig.getMaxImagePixels();
    }

    public void setMaxImagePixels(long maxImagePixels) {
        defaultConfig.setMaxImagePixels(maxImagePixels);
    }

    /**
     * Returns whether the parser has been successfully initialized
     * (i.e., Tess4J native library is available).
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the default configuration. Visible for testing.
     */
    Tess4JConfig getDefaultConfig() {
        return defaultConfig;
    }
}
