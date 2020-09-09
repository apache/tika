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
package org.apache.tika.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.Tika;
import org.apache.tika.batch.BatchProcessDriverCLI;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.TikaConfigSerializer;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.gui.TikaGUI;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.NetworkParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.digestutils.CommonsDigester;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.xmp.XMPMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Simple command line interface for Apache Tika.
 */
public class TikaCLI {
    private final int MAX_MARK = 20*1024*1024;//20MB

    private File extractDir = new File(".");

    private static final Logger LOG = LoggerFactory.getLogger(TikaCLI.class);

    public TikaCLI() {
        context = new ParseContext();
    }
    public static void main(String[] args) throws Exception {
        TikaCLI cli = new TikaCLI();

        if (!isConfigured()) {
            PropertyConfigurator.configure(cli.getClass().getResourceAsStream("/log4j.properties"));
        }

        if (cli.testForHelp(args)) {
            cli.usage();
            return;
        } else if (cli.testForBatch(args)) {
            String[] batchArgs = BatchCommandLineBuilder.build(args);
            BatchProcessDriverCLI batchDriver = new BatchProcessDriverCLI(batchArgs);
            batchDriver.execute();
            return;
        }

        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                cli.process(args[i]);
            }
            if (cli.pipeMode) {
                cli.process("-");
            }
        } else {
            // Started with no arguments. Wait for up to 0.1s to see if
            // we have something waiting in standard input and use the
            // pipe mode if we have. If no input is seen, start the GUI.
            if (System.in.available() == 0) {
                Thread.sleep(100);
            }
            if (System.in.available() > 0) {
                cli.process("-");
            } else {
                cli.process("--gui");
            }
        }
    }

    private static boolean isConfigured() {
        //Borrowed from: http://wiki.apache.org/logging-log4j/UsefulCode
        Enumeration appenders = LogManager.getRootLogger().getAllAppenders();
        if (appenders.hasMoreElements()) {
            return true;
        }
        else {
            Enumeration loggers = LogManager.getCurrentLoggers() ;
            while (loggers.hasMoreElements()) {
                org.apache.log4j.Logger c = (org.apache.log4j.Logger) loggers.nextElement();
                if (c.getAllAppenders().hasMoreElements())
                    return true;
            }
        }
        return false;
    }

    private void extractInlineImagesFromPDFs() {
        if (configFilePath == null && context.get(PDFParserConfig.class) == null) {
            PDFParserConfig pdfParserConfig = new PDFParserConfig();
            pdfParserConfig.setExtractInlineImages(true);
            String warn = "As a convenience, TikaCLI has turned on extraction of\n" +
                    "inline images for the PDFParser (TIKA-2374).\n" +
                    "Aside from the -z option, this is not the default behavior\n"+
                    "in Tika generally or in tika-server.";
            LOG.info(warn);
            context.set(PDFParserConfig.class, pdfParserConfig);
        }
    }

    private class OutputType {
        public void process(
                InputStream input, OutputStream output, Metadata metadata)
                throws Exception {
            Parser p = parser;
            if (fork) {
                p = new ForkParser(TikaCLI.class.getClassLoader(), p);
            }
            ContentHandler handler = getContentHandler(output, metadata);

            p.parse(input, handler, metadata, context);
            // fix for TIKA-596: if a parser doesn't generate
            // XHTML output, the lack of an output document prevents
            // metadata from being output: this fixes that
            if (handler instanceof NoDocumentMetHandler){
                NoDocumentMetHandler metHandler = (NoDocumentMetHandler)handler;
                if(!metHandler.metOutput()){
                    metHandler.endDocument();
                }
            }
        }

        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            throw new UnsupportedOperationException();
        }
        
    }

    private final OutputType XML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            return getTransformerHandler(output, "xml", encoding, prettyPrint);
        }
    };

    private final OutputType HTML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            return new ExpandedTitleContentHandler(getTransformerHandler(output, "html", encoding, prettyPrint));
        }
    };

    private final OutputType TEXT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            return new BodyContentHandler(getOutputWriter(output, encoding));
        }
    };

    private final OutputType NO_OUTPUT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) {
            return new DefaultHandler();
        }
    };

    private final OutputType TEXT_MAIN = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            return new BoilerpipeContentHandler(getOutputWriter(output, encoding));
        }
    };
    
    private final OutputType METADATA = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            final PrintWriter writer =
                new PrintWriter(getOutputWriter(output, encoding));
            return new NoDocumentMetHandler(metadata, writer);
        }
    };

    private final OutputType JSON = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            final PrintWriter writer =
                    new PrintWriter(getOutputWriter(output, encoding));
            return new NoDocumentJSONMetHandler(metadata, writer);
        }
    };

    private final OutputType XMP = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, final Metadata metadata) throws Exception {
            final PrintWriter writer =
                    new PrintWriter(getOutputWriter(output, encoding));
            return new NoDocumentXMPMetaHandler(metadata, writer);
        }
    };

    private final OutputType LANGUAGE = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(
                OutputStream output, Metadata metadata) throws Exception {
            final PrintWriter writer =
                new PrintWriter(getOutputWriter(output, encoding));
            return new LanguageHandler() {
                public void endDocument() {
                    writer.println(getLanguage().getLanguage());
                    writer.flush();
                }
            };
        }
    };

    private final OutputType DETECT = new OutputType() {
        @Override
        public void process(
                InputStream stream, OutputStream output, Metadata metadata)
                throws Exception {
            PrintWriter writer =
                new PrintWriter(getOutputWriter(output, encoding));
            writer.println(detector.detect(stream, metadata).toString());
            writer.flush();
        }
    };
    
    
    private ParseContext context;
    
    private Detector detector;

    private Parser parser;

    private TikaConfig config;

    private String configFilePath;

    private OutputType type = XML;

    private boolean recursiveJSON = false;

    private URI networkURI = null;
    
    /**
     * Output character encoding, or <code>null</code> for platform default
     */
    private String encoding = null;

    /**
     * Password for opening encrypted documents, or <code>null</code>.
     */
    private String password = System.getenv("TIKA_PASSWORD");

    private DigestingParser.Digester digester = null;

    private boolean pipeMode = true;

    private boolean fork = false;

    private boolean prettyPrint;

    public void process(String arg) throws Exception {
        if (arg.equals("-?") || arg.equals("--help")) {
            pipeMode = false;
            usage();
        } else if (arg.equals("-V") || arg.equals("--version")) {
            pipeMode = false;
            version();
        } else if (arg.equals("-v") || arg.equals("--verbose")) {
            org.apache.log4j.Logger.getRootLogger().setLevel(Level.DEBUG);
        } else if (arg.equals("-g") || arg.equals("--gui")) {
            pipeMode = false;
            if (configFilePath != null){
                TikaGUI.main(new String[]{configFilePath});
            } else {
                TikaGUI.main(new String[0]);
            }
        } else if (arg.equals("--list-parser") || arg.equals("--list-parsers")) {
            pipeMode = false;
            displayParsers(false, false);
        } else if (arg.equals("--list-detector") || arg.equals("--list-detectors")) {
           pipeMode = false;
           displayDetectors();
        } else if (arg.equals("--list-parser-detail") || arg.equals("--list-parser-details")) {
            pipeMode = false;
            displayParsers(true, false);
        } else if (arg.equals("--list-parser-detail-apt") || arg.equals("--list-parser-details-apt")) {
            pipeMode = false;
            displayParsers(true, true);
        } else if(arg.equals("--list-met-models")){
            pipeMode = false;
            displayMetModels();
        } else if(arg.equals("--list-supported-types")){
            pipeMode = false;
            displaySupportedTypes();
        } else if (arg.startsWith("--compare-file-magic=")) {
            pipeMode = false;
            compareFileMagic(arg.substring(arg.indexOf('=')+1));
        } else if (arg.equals("--dump-minimal-config")) {
            pipeMode = false;
            dumpConfig(TikaConfigSerializer.Mode.MINIMAL);
        } else if (arg.equals("--dump-current-config")) {
            pipeMode = false;
            dumpConfig(TikaConfigSerializer.Mode.CURRENT);
        } else if (arg.equals("--dump-static-config")) {
            pipeMode = false;
            dumpConfig(TikaConfigSerializer.Mode.STATIC);
        } else if (arg.equals("--dump-static-full-config")) {
            pipeMode = false;
            dumpConfig(TikaConfigSerializer.Mode.STATIC_FULL);
        } else if (arg.equals("--container-aware")
                || arg.equals("--container-aware-detector")) {
            // ignore, as container-aware detectors are now always used
        } else if (arg.equals("-f") || arg.equals("--fork")) {
            fork = true;
        } else if (arg.startsWith("--config=")) {
            configFilePath = arg.substring("--config=".length());
        } else if (arg.startsWith("--digest=")) {
            digester = new CommonsDigester(MAX_MARK,
                    arg.substring("--digest=".length()));
        } else if (arg.startsWith("-e")) {
            encoding = arg.substring("-e".length());
        } else if (arg.startsWith("--encoding=")) {
            encoding = arg.substring("--encoding=".length());
        } else if (arg.startsWith("-p") && !arg.equals("-p")) {
            password = arg.substring("-p".length());
        } else if (arg.startsWith("--password=")) {
            password = arg.substring("--password=".length());
        } else  if (arg.equals("-j") || arg.equals("--json")) {
            type = JSON;
        } else if (arg.equals("-J") || arg.equals("--jsonRecursive")) {
            recursiveJSON = true;
        } else if (arg.equals("-y") || arg.equals("--xmp")) {
            type = XMP;
        } else if (arg.equals("-x") || arg.equals("--xml")) {
            type = XML;
        } else if (arg.equals("-h") || arg.equals("--html")) {
            type = HTML;
        } else if (arg.equals("-t") || arg.equals("--text")) {
            type = TEXT;
        } else if (arg.equals("-T") || arg.equals("--text-main")) {
            type = TEXT_MAIN;
        } else if (arg.equals("-m") || arg.equals("--metadata")) {
            type = METADATA;
        } else if (arg.equals("-l") || arg.equals("--language")) {
            type = LANGUAGE;
        } else if (arg.equals("-d") || arg.equals("--detect")) {
            type = DETECT;
        } else if (arg.startsWith("--extract-dir=")) {
            String dirPath = arg.substring("--extract-dir=".length());
            //if the user accidentally doesn't include
            //a directory, set the directory to the cwd
            if (dirPath.length() == 0) {
                dirPath = ".";
            }
            extractDir = new File(dirPath);
        } else if (arg.equals("-z") || arg.equals("--extract")) {
            extractInlineImagesFromPDFs();
            type = NO_OUTPUT;
            context.set(EmbeddedDocumentExtractor.class, new FileEmbeddedDocumentExtractor());
        } else if (arg.equals("-r") || arg.equals("--pretty-print")) {
            prettyPrint = true;
        } else if (arg.equals("-p") || arg.equals("--port")
                || arg.equals("-s") || arg.equals("--server")) {
            throw new IllegalArgumentException(
                    "As of Tika 2.0, the server option is no longer supported in tika-app.\n"+
                    "See https://wiki.apache.org/tika/TikaJAXRS for usage.");
        } else if (arg.startsWith("-c")) {
            networkURI = new URI(arg.substring("-c".length()));
        } else if (arg.startsWith("--client=")) {
            networkURI = new URI(arg.substring("-c".length()));
        } else {
            pipeMode = false;
            configure();
            
            if (arg.equals("-")) {
                try (InputStream stream = TikaInputStream.get(
                        new CloseShieldInputStream(System.in))) {
                    type.process(stream, System.out, new Metadata());
                }
            } else {
                URL url;
                File file = new File(arg);
                if (file.isFile()) {
                    url = file.toURI().toURL();
                } else {
                    url = new URL(arg);
                }
                if (recursiveJSON) {
                    handleRecursiveJson(url, System.out);
                } else {
                    Metadata metadata = new Metadata();
                    try (InputStream input =
                            TikaInputStream.get(url, metadata)) {
                        type.process(input, System.out, metadata);
                    } finally {
                        System.out.flush();
                    }
                }
            }
        }
    }

    private void dumpConfig(TikaConfigSerializer.Mode mode) throws Exception {
        configure();
        TikaConfig localConfig = (config == null) ? TikaConfig.getDefaultConfig() : config;

        TikaConfigSerializer.serialize(localConfig, mode,
                new OutputStreamWriter(System.out, UTF_8), UTF_8);
    }

    private void handleRecursiveJson(URL url, OutputStream output) throws IOException, SAXException, TikaException {
        Metadata metadata = new Metadata();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(getContentHandlerFactory(type),
                        -1, config.getMetadataFilter());
        try (InputStream input = TikaInputStream.get(url, metadata)) {
            wrapper.parse(input, handler, metadata, context);
        }
        JsonMetadataList.setPrettyPrinting(prettyPrint);
        Writer writer = getOutputWriter(output, encoding);
        try {
            JsonMetadataList.toJson(handler.getMetadataList(), writer);
        } finally {
            writer.flush();
        }
    }

    private ContentHandlerFactory getContentHandlerFactory(OutputType type) {
        BasicContentHandlerFactory.HANDLER_TYPE handlerType = BasicContentHandlerFactory.HANDLER_TYPE.IGNORE;
        if (type.equals(HTML)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.HTML;
        } else if (type.equals(XML)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.XML;
        } else if (type.equals(TEXT)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
        } else if (type.equals(TEXT_MAIN)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.BODY;
        } else if (type.equals(METADATA)) {
            handlerType = BasicContentHandlerFactory.HANDLER_TYPE.IGNORE;
        }
        return new BasicContentHandlerFactory(handlerType, -1);
    }
    private void usage() {
        PrintStream out = System.out;
        out.println("usage: java -jar tika-app.jar [option...] [file...]");
        out.println();
        out.println("Options:");
        out.println("    -?  or --help          Print this usage message");
        out.println("    -v  or --verbose       Print debug level messages");
        out.println("    -V  or --version       Print the Apache Tika version number");
        out.println();
        out.println("    -g  or --gui           Start the Apache Tika GUI");
        out.println("    -f  or --fork          Use Fork Mode for out-of-process extraction");
        out.println();
        out.println("    --config=<tika-config.xml>");
        out.println("        TikaConfig file. Must be specified before -g, -s, -f or the dump-x-config !");
        out.println("    --dump-minimal-config  Print minimal TikaConfig");
        out.println("    --dump-current-config  Print current TikaConfig");
        out.println("    --dump-static-config   Print static config");
        out.println("    --dump-static-full-config  Print static explicit config");
        out.println("");
        out.println("    -x  or --xml           Output XHTML content (default)");
        out.println("    -h  or --html          Output HTML content");
        out.println("    -t  or --text          Output plain text content");
        out.println("    -T  or --text-main     Output plain text content (main content only)");
        out.println("    -m  or --metadata      Output only metadata");
        out.println("    -j  or --json          Output metadata in JSON");
        out.println("    -y  or --xmp           Output metadata in XMP");
        out.println("    -J  or --jsonRecursive Output metadata and content from all");
        out.println("                           embedded files (choose content type");
        out.println("                           with -x, -h, -t or -m; default is -x)");
        out.println("    -l  or --language      Output only language");
        out.println("    -d  or --detect        Detect document type");
        out.println("           --digest=X      Include digest X (md2, md5, sha1,");
        out.println("                               sha256, sha384, sha512");
        out.println("    -eX or --encoding=X    Use output encoding X");
        out.println("    -pX or --password=X    Use document password X");
        out.println("    -z  or --extract       Extract all attachements into current directory");
        out.println("    --extract-dir=<dir>    Specify target directory for -z");
        out.println("    -r  or --pretty-print  For JSON, XML and XHTML outputs, adds newlines and");
        out.println("                           whitespace, for better readability");
        out.println();
        out.println("    --list-parsers");
        out.println("         List the available document parsers");
        out.println("    --list-parser-details");
        out.println("         List the available document parsers and their supported mime types");
        out.println("    --list-parser-details-apt");
        out.println("         List the available document parsers and their supported mime types in apt format.");
        out.println("    --list-detectors");
        out.println("         List the available document detectors");
        out.println("    --list-met-models");
        out.println("         List the available metadata models, and their supported keys");
        out.println("    --list-supported-types");
        out.println("         List all known media types and related information");
        out.println();
        out.println();
        out.println("    --compare-file-magic=<dir>");
        out.println("         Compares Tika's known media types to the File(1) tool's magic directory");
        out.println("Description:");
        out.println("    Apache Tika will parse the file(s) specified on the");
        out.println("    command line and output the extracted text content");
        out.println("    or metadata to standard output.");
        out.println();
        out.println("    Instead of a file name you can also specify the URL");
        out.println("    of a document to be parsed.");
        out.println();
        out.println("    If no file name or URL is specified (or the special");
        out.println("    name \"-\" is used), then the standard input stream");
        out.println("    is parsed. If no arguments were given and no input");
        out.println("    data is available, the GUI is started instead.");
        out.println();
        out.println("- GUI mode");
        out.println();
        out.println("    Use the \"--gui\" (or \"-g\") option to start the");
        out.println("    Apache Tika GUI. You can drag and drop files from");
        out.println("    a normal file explorer to the GUI window to extract");
        out.println("    text content and metadata from the files.");
        out.println();
        out.println("- Batch mode");
        out.println();
        out.println("    Simplest method.");
        out.println("    Specify two directories as args with no other args:");
        out.println("         java -jar tika-app.jar <inputDirectory> <outputDirectory>");
        out.println();
        out.println("Batch Options:");
        out.println("    -i  or --inputDir          Input directory");
        out.println("    -o  or --outputDir         Output directory");
        out.println("    -numConsumers              Number of processing threads");
        out.println("    -bc                        Batch config file");
        out.println("    -maxRestarts               Maximum number of times the ");
        out.println("                               watchdog process will restart the child process.");
        out.println("    -timeoutThresholdMillis    Number of milliseconds allowed to a parse");
        out.println("                               before the process is killed and restarted");
        out.println("    -fileList                  List of files to process, with");
        out.println("                               paths relative to the input directory");
        out.println("    -includeFilePat            Regular expression to determine which");
        out.println("                               files to process, e.g. \"(?i)\\.pdf\"");
        out.println("    -excludeFilePat            Regular expression to determine which");
        out.println("                               files to avoid processing, e.g. \"(?i)\\.pdf\"");
        out.println("    -maxFileSizeBytes          Skip files longer than this value");
        out.println();
        out.println("    Control the type of output with -x, -h, -t and/or -J.");
        out.println();
        out.println("    To modify child process jvm args, prepend \"J\" as in:");
        out.println("    -JXmx4g or -JDlog4j.configuration=file:log4j.xml.");
    }

    private void version() {
        System.out.println(new Tika().toString());
    }

    private boolean testForHelp(String[] args) {
        for (String s : args) {
            if (s.equals("-?") || s.equals("--help")) {
                return true;
            }
        }
        return false;
    }

    private boolean testForBatch(String[] args) {
        if (args.length == 2 && ! args[0].startsWith("-")
                && ! args[1].startsWith("-")) {
            Path inputCand = Paths.get(args[0]);
            Path outputCand = Paths.get(args[1]);
            if (Files.isDirectory(inputCand) &&
                    !Files.isRegularFile(outputCand)) {
                return true;
            }
        }

        for (String s : args) {
            if (s.equals("-inputDir") || s.equals("--inputDir") || s.equals("-i")) {
                return true;
            }
        }
        return false;
    }


    private void configure() throws TikaException, IOException, SAXException {

        if (networkURI != null) {
            parser = new NetworkParser(networkURI);
            config = TikaConfig.getDefaultConfig();
        } else {
            if (configFilePath != null) {
                config = new TikaConfig(new File(configFilePath));
            } else {
                config = TikaConfig.getDefaultConfig();
            }

            parser = new AutoDetectParser(config);
            if (digester != null) {
                parser = new DigestingParser(parser, digester);
            }
        }
        detector = config.getDetector();
        context.set(Parser.class, parser);
        context.set(PasswordProvider.class, new SimplePasswordProvider(password));
    }

    private void displayMetModels(){
        Class<?>[] modelClasses = Metadata.class.getInterfaces();
        Arrays.sort(modelClasses, new Comparator<Class<?>>() {
            public int compare(Class<?> o1, Class<?> o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        for (Class<?> modelClass: modelClasses) {
            // we don't care about internal Tika met classes
            // if we do, then we can take this conditional out
            if (!modelClass.getSimpleName().contains("Tika")) {
                System.out.println(modelClass.getSimpleName());
                Field[] keyFields = modelClass.getFields();
                Arrays.sort(keyFields, new Comparator<Field>() {
                    public int compare(Field o1, Field o2) {
                        return o1.getName().compareTo(o2.getName());
                    }
                });
                for (Field keyField: keyFields) {
                    System.out.println(" "+keyField.getName());
                }
            }
        }
    }

    /*
     * Displays loaded parsers and their mime types
     * If a parser is a composite parser, it will list the
     * sub parsers and their mime-types.
     */
    private void displayParsers(boolean includeMimeTypes, boolean aptListFormat) throws TikaException, IOException, SAXException {
        configure();
        displayParser(parser, includeMimeTypes, aptListFormat, 3);
    }
     
    private void displayParser(Parser p, boolean includeMimeTypes, boolean apt, int i) {
        String decorated = null;
        if (p instanceof ParserDecorator) {
            ParserDecorator pd = (ParserDecorator)p;
            decorated = " (Wrapped by " + pd.getDecorationName() + ")";
            p = pd.getWrappedParser();
        }
        
        boolean isComposite = (p instanceof CompositeParser);
        String name = p.getClass().getName();
                      
        if (apt) {
            name = name.substring(0, name.lastIndexOf(".") + 1) + "{{{./api/" + name.replace(".", "/") + "}" + name.substring(name.lastIndexOf(".") + 1) + "}}";
        } else if (decorated != null) {
            name += decorated;
        }
        if ((apt && !isComposite) || !apt) {    // Don't display Composite parsers in the apt output.
            System.out.println(indent(i) + ((apt) ? "* " : "") + name + (isComposite ? " (Composite Parser):" : ""));
            if (apt) System.out.println();
            if (includeMimeTypes && !isComposite) {
                for (MediaType mt : p.getSupportedTypes(context)) {
                    System.out.println(indent(i + 3) + ((apt) ? "* " : "") + mt);
                    if (apt) System.out.println();
                }
            }
        }
        
        if (isComposite) {
            Parser[] subParsers = sortParsers(invertMediaTypeMap(((CompositeParser) p).getParsers()));
            for(Parser sp : subParsers) {
                displayParser(sp, includeMimeTypes, apt, i + ((apt) ? 0 : 3));  // Don't indent for Composites in apt.
            }
        }
    }

    /*
     * Displays loaded detectors and their mime types
     * If a detector is a composite detector, it will list the
     *  sub detectors.
     */
    private void displayDetectors() throws TikaException, IOException, SAXException {
        configure();
        displayDetector(detector, 0);
    }
     
    private void displayDetector(Detector d, int i) {
        boolean isComposite = (d instanceof CompositeDetector);
        String name = d.getClass().getName();
        System.out.println(indent(i) + name + (isComposite ? " (Composite Detector):" : ""));
        if (isComposite) {
            List<Detector> subDetectors = ((CompositeDetector)d).getDetectors();
            for(Detector sd : subDetectors) {
                displayDetector(sd, i+2);
            }
        }
    }

    private String indent(int indent) {
        return "                     ".substring(0, indent);
    }

    private Parser[] sortParsers(Map<Parser, Set<MediaType>> parsers) {
        // Get a nicely sorted list of the parsers
        Parser[] sortedParsers = parsers.keySet().toArray(new Parser[parsers.size()]);
        Arrays.sort(sortedParsers, new Comparator<Parser>() {
            public int compare(Parser p1, Parser p2) {
                String name1 = p1.getClass().getName();
                String name2 = p2.getClass().getName();
                return name1.compareTo(name2);
            }
        });
        return sortedParsers;
    }

    private Map<Parser, Set<MediaType>> invertMediaTypeMap(Map<MediaType, Parser> supported) {
        Map<Parser,Set<MediaType>> parsers = new HashMap<Parser, Set<MediaType>>();
        for(Entry<MediaType, Parser> e : supported.entrySet()) {
            if (!parsers.containsKey(e.getValue())) {
                parsers.put(e.getValue(), new HashSet<MediaType>());
            }
            parsers.get(e.getValue()).add(e.getKey());
        }
        return parsers;
    }

    /**
     * Prints all the known media types, aliases and matching parser classes.
     */
    private void displaySupportedTypes() {
        AutoDetectParser parser = new AutoDetectParser();
        MediaTypeRegistry registry = parser.getMediaTypeRegistry();
        Map<MediaType, Parser> parsers = parser.getParsers();

        for (MediaType type : registry.getTypes()) {
            System.out.println(type);
            for (MediaType alias : registry.getAliases(type)) {
                System.out.println("  alias:     " + alias);
            }
            MediaType supertype = registry.getSupertype(type);
            if (supertype != null) {
                System.out.println("  supertype: " + supertype);
            }
            Parser p = parsers.get(type);
            if (p != null) {
                if (p instanceof CompositeParser) {
                    p = ((CompositeParser)p).getParsers().get(type);
                }
                System.out.println("  parser:    " + p.getClass().getName());
            }
        }
    }
    
    /**
     * Compares our mime types registry with the File(1) tool's 
     *  directory of (uncompiled) Magic entries. 
     * (Well, those with mimetypes anyway)
     * @param magicDir Path to the magic directory
     */
    private void compareFileMagic(String magicDir) throws Exception {
        Set<String> tikaLacking = new TreeSet<String>();
        Set<String> tikaNoMagic = new TreeSet<String>();
        
        // Sanity check
        File dir = new File(magicDir);
        if ((new File(dir, "elf")).exists() &&
            (new File(dir, "mime")).exists() &&
            (new File(dir, "vorbis")).exists()) {
            // Looks plausible
        } else {
            throw new IllegalArgumentException(
                    magicDir + " doesn't seem to hold uncompressed file magic entries"); 
        }
    
        // Find all the mimetypes in the directory
        Set<String> fileMimes = new HashSet<String>();
        for (File mf : dir.listFiles()) {
            if (mf.isFile()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        new FileInputStream(mf), UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("!:mime") ||
                        line.startsWith("#!:mime")) {
                        String mime = line.substring(7).trim();
                        fileMimes.add(mime);
                    }
                }
                r.close();
            }
        }
        
        // See how those compare to the Tika ones
        TikaConfig config = TikaConfig.getDefaultConfig();
        MimeTypes mimeTypes = config.getMimeRepository();
        MediaTypeRegistry registry = config.getMediaTypeRegistry();
        for (String mime : fileMimes) {
            try {
                final MimeType type = mimeTypes.getRegisteredMimeType(mime);
                
                if (type == null) {
                    // Tika doesn't know about this one
                    tikaLacking.add(mime);
                } else {
                    // Tika knows about this one!
                    
                    // Does Tika have magic for it?
                    boolean hasMagic = type.hasMagic();
                    
                    // How about the children?
                    if (!hasMagic) {
                        for (MediaType child : registry.getChildTypes(type.getType())) {
                            MimeType childType = mimeTypes.getRegisteredMimeType(child.toString());
                            if (childType != null && childType.hasMagic()) {
                                hasMagic = true;
                            }
                        }
                    }
                    
                    // How about the parents?
                    MimeType parentType = type;
                    while (parentType != null && !hasMagic) {
                        if (parentType.hasMagic()) {
                            // Has magic, fine
                            hasMagic = true;
                        } else {
                            // Check the parent next
                            MediaType parent = registry.getSupertype(type.getType());
                            if (parent == MediaType.APPLICATION_XML ||
                                parent == MediaType.TEXT_PLAIN ||
                                parent == MediaType.OCTET_STREAM) {
                                // Stop checking parents if we hit a top level type
                                parent = null;
                            }
                            if (parent != null) {
                                parentType = mimeTypes.getRegisteredMimeType(parent.toString());
                            } else {
                                parentType = null;
                            }
                        }
                    }
                    if (!hasMagic) {
                        tikaNoMagic.add(mime);
                    }
                }
            } catch (MimeTypeException e) {
                // Broken entry in the file magic directory
                // Silently skip
            }
        }
        
        // Check how many tika knows about
        int tikaTypes = 0;
        int tikaAliases = 0;
        for (MediaType type : registry.getTypes()) {
            tikaTypes++;
            tikaAliases += registry.getAliases(type).size();
        }
        
        // Report
        System.out.println("Tika knows about " + tikaTypes + " unique mime types");
        System.out.println("Tika knows about " + (tikaTypes+tikaAliases) + " mime types including aliases");
        System.out.println("The File Magic directory knows about " + fileMimes.size() + " unique mime types");
        System.out.println();
        System.out.println("The following mime types are known to File but not Tika:");
        for (String mime : tikaLacking) {
            System.out.println("  " + mime);
        }
        System.out.println();
        System.out.println("The following mime types from File have no Tika magic (but their children might):");
        for (String mime : tikaNoMagic) {
            System.out.println("  " + mime);
        }
    }

    /**
     * Returns a output writer with the given encoding.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     * @param output output stream
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return output writer
     * @throws UnsupportedEncodingException
     *         if the given encoding is not supported
     */
    private static Writer getOutputWriter(OutputStream output, String encoding)
            throws UnsupportedEncodingException {
        if (encoding != null) {
            return new OutputStreamWriter(output, encoding);
        } else if (System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).startsWith("mac os x")) {
            // TIKA-324: Override the default encoding on Mac OS X
            return new OutputStreamWriter(output, UTF_8);
        } else {
            return new OutputStreamWriter(output, Charset.defaultCharset());
        }
    }

    /**
     * Returns a transformer handler that serializes incoming SAX events
     * to XHTML or HTML (depending the given method) using the given output
     * encoding.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     * @param output output stream
     * @param method "xml" or "html"
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return {@link System#out} transformer handler
     * @throws TransformerConfigurationException
     *         if the transformer can not be created
     */
    private static TransformerHandler getTransformerHandler(
            OutputStream output, String method, String encoding, boolean prettyPrint)
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, method);
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, prettyPrint ? "yes" : "no");
        if (encoding != null) {
            handler.getTransformer().setOutputProperty(
                    OutputKeys.ENCODING, encoding);
        }
        handler.setResult(new StreamResult(output));
        return handler;
    }

    private class FileEmbeddedDocumentExtractor
            implements EmbeddedDocumentExtractor {

        private int count = 0;
        private final TikaConfig config = TikaConfig.getDefaultConfig();

        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

            if (! inputStream.markSupported()) {
                inputStream = TikaInputStream.get(inputStream);
            }
            MediaType contentType = detector.detect(inputStream, metadata);

            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            File outputFile = null;
            if (name == null) {
                name = "file" + count++;
            }
            outputFile = getOutputFile(name, metadata, contentType);


            File parent = outputFile.getParentFile();
            if (!parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new IOException("unable to create directory \"" + parent + "\"");
                }
            }
            System.out.println("Extracting '"+name+"' ("+contentType+") to " + outputFile);

            try (FileOutputStream os = new FileOutputStream(outputFile)) {
                if (inputStream instanceof TikaInputStream) {
                    TikaInputStream tin = (TikaInputStream) inputStream;

                    if (tin.getOpenContainer() != null && tin.getOpenContainer() instanceof DirectoryEntry) {
                        POIFSFileSystem fs = new POIFSFileSystem();
                        copy((DirectoryEntry) tin.getOpenContainer(), fs.getRoot());
                        fs.writeFilesystem(os);
                    } else {
                        IOUtils.copy(inputStream, os);
                    }
                } else {
                    IOUtils.copy(inputStream, os);
                }
            } catch (Exception e) {
                //
                // being a CLI program messages should go to the stderr too
                //
                String msg = String.format(
                    Locale.ROOT,
                    "Ignoring unexpected exception trying to save embedded file %s (%s)",
                    name,
                    e.getMessage()
                );
                LOG.warn(msg, e);
            }
        }

        private File getOutputFile(String name, Metadata metadata, MediaType contentType) {
            String ext = getExtension(contentType);
            if (name.indexOf('.')==-1 && contentType!=null) {
                name += ext;
            }

            String relID = metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
            if (relID != null && !name.startsWith(relID)) {
                name = relID + "_" + name;
            }
            //defensively do this so that we don't get an exception
            //from FilenameUtils.normalize
            name = name.replaceAll("\u0000", " ");
            String normalizedName = FilenameUtils.normalize(name);

            if (normalizedName == null) {
                normalizedName = FilenameUtils.getName(name);
            }

            if (normalizedName == null) {
                normalizedName = "file"+count++ +ext;
            }
            //strip off initial C:/ or ~/ or /
            int prefixLength = FilenameUtils.getPrefixLength(normalizedName);
            if (prefixLength > -1) {
                normalizedName = normalizedName.substring(prefixLength);
            }
            File outputFile = new File(extractDir, normalizedName);
            //if file already exists, prepend uuid
            if (outputFile.exists()) {
                String fileName = FilenameUtils.getName(normalizedName);
                outputFile = new File(extractDir, UUID.randomUUID().toString()+"-"+fileName);
            }
            return outputFile;
        }

        private String getExtension(MediaType contentType) {
            try {
                String ext = config.getMimeRepository().forName(
                        contentType.toString()).getExtension();
                if (ext == null) {
                    return ".bin";
                } else {
                    return ext;
                }
            } catch (MimeTypeException e) {
                e.printStackTrace();
            }
            return ".bin";

        }

        protected void copy(DirectoryEntry sourceDir, DirectoryEntry destDir)
                throws IOException {
            for (org.apache.poi.poifs.filesystem.Entry entry : sourceDir) {
                if (entry instanceof DirectoryEntry) {
                    // Need to recurse
                    DirectoryEntry newDir = destDir.createDirectory(entry.getName());
                    copy((DirectoryEntry) entry, newDir);
                } else {
                    // Copy entry
                    try (InputStream contents =
                            new DocumentInputStream((DocumentEntry) entry)) {
                        destDir.createDocument(entry.getName(), contents);
                    }
                }
            }
        }
    }

    private class NoDocumentMetHandler extends DefaultHandler {

        protected final Metadata metadata;

        protected PrintWriter writer;
        
        private boolean metOutput;

        public NoDocumentMetHandler(Metadata metadata, PrintWriter writer){
            this.metadata = metadata;
            this.writer = writer;
            this.metOutput = false;
        }
        
        @Override
        public void endDocument() {
            String[] names = metadata.names();
            Arrays.sort(names);
            outputMetadata(names);
            writer.flush();
            this.metOutput = true;
        }
        
        public void outputMetadata(String[] names) {
           for (String name : names) {
              for(String value : metadata.getValues(name)) {
                 writer.println(name + ": " + value);
              }
           }
        }
        
        public boolean metOutput(){
            return this.metOutput;
        }
        
    }

    /**
     * Outputs the Tika metadata as XMP using the Tika XMP module
     */
    private class NoDocumentXMPMetaHandler extends DefaultHandler
    {
    	protected final Metadata metadata;
    	
        protected PrintWriter writer;
        
        public NoDocumentXMPMetaHandler(Metadata metadata, PrintWriter writer){
        	this.metadata = metadata;
            this.writer = writer;
        }
        
        @Override
        public void endDocument() throws SAXException 
        {
        	try 
        	{
        		XMPMetadata xmp = new XMPMetadata(metadata);
        		String result;
        		result = xmp.toString();
        		writer.write(result);
        		writer.flush();
        	} 
        	catch (TikaException e) 
        	{
        		throw new SAXException(e);
        	}
        }
    }

    private class NoDocumentJSONMetHandler extends DefaultHandler {

        protected final Metadata metadata;
        
        protected PrintWriter writer;

        public NoDocumentJSONMetHandler(Metadata metadata, PrintWriter writer) {
            this.metadata = metadata;
            this.writer = writer;
        }
        
        @Override
        public void endDocument() throws SAXException {
            try {
                JsonMetadata.setPrettyPrinting(prettyPrint);
                JsonMetadata.toJson(metadata, writer);
                writer.flush();
            } catch (TikaException e) {
                throw new SAXException(e);
            }
        }        
    }


    private static class SimplePasswordProvider
            implements PasswordProvider, Serializable {
        private final String password;
        public SimplePasswordProvider(String password) {
            this.password = password;
        }

        @Override
        public String getPassword(Metadata metadata) {
            return password;
        }
    }
}
