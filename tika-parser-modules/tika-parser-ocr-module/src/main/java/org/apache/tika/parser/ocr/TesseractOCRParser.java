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

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.parser.image.JpegParser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;

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
 *
 *
 */
public class TesseractOCRParser extends AbstractParser implements Initializable {
    private static final Logger LOG = LoggerFactory.getLogger(TesseractOCRParser.class);

    private static volatile boolean HAS_WARNED = false;
    private static final Object[] LOCK = new Object[0];


    private static final long serialVersionUID = -8167538283213097265L;
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new MediaType[]{
                    MediaType.image("png"), MediaType.image("jpeg"), MediaType.image("tiff"),
                    MediaType.image("bmp"), MediaType.image("gif"), MediaType.image("jp2"),
                    MediaType.image("jpx"), MediaType.image("x-portable-pixmap")
            })));
    private final TesseractOCRConfig defaultConfig = new TesseractOCRConfig();

    private static Map<String,Boolean> TESSERACT_PRESENT = new HashMap<>();
    private static Map<String,Boolean> IMAGE_MAGICK_PRESENT = new HashMap<>();


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        // If Tesseract is installed, offer our supported image types
        TesseractOCRConfig config = context.get(TesseractOCRConfig.class, defaultConfig);
        if (hasTesseract(config)) {
            return SUPPORTED_TYPES;
        }
        // Otherwise don't advertise anything, so the other image parsers
        //  can be selected instead
        return Collections.emptySet();
    }

    private void setEnv(TesseractOCRConfig config, ProcessBuilder pb) {
        String tessdataPrefix = "TESSDATA_PREFIX";
        Map<String, String> env = pb.environment();

        if (!config.getTessdataPath().isEmpty()) {
            env.put(tessdataPrefix, config.getTessdataPath());
        }
        else if(!config.getTesseractPath().isEmpty()) {
            env.put(tessdataPrefix, config.getTesseractPath());
        }
    }

    public boolean hasTesseract(TesseractOCRConfig config) {
        // Fetch where the config says to find Tesseract
        String tesseract = config.getTesseractPath() + getTesseractProg();

        // Have we already checked for a copy of Tesseract there?
        if (TESSERACT_PRESENT.containsKey(tesseract)) {
            return TESSERACT_PRESENT.get(tesseract);
        }
        //prevent memory bloat
        if (TESSERACT_PRESENT.size() > 100) {
            TESSERACT_PRESENT.clear();
        }
        //check that the parent directory exists
        if (! config.getTesseractPath().isEmpty() &&
                ! Files.isDirectory(Paths.get(config.getTesseractPath()))) {
            TESSERACT_PRESENT.put(tesseract, false);
            return false;
        }

        // Try running Tesseract from there, and see if it exists + works
        String[] checkCmd = { tesseract };
        boolean hasTesseract = ExternalParser.check(checkCmd);
        TESSERACT_PRESENT.put(tesseract, hasTesseract);
        return hasTesseract;
     
    }
    
    private boolean hasImageMagick(TesseractOCRConfig config) {
        // Fetch where the config says to find ImageMagick Program
        String ImageMagick = getImageMagickPath(config);

        // Have we already checked for a copy of ImageMagick Program there?
        if (IMAGE_MAGICK_PRESENT.containsKey(ImageMagick)) {
            return IMAGE_MAGICK_PRESENT.get(ImageMagick);
        }
        //prevent memory bloat
        if (IMAGE_MAGICK_PRESENT.size() > 100) {
            IMAGE_MAGICK_PRESENT.clear();
        }
        //check that directory exists
        if (!config.getImageMagickPath().isEmpty() &&
                ! Files.isDirectory(Paths.get(config.getImageMagickPath()))) {
            IMAGE_MAGICK_PRESENT.put(ImageMagick, false);
            return false;
        }
        if (SystemUtils.IS_OS_WINDOWS && config.getImageMagickPath().isEmpty()) {
            LOG.warn("Must specify path for imagemagick on Windows OS to avoid accidental confusion with convert.exe");
            IMAGE_MAGICK_PRESENT.put(ImageMagick, false);
            return false;
        }

        // Try running ImageMagick program from there, and see if it exists + works
        String[] checkCmd = { ImageMagick };
        boolean hasImageMagick = ExternalParser.check(checkCmd);
        IMAGE_MAGICK_PRESENT.put(ImageMagick, hasImageMagick);
        
        return hasImageMagick;
     
    }

    private String getImageMagickPath(TesseractOCRConfig config) {
        return config.getImageMagickPath() + getImageMagickProg();
    }

    public static boolean hasPython() {
        // check if python is installed and it has the required dependencies for the rotation program to run
        boolean hasPython = false;
        TemporaryResources tmp = null;
        try {
            tmp = new TemporaryResources();
            File importCheck = tmp.createTemporaryFile();
            String prg = "import numpy, matplotlib, skimage, _tkinter";
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(importCheck), Charset.forName("UTF-8"));
            out.write(prg);
            out.close();

            Process p = Runtime.getRuntime().exec("python " + importCheck.getAbsolutePath());
            if (p.waitFor() == 0) {
                hasPython = true;
            }


        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //swallow
        } finally {
            IOUtils.closeQuietly(tmp);
        }

        return hasPython;
    }

    public void parse(Image image, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        try {
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
        } finally {
            tmp.dispose();
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext parseContext)
            throws IOException, SAXException, TikaException {
        TesseractOCRConfig config = parseContext.get(TesseractOCRConfig.class, defaultConfig);
        // If Tesseract is not on the path with the current config, do not try to run OCR
        // getSupportedTypes shouldn't have listed us as handling it, so this should only
        //  occur if someone directly calls this parser, not via DefaultParser or similar
        if (! hasTesseract(config))
            return;

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);

            //trigger the spooling to a tmp file if the stream wasn't
            //already a TikaInputStream that contained a file
            tikaStream.getPath();
            //this is the text output file name specified on the tesseract
            //commandline.  The actual output file name will have a suffix added.
            File tmpOCROutputFile = tmp.createTemporaryFile();

            // Temporary workaround for TIKA-1445 - until we can specify
            //  composite parsers with strategies (eg Composite, Try In Turn),
            //  always send the image onwards to the regular parser to have
            //  the metadata for them extracted as well
            _TMP_IMAGE_METADATA_PARSER.parse(tikaStream, new DefaultHandler(), metadata, parseContext);

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            parse(tikaStream, tmpOCROutputFile, parseContext, xhtml, config);
            xhtml.endDocument();
        } finally {
            tmp.dispose();
        }
    }

    /**
     * Use this to parse content without starting a new document.
     * This appends SAX events to xhtml without re-adding the metadata, body start, etc.
     *
     * @param stream inputstream
     * @param xhtml handler
     * @param config TesseractOCRConfig to use for this parse
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     *
     * @deprecated use {@link #parseInline(InputStream, XHTMLContentHandler, ParseContext, TesseractOCRConfig)}
     */
    public void parseInline(InputStream stream, XHTMLContentHandler xhtml, TesseractOCRConfig config)
            throws IOException, SAXException, TikaException {
        parseInline(stream, xhtml, new ParseContext(), config);
    }

    /**
     * Use this to parse content without starting a new document.
     * This appends SAX events to xhtml without re-adding the metadata, body start, etc.
     *
     * @param stream inputstream
     * @param xhtml handler
     * @param config TesseractOCRConfig to use for this parse
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     *
     */
    public void parseInline(InputStream stream, XHTMLContentHandler xhtml, ParseContext parseContext,
                            TesseractOCRConfig config)
            throws IOException, SAXException, TikaException {
        // If Tesseract is not on the path with the current config, do not try to run OCR
        // getSupportedTypes shouldn't have listed us as handling it, so this should only
        //  occur if someone directly calls this parser, not via DefaultParser or similar
        if (! hasTesseract(config))
            return;

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
            File tmpImgFile = tmp.createTemporaryFile();
            parse(tikaStream, tmpImgFile, parseContext, xhtml, config);
        } finally {
            tmp.dispose();
        }
    }

    /**
     * This method is used to process the image to an OCR-friendly format.
     * @param scratchFile input image to be processed
     * @param config TesseractOCRconfig class to get ImageMagick properties
     * @throws IOException if an input error occurred
     * @throws TikaException if an exception timed out
     */
    private void processImage(File scratchFile, TesseractOCRConfig config) throws IOException, TikaException {
    	
    	// fetch rotation script from resources
    	InputStream in = getClass().getResourceAsStream("rotation.py");
    	TemporaryResources tmp = new TemporaryResources();
    	File rotationScript = tmp.createTemporaryFile();
    	Files.copy(in, rotationScript.toPath(), StandardCopyOption.REPLACE_EXISTING);

    	CommandLine commandLine = new CommandLine("python");
    	String[] args = {"-W",
                "ignore",
                rotationScript.getAbsolutePath(),
                "-f",
                scratchFile.getAbsolutePath()};
    	commandLine.addArguments(args, true);
    	String angle = "0"; 
    			
    	DefaultExecutor executor = new DefaultExecutor();
    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    	PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        
        // determine the angle of rotation required to make the text horizontal
        if(config.getApplyRotation() && hasPython()) {
            try {
                executor.execute(commandLine);
                String tmpAngle = outputStream.toString("UTF-8").trim();
                //verify that you've gotten a numeric value out
                Double.parseDouble(tmpAngle);
                angle = tmpAngle;
            } catch(Exception e) {	

            }
        }
              
        // process the image - parameter values can be set in TesseractOCRConfig.properties
        commandLine = new CommandLine(getImageMagickPath(config));
        args = new String[]{
                "-density", Integer.toString(config.getDensity()),
                "-depth ", Integer.toString(config.getDepth()),
                "-colorspace", config.getColorspace(),
                "-filter", config.getFilter(),
                "-resize", config.getResize() + "%",
                "-rotate", angle,
                scratchFile.getAbsolutePath(),
                scratchFile.getAbsolutePath()
        };
        commandLine.addArguments(args, true);
		try {
			executor.execute(commandLine);
		} catch(Exception e) {	

		} 
       
        tmp.close();
    }
    
    private void parse(TikaInputStream tikaInputStream, File tmpOCROutputFile, ParseContext parseContext,
                       XHTMLContentHandler xhtml, TesseractOCRConfig config)
            throws IOException, SAXException, TikaException {
        File tmpTxtOutput = null;
        try {
            File input = tikaInputStream.getFile();
            long size = tikaInputStream.getLength();

            if (size >= config.getMinFileSizeToOcr() && size <= config.getMaxFileSizeToOcr()) {

            	// Process image if ImageMagick Tool is present
            	if(config.isEnableImageProcessing() == 1 && hasImageMagick(config)) {
                    // copy the contents of the original input file into a temporary file
                    // which will be preprocessed for OCR
                    TemporaryResources tmp = new TemporaryResources();
                    try {
                        File tmpFile = tmp.createTemporaryFile();
                        FileUtils.copyFile(input, tmpFile);
                        processImage(tmpFile, config);
                        doOCR(tmpFile, tmpOCROutputFile, config);
                    } finally {
                        if (tmp != null) {
                            tmp.dispose();
                        }
                    }
            	} else {
                    doOCR(input, tmpOCROutputFile, config);
                }

                // Tesseract appends the output type (.txt or .hocr) to output file name
                tmpTxtOutput = new File(tmpOCROutputFile.getAbsolutePath() + "." +
                        config.getOutputType().toString().toLowerCase(Locale.US));

                if (tmpTxtOutput.exists()) {
                    try (InputStream is = new FileInputStream(tmpTxtOutput)) {
                        if (config.getOutputType().equals(TesseractOCRConfig.OUTPUT_TYPE.HOCR)) {
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

    /**
     * no-op
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        //this will incorrectly trigger for people who turn off Tesseract
        //by sending in a bogus tesseract path via a custom TesseractOCRConfig.
        //TODO: figure out how to solve that.
        if (! hasWarned()) {
            if (hasTesseract(defaultConfig)) {
                problemHandler.handleInitializableProblem(this.getClass().getName(),
                        "Tesseract OCR is installed and will be automatically applied to image files unless\n" +
                                "you've excluded the TesseractOCRParser from the default parser.\n"+
                                "Tesseract may dramatically slow down content extraction (TIKA-2359).\n" +
                                "As of Tika 1.15 (and prior versions), Tesseract is automatically called.\n" +
                                "In future versions of Tika, users may need to turn the TesseractOCRParser on via TikaConfig.");
                warn();
            }
        }
    }
    // TIKA-1445 workaround parser
    private static Parser _TMP_IMAGE_METADATA_PARSER = new CompositeImageParser();



    private static class CompositeImageParser extends CompositeParser {
        private static final long serialVersionUID = -2398203346206381382L;
        private static List<Parser> imageParsers = Arrays.asList(new Parser[]{
                new ImageParser(), new JpegParser(), new TiffParser()
        });
        CompositeImageParser() {
            super(new MediaTypeRegistry(), imageParsers);
        }
    }

    /**
     * Run external tesseract-ocr process.
     *
     * @param input
     *          File to be ocred
     * @param output
     *          File to collect ocr result
     * @param config
     *          Configuration of tesseract-ocr engine
     * @throws TikaException
     *           if the extraction timed out
     * @throws IOException
     *           if an input error occurred
     */
    private void doOCR(File input, File output, TesseractOCRConfig config) throws IOException, TikaException {
        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(
                config.getTesseractPath() + getTesseractProg(), input.getPath(),  output.getPath(), "-l",
                config.getLanguage(), "--psm", config.getPageSegMode()
        ));
        for (Map.Entry<String, String> entry : config.getOtherTesseractConfig().entrySet()) {
            cmd.add("-c");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }
        cmd.addAll(Arrays.asList(
                "-c", "page_separator=" + config.getPageSeparator(),
                "-c",
                (config.getPreserveInterwordSpacing())? "preserve_interword_spaces=1" : "preserve_interword_spaces=0",
                config.getOutputType().name().toLowerCase(Locale.US)
        ));
        LOG.debug("Tesseract command: " + String.join(" ", cmd));
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        setEnv(config, pb);
        final Process process = pb.start();

        process.getOutputStream().close();
        InputStream out = process.getInputStream();
        InputStream err = process.getErrorStream();

        logStream("OCR MSG", out, input);
        logStream("OCR ERROR", err, input);

        FutureTask<Integer> waitTask = new FutureTask<>(new Callable<Integer>() {
            public Integer call() throws Exception {
                return process.waitFor();
            }
        });

        Thread waitThread = new Thread(waitTask);
        waitThread.start();

        try {
            waitTask.get(config.getTimeout(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            waitThread.interrupt();
            process.destroy();
            Thread.currentThread().interrupt();
            throw new TikaException("TesseractOCRParser interrupted", e);
        } catch (ExecutionException e) {
            // should not be thrown
        } catch (TimeoutException e) {
            waitThread.interrupt();
            process.destroy();
            throw new TikaException("TesseractOCRParser timeout", e);
        }
    }

    /**
     * Reads the contents of the given stream and write it to the given XHTML
     * content handler. The stream is closed once fully processed.
     *
     * @param stream
     *          Stream where is the result of ocr
     * @param xhtml
     *          XHTML content handler
     * @throws SAXException
     *           if the XHTML SAX events could not be handled
     * @throws IOException
     *           if an input error occurred
     */
    private void extractOutput(InputStream stream, XHTMLContentHandler xhtml) throws SAXException, IOException {
        xhtml.startElement("div", "class", "ocr");
        try (Reader reader = new InputStreamReader(stream, UTF_8)) {
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                if (n > 0) {
                    xhtml.characters(buffer, 0, n);
                }
            }
        }
        xhtml.endElement("div");
    }

    private void extractHOCROutput(InputStream is, ParseContext parseContext,
                                   XHTMLContentHandler xhtml) throws TikaException, IOException, SAXException {
        if (parseContext == null) {
            parseContext = new ParseContext();
        }

        xhtml.startElement("div", "class", "ocr");
        XMLReaderUtils.parseSAX(is, new OfflineContentHandler(new HOCRPassThroughHandler(xhtml)), parseContext);
        xhtml.endElement("div");

    }

    /**
     * Starts a thread that reads the contents of the standard output or error
     * stream of the given process to not block the process. The stream is closed
     * once fully processed.
     */
    private void logStream(final String logType, final InputStream stream, final File file) {
        new Thread() {
            public void run() {
                Reader reader = new InputStreamReader(stream, UTF_8);
                StringBuilder out = new StringBuilder();
                char[] buffer = new char[1024];
                try {
                    for (int n = reader.read(buffer); n != -1; n = reader.read(buffer))
                        out.append(buffer, 0, n);
                } catch (IOException e) {

                } finally {
                    IOUtils.closeQuietly(stream);
                }

                LOG.debug("{}", out);
            }
        }.start();
    }

    public static String getTesseractProg() {
        return System.getProperty("os.name").startsWith("Windows") ? "tesseract.exe" : "tesseract";
    }

    public static String getImageMagickProg() {
    	return System.getProperty("os.name").startsWith("Windows") ? "convert.exe" : "convert";
    }


    private static class HOCRPassThroughHandler extends DefaultHandler {
        private final ContentHandler xhtml;
        public static final Set<String> IGNORE = unmodifiableSet(
                "html", "head", "title", "meta", "body");

        public HOCRPassThroughHandler(ContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        /**
         * Starts the given element. Table cells and list items are automatically
         * indented by emitting a tab character as ignorable whitespace.
         */
        @Override
        public void startElement(
                String uri, String local, String name, Attributes attributes)
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

        private static Set<String> unmodifiableSet(String... elements) {
            return Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(elements)));
        }
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
        HAS_WARNED = true;
    }

    @Field
    public void setTesseractPath(String tesseractPath) {
        defaultConfig.setTesseractPath(tesseractPath);
    }

    @Field
    public void setTessdataPath(String tessdataPath) {
        defaultConfig.setTessdataPath(tessdataPath);
    }

    @Field
    public void setLanguage(String language) {
        defaultConfig.setLanguage(language);
    }

    @Field
    public void setPageSegMode(String pageSegMode) {
        defaultConfig.setPageSegMode(pageSegMode);
    }

    @Field
    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        defaultConfig.setMaxFileSizeToOcr(maxFileSizeToOcr);
    }

    @Field
    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        defaultConfig.setMinFileSizeToOcr(minFileSizeToOcr);
    }

    @Field
    public void setTimeout(int timeout) {
        defaultConfig.setTimeout(timeout);
    }

    @Field
    public void setOutputType(String outputType) {
        defaultConfig.setOutputType(outputType);
    }

    @Field
    public void setPreserveInterwordSpacing(boolean preserveInterwordSpacing) {
        defaultConfig.setPreserveInterwordSpacing(preserveInterwordSpacing);
    }

    @Field
    public void setEnableImageProcessing(int enableImageProcessing) {
        defaultConfig.setEnableImageProcessing(enableImageProcessing);
    }

    @Field
    public void setImageMagickPath(String imageMagickPath) {
        defaultConfig.setImageMagickPath(imageMagickPath);
    }

    @Field
    public void setDensity(int density) {
        defaultConfig.setDensity(density);
    }

    @Field
    public void setDepth(int depth) {
        defaultConfig.setDepth(depth);
    }

    @Field
    public void setColorspace(String colorspace) {
        defaultConfig.setColorspace(colorspace);
    }

    @Field
    public void setFilter(String filter) {
        defaultConfig.setFilter(filter);
    }

    @Field
    public void setResize(int resize) {
        defaultConfig.setResize(resize);
    }

    @Field
    public void setApplyRotation(boolean applyRotation) {
        defaultConfig.setApplyRotation(applyRotation);
    }

    public TesseractOCRConfig getDefaultConfig() {
        return defaultConfig;
    }
}

