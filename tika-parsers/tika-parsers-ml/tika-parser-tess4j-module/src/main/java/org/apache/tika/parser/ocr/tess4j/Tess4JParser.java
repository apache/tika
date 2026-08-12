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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.ParseTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ProcessUtils;
import org.apache.tika.utils.StringUtils;

/**
 * OCR parser using <a href="https://github.com/nguyenq/tess4j">Tess4J</a>,
 * which provides a Java JNA wrapper around the native Tesseract library.
 *
 * <p><b>Advanced users only.</b> This parser loads the Tesseract native library
 * directly into the JVM via JNA (Java Native Access). Using it safely requires
 * locating and linking the correct platform-specific native libraries and
 * accepting that a fault in the native code can crash the entire JVM. If you are
 * not comfortable with native-library integration via JNA, please prefer the
 * standard {@code TesseractOCRParser}, which performs the same OCR by running the
 * {@code tesseract} command-line program in a separate process: it needs no
 * native linking and a crash in Tesseract can never take down your application,
 * so it is the recommended choice for almost everyone. Reach for
 * {@code Tess4JParser} only when you have a measured need for in-process OCR
 * throughput <em>and</em> the expertise to operate native bindings safely.
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
        // True only while doOCRWithTimeout's background thread genuinely owns the instance.
        // Starting false (rather than the old pessimistic true) means any exception during
        // setup -- borrow, pixel-count check, image decode -- leaves this false, so the
        // instance is returned promptly in the finally below instead of leaking; that setup
        // work never touches a second thread, so there's nothing to still be "busy".
        boolean tesseractStillBusy = false;
        long requestedMillis = config.getTimeoutMillis();
        long timeoutMillis = ParseTimeout.getOrCreate(parseContext).budgetFor(requestedMillis);
        try {
            tesseract = borrowTesseract(parseContext, timeoutMillis);
            if (tesseract == null) {
                throw new TikaTimeoutException("Timed out waiting for a Tesseract instance from the pool",
                        requestedMillis, timeoutMillis);
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

            // From here on, a timeout from doOCRWithTimeout means the native call is still
            // running on its own background thread -- see that method for how ownership of
            // returning the instance to the pool is handed off in that case.
            tesseractStillBusy = true;
            String ocrResult = doOCRWithTimeout(tesseract, image, requestedMillis, parseContext);
            tesseractStillBusy = false;
            ParseTimeout.checkpoint(parseContext);

            // Emit the text as XHTML
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", "class", "class", "CDATA", "ocr");
            xhtml.startElement(XHTML, "div", "div", attrs);
            if (ocrResult != null && !ocrResult.isEmpty()) {
                xhtml.characters(ocrResult.toCharArray(), 0, ocrResult.length());
            }
            xhtml.endElement(XHTML, "div", "div");

        } catch (TesseractException e) {
            // doOCR itself completed (successfully or not) before we got here -- the
            // instance is idle again, regardless of how doOCRWithTimeout got the exception
            // to us -- so it's ours to return.
            tesseractStillBusy = false;
            throw new TikaException("Tess4J OCR failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TikaException("Interrupted while waiting for Tesseract instance", e);
        } finally {
            if (tesseract != null && !tesseractStillBusy) {
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
     * Borrows a {@link Tesseract} instance from the pool, waiting up to the specified
     * timeout. Polls in {@link ProcessUtils#HEARTBEAT_INTERVAL_MILLIS} increments,
     * checkpointing {@code parseContext}'s {@link ParseTimeout} between polls, so a busy
     * pool doesn't trip the stall detector while a worker is still legitimately in use.
     *
     * @param parseContext  may be null, in which case no checkpoint is recorded
     * @param timeoutMillis maximum time to wait in milliseconds
     * @return a Tesseract instance, or null if the timeout elapsed
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    private Tesseract borrowTesseract(ParseContext parseContext, long timeoutMillis)
            throws InterruptedException {
        long startNanos = System.nanoTime();
        while (true) {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            long remaining = timeoutMillis - elapsedMillis;
            long pollMillis = remaining <= 0 ? 0 :
                    Math.min(remaining, ProcessUtils.HEARTBEAT_INTERVAL_MILLIS);
            Tesseract tesseract = pool.poll(pollMillis, TimeUnit.MILLISECONDS);
            if (tesseract != null) {
                return tesseract;
            }
            if (remaining <= 0) {
                return null;
            }
            ParseTimeout.checkpoint(parseContext);
        }
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
     * Runs {@code tesseract.doOCR(image)} on a background thread and waits for it,
     * checkpointing {@link ParseTimeout} every {@link ProcessUtils#HEARTBEAT_INTERVAL_MILLIS}
     * -- mirrors {@link #borrowTesseract}'s own bounded wait. Without this, doOCR (a
     * synchronous native call that can run for the full duration of a large/complex
     * image) never checkpoints, so the server's progress-stall watchdog kills the whole
     * forked JVM on any OCR call slower than {@code progressTimeoutMillis} -- the exact
     * failure mode this timeout model exists to prevent.
     * <p>
     * The native call itself cannot be cancelled once started (JNA/native calls don't
     * respond to {@link Thread#interrupt()}): on timeout or interrupt, this method gives
     * up waiting and throws, but the background thread -- and the {@code tesseract}
     * instance it's still using -- keeps running until the native call eventually returns
     * on its own. {@code settled} arbitrates who returns {@code tesseract} to the pool in
     * that case: both this method (on giving up) and the worker thread (on finishing)
     * race to flip it from {@code false} to {@code true}; whichever one loses the race --
     * i.e. finds it already {@code true} -- is the second to arrive and does the
     * returning, so the instance goes back exactly once no matter how close the timing is.
     * If this method returns normally, or throws {@link TesseractException}, the worker
     * finished before either side touched {@code settled} (this method never gave up), so
     * the caller retains ownership and returns {@code tesseract} itself, same as before
     * this method was ever called.
     */
    private String doOCRWithTimeout(Tesseract tesseract, BufferedImage image, long requestedMillis,
                                    ParseContext parseContext)
            throws TesseractException, TikaTimeoutException {
        // Re-budget here rather than reusing the caller's borrow budget: time spent
        // waiting for the pool and decoding the image has already been burned.
        long budgetMillis = ParseTimeout.getOrCreate(parseContext).budgetFor(requestedMillis);
        if (budgetMillis <= 0) {
            // Nothing async ever starts here, but the caller already flipped
            // tesseractStillBusy to true before calling us, so it won't return the
            // instance itself -- do it here or it's stuck in limbo forever.
            returnTesseract(tesseract);
            throw new TikaTimeoutException("Tesseract OCR call not attempted", requestedMillis,
                    budgetMillis);
        }

        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean settled = new AtomicBoolean(false);
        Thread ocrThread = new Thread(() -> {
            try {
                result.set(tesseract.doOCR(image));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
                if (!settled.compareAndSet(false, true)) {
                    // The waiter already gave up and is not coming back for this instance.
                    returnTesseract(tesseract);
                }
            }
        }, "tess4j-ocr-worker");
        ocrThread.setDaemon(true);
        ocrThread.start();

        long startNanos = System.nanoTime();
        while (true) {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            long remaining = budgetMillis - elapsedMillis;
            long waitMillis = remaining <= 0 ? 0 :
                    Math.min(remaining, ProcessUtils.HEARTBEAT_INTERVAL_MILLIS);
            boolean finished;
            try {
                finished = done.await(waitMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!settled.compareAndSet(false, true)) {
                    returnTesseract(tesseract);
                }
                throw new TikaTimeoutException("interrupted while waiting for Tesseract OCR",
                        requestedMillis, budgetMillis);
            }
            if (finished) {
                break;
            }
            if (remaining <= 0) {
                if (!settled.compareAndSet(false, true)) {
                    returnTesseract(tesseract);
                }
                throw new TikaTimeoutException("Tesseract OCR call timed out", requestedMillis,
                        budgetMillis);
            }
            ParseTimeout.checkpoint(parseContext);
        }

        Throwable t = failure.get();
        if (t == null) {
            return result.get();
        }
        if (t instanceof TesseractException te) {
            throw te;
        }
        throw new TesseractException(t);
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

    public long getTimeoutMillis() {
        return defaultConfig.getTimeoutMillis();
    }

    public void setTimeoutMillis(long timeoutMillis) {
        defaultConfig.setTimeoutMillis(timeoutMillis);
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
