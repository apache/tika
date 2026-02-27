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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.logging.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.Tika;
import org.apache.tika.async.cli.TikaAsyncCLI;
import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.digest.DigestDef;
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.gui.TikaGUI;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.NetworkParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.digestutils.CommonsDigesterFactory;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.fork.PipesForkParser;
import org.apache.tika.pipes.fork.PipesForkParserConfig;
import org.apache.tika.pipes.fork.PipesForkResult;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.ToMarkdownContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;
import org.apache.tika.xmp.XMPMetadata;

/**
 * Simple command line interface for Apache Tika.
 */
public class TikaCLI {
    private static final Logger LOG = LoggerFactory.getLogger(TikaCLI.class);

    private final int MAX_MARK = 20 * 1024 * 1024;//20MB

    private final OutputType NO_OUTPUT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) {
            return new DefaultHandler();
        }
    };
    private ParseContext context;
    private Detector detector;
    private Parser parser;
    private TikaLoader tikaLoader;
    private String configFilePath;
    private boolean recursiveJSON = false;
    private URI networkURI = null;
    /**
     * Output character encoding, or <code>null</code> for platform default
     */
    private String encoding = null;
    private final OutputType TEXT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            return new BodyContentHandler(getOutputWriter(output, encoding));
        }
    };
    private final OutputType TEXT_MAIN = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            return new BoilerpipeContentHandler(getOutputWriter(output, encoding));
        }
    };
    private final OutputType TEXT_ALL = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            return new WriteOutContentHandler(getOutputWriter(output, encoding));
        }
    };
    private final OutputType METADATA = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            final PrintWriter writer = new PrintWriter(getOutputWriter(output, encoding));
            return new NoDocumentMetHandler(metadata, writer);
        }
    };
    private final OutputType JSON = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            final PrintWriter writer = new PrintWriter(getOutputWriter(output, encoding));
            return new NoDocumentJSONMetHandler(metadata, writer);
        }
    };
    private final OutputType XMP = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, final Metadata metadata) throws Exception {
            final PrintWriter writer = new PrintWriter(getOutputWriter(output, encoding));
            return new NoDocumentXMPMetaHandler(metadata, writer);
        }
    };
    private final OutputType LANGUAGE = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            final PrintWriter writer = new PrintWriter(getOutputWriter(output, encoding));
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
        public void process(TikaInputStream tis, OutputStream output, Metadata metadata) throws Exception {
            PrintWriter writer = new PrintWriter(getOutputWriter(output, encoding));
            writer.println(detector
                    .detect(tis, metadata, context)
                    .toString());
            writer.flush();
        }
    };
    /**
     * Password for opening encrypted documents, or <code>null</code>.
     */
    private String password = System.getenv("TIKA_PASSWORD");
    private DigesterFactory digesterFactory = null;
    /**
     * Maximum depth for embedded document extraction, or -1 for unlimited.
     */
    private int maxEmbeddedDepth = EmbeddedLimits.UNLIMITED;
    /**
     * Maximum count of embedded documents to extract, or -1 for unlimited.
     */
    private int maxEmbeddedCount = EmbeddedLimits.UNLIMITED;
    private boolean pipeMode = true;
    private boolean prettyPrint;
    /**
     * Fork mode: run parsing in a forked JVM process for isolation.
     */
    private boolean forkMode = false;
    /**
     * Fork mode timeout in milliseconds.
     */
    private long forkTimeout = 60000;
    /**
     * Fork mode JVM arguments.
     */
    private List<String> forkJvmArgs = null;
    /**
     * Fork mode plugins directory.
     */
    private String forkPluginsDir = null;
    private final OutputType MARKDOWN = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            return new BodyContentHandler(new ToMarkdownContentHandler(getOutputWriter(output, encoding)));
        }
    };
    private final OutputType XML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            return getTransformerHandler(output, "xml", encoding, prettyPrint);
        }
    };
    private OutputType type = XML;
    private final OutputType HTML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            return new ExpandedTitleContentHandler(getTransformerHandler(output, "html", encoding, prettyPrint));
        }
    };

    public TikaCLI() {
        context = new ParseContext();
    }

    public static void main(String[] args) throws Exception {
        TikaCLI cli = new TikaCLI();

        if (cli.testForHelp(args)) {
            cli.usage();
            return;
        } else if (cli.testForAsync(args)) {
            async(args);
            return;
        }

        if (args.length > 0) {
            for (String arg : args) {
                cli.process(arg);
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

    private static void async(String[] args) throws Exception {
        args = AsyncHelper.translateArgs(args);
        String tikaConfigPath = "";
        //TODO - runpack is a smelly. fix this.
        boolean runpack = false;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("-c")) {
                tikaConfigPath = args[i + 1];
            } else if ("-Z".equals(args[i]) || "-z".equals(args[i]) || "--extract".equals(args[i])) {
                runpack = true;
            }
        }

        if (runpack || ! StringUtils.isBlank(tikaConfigPath)) {
            TikaAsyncCLI.main(args);
            return;
        }
        if (args.length == 1 &&  args[0].endsWith(".json")) {
            TikaAsyncCLI.main(args);
            return;
        }
        // For batch mode (two directories), pass directly to TikaAsyncCLI.
        // It will create its own config with PluginsWriter that includes
        // plugin-roots, fetcher, emitter, and pipes-iterator configuration.
        TikaAsyncCLI.main(args);
    }

    /**
     * Returns a output writer with the given encoding.
     *
     * @param output   output stream
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return output writer
     * @throws UnsupportedEncodingException if the given encoding is not supported
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     */
    private static Writer getOutputWriter(OutputStream output, String encoding) throws UnsupportedEncodingException {
        if (encoding != null) {
            return new OutputStreamWriter(output, encoding);
        } else {
            return new OutputStreamWriter(output, UTF_8);
        }
    }

    /**
     * Returns a transformer handler that serializes incoming SAX events
     * to XHTML or HTML (depending the given method) using the given output
     * encoding.
     *
     * @param output   output stream
     * @param method   "xml" or "html"
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return {@link System#out} transformer handler
     * @throws TransformerConfigurationException if the transformer can not be created
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     */
    private static TransformerHandler getTransformerHandler(OutputStream output, String method, String encoding, boolean prettyPrint)
            throws TransformerConfigurationException, TikaException {
        SAXTransformerFactory factory = XMLReaderUtils.getSAXTransformerFactory();
        TransformerHandler handler = factory.newTransformerHandler();

        handler
                .getTransformer()
                .setOutputProperty(OutputKeys.METHOD, method);
        handler
                .getTransformer()
                .setOutputProperty(OutputKeys.INDENT, prettyPrint ? "yes" : "no");
        if (encoding != null) {
            handler
                    .getTransformer()
                    .setOutputProperty(OutputKeys.ENCODING, encoding);
        }
        handler.setResult(new StreamResult(output));
        return handler;
    }

    private boolean testForAsync(String[] args) {

        // Single .json file is a config file for async mode
        if (args.length == 1 && args[0].endsWith(".json")) {
            return true;
        }

        if (args.length == 2) {
            if (Files.isDirectory(Paths.get(args[0]))) {
                return true;
            }
        }

        // Check if last two args are directories (batch mode with options)
        if (args.length >= 2) {
            String lastArg = args[args.length - 1];
            String secondLastArg = args[args.length - 2];
            // Make sure neither looks like an option value
            if (!lastArg.startsWith("-") && !secondLastArg.startsWith("-")) {
                try {
                    if (Files.isDirectory(Paths.get(secondLastArg)) &&
                        (Files.isDirectory(Paths.get(lastArg)) || !Files.exists(Paths.get(lastArg)))) {
                        return true;
                    }
                } catch (Exception e) {
                    // Invalid path, not batch mode
                }
            }
        }

        for (String arg : args) {
            if (arg.equals("-a") || arg.equals("--async")) {
                return true;
            }
            if (arg.equals("-i") || arg.startsWith("--input")) {
                return true;
            }
            if (arg.equals("-o") || arg.startsWith("--output")) {
                return true;
            }
            if (arg.equals("-Z") || arg.equals("-z") || arg.equals("--extract") || arg.startsWith("--extract-dir")) {
                return true;
            }

        }
        return false;
    }

    public void process(String arg) throws Exception {
        if (arg.equals("-?") || arg.equals("--help")) {
            pipeMode = false;
            usage();
        } else if (arg.equals("-V") || arg.equals("--version")) {
            pipeMode = false;
            version();
        } else if (arg.equals("-v") || arg.equals("--verbose")) {
            org.apache.logging.log4j.core.config.Configurator.setRootLevel(Level.DEBUG);
        } else if (arg.equals("-g") || arg.equals("--gui")) {
            pipeMode = false;
            if (configFilePath != null) {
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
        } else if (arg.equals("--list-met-models")) {
            pipeMode = false;
            displayMetModels();
        } else if (arg.equals("--list-supported-types")) {
            pipeMode = false;
            displaySupportedTypes();
        } else if (arg.startsWith("--compare-file-magic=")) {
            pipeMode = false;
            compareFileMagic(arg.substring("--compare-file-magic=".length()));
        //TODO -- rework with json serialization
        /*} else if (arg.equals("--dump-minimal-config")) {
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
            dumpConfig(TikaConfigSerializer.Mode.STATIC_FULL);*/
        } else if (arg.startsWith("--convert-config-xml-to-json=")) {
            pipeMode = false;
            convertConfigXmlToJson(arg.substring("--convert-config-xml-to-json=".length()));
        } else if (arg.equals("--container-aware") || arg.equals("--container-aware-detector")) {
            // ignore, as container-aware detectors are now always used
        } else if (arg.startsWith("--config=")) {
            configFilePath = arg.substring("--config=".length());
        } else if (arg.startsWith("--digest=")) {
            String algorithmName = arg.substring("--digest=".length()).toUpperCase(Locale.ROOT);
            DigestDef.Algorithm algorithm = DigestDef.Algorithm.valueOf(algorithmName);
            CommonsDigesterFactory factory = new CommonsDigesterFactory();
            factory.setDigests(Collections.singletonList(new DigestDef(algorithm)));
            digesterFactory = factory;
        } else if (arg.startsWith("-e")) {
            encoding = arg.substring("-e".length());
        } else if (arg.startsWith("--encoding=")) {
            encoding = arg.substring("--encoding=".length());
        } else if (arg.startsWith("-p") && !arg.equals("-p")) {
            password = arg.substring("-p".length());
        } else if (arg.startsWith("--password=")) {
            password = arg.substring("--password=".length());
        } else if (arg.equals("-j") || arg.equals("--json")) {
            type = JSON;
        } else if (arg.equals("-J") || arg.equals("--jsonRecursive")) {
            recursiveJSON = true;
        } else if (arg.equals("-y") || arg.equals("--xmp")) {
            type = XMP;
        } else if (arg.equals("-x") || arg.equals("--xml")) {
            type = XML;
        } else if (arg.equals("-h") || arg.equals("--html")) {
            type = HTML;
        } else if (arg.equals("--md")) {
            type = MARKDOWN;
        } else if (arg.equals("-t") || arg.equals("--text")) {
            type = TEXT;
        } else if (arg.equals("-T") || arg.equals("--text-main")) {
            type = TEXT_MAIN;
        } else if (arg.equals("-A") || arg.equals("--text-all")) {
            type = TEXT_ALL;
        } else if (arg.equals("-m") || arg.equals("--metadata")) {
            type = METADATA;
        } else if (arg.equals("-l") || arg.equals("--language")) {
            type = LANGUAGE;
        } else if (arg.equals("-d") || arg.equals("--detect")) {
            type = DETECT;
        } else if (arg.equals("-f") || arg.equals("--fork")) {
            forkMode = true;
        } else if (arg.startsWith("--fork-timeout=")) {
            forkTimeout = Long.parseLong(arg.substring("--fork-timeout=".length()));
        } else if (arg.startsWith("--fork-jvm-args=")) {
            forkJvmArgs = Arrays.asList(arg.substring("--fork-jvm-args=".length()).split(","));
        } else if (arg.startsWith("--fork-plugins-dir=")) {
            forkPluginsDir = arg.substring("--fork-plugins-dir=".length());
        } else if (arg.startsWith("--maxEmbeddedDepth=")) {
            maxEmbeddedDepth = Integer.parseInt(arg.substring("--maxEmbeddedDepth=".length()));
        } else if (arg.startsWith("--maxEmbeddedCount=")) {
            maxEmbeddedCount = Integer.parseInt(arg.substring("--maxEmbeddedCount=".length()));
        } else if (arg.equals("-r") || arg.equals("--pretty-print")) {
            prettyPrint = true;
        } else if (arg.equals("-p") || arg.equals("--port") || arg.equals("-s") || arg.equals("--server")) {
            throw new IllegalArgumentException("As of Tika 2.0, the server option is no longer supported in tika-app.\n" + "See https://wiki.apache.org/tika/TikaJAXRS for usage.");
        } else if (arg.startsWith("-c")) {
            networkURI = new URI(arg.substring("-c".length()));
        } else if (arg.startsWith("--client=")) {
            networkURI = new URI(arg.substring("--client=".length()));
        } else {
            pipeMode = false;
            configure();

            if (arg.equals("-")) {
                try (TikaInputStream tis = TikaInputStream.get(CloseShieldInputStream.wrap(System.in))) {
                    if (forkMode) {
                        processWithFork(tis, Metadata.newInstance(context), System.out);
                    } else {
                        type.process(tis, System.out, Metadata.newInstance(context));
                    }
                }
            } else {
                URL url;
                File file = new File(arg);
                if (file.isFile()) {
                    url = file
                            .toURI()
                            .toURL();
                } else {
                    url = new URL(arg);
                }
                if (forkMode) {
                    Metadata metadata = Metadata.newInstance(context);
                    try (TikaInputStream tis = TikaInputStream.get(url, metadata)) {
                        processWithFork(tis, metadata, System.out);
                    }
                } else if (recursiveJSON) {
                    handleRecursiveJson(url, System.out);
                } else {
                    Metadata metadata = Metadata.newInstance(context);
                    try (TikaInputStream tis = TikaInputStream.get(url, metadata)) {
                        type.process(tis, System.out, metadata);
                    } finally {
                        System.out.flush();
                    }
                }
            }
        }
    }

    //TODO -- rework with json serialization
    /*private void dumpConfig(TikaConfigSerializer.Mode mode) throws Exception {
        configure();
        TikaLoader localConfig = (tikaLoader == null) ? TikaLoader.loadDefault() : tikaLoader;
        //TODO -- implement mode
        System.out.println(localConfig.getConfig().toString());
    }*/

    private void convertConfigXmlToJson(String paths) throws Exception {
        String[] parts = paths.split(",");
        if (parts.length != 2) {
            System.err.println("Error: --convert-config-xml-to-json requires input and output paths separated by comma");
            System.err.println("Usage: --convert-config-xml-to-json=<input.xml>,<output.json>");
            return;
        }

        Path xmlPath = Paths.get(parts[0].trim());
        Path jsonPath = Paths.get(parts[1].trim());

        if (!Files.exists(xmlPath)) {
            System.err.println("Error: Input XML file not found: " + xmlPath);
            return;
        }

        try {
            XmlToJsonConfigConverter.convert(xmlPath, jsonPath);
            System.out.println("Successfully converted XML config to JSON:");
            System.out.println("  Input:  " + xmlPath.toAbsolutePath());
            System.out.println("  Output: " + jsonPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error converting config: " + e.getMessage());
            throw e;
        }
    }

    private void handleRecursiveJson(URL url, OutputStream output) throws IOException, SAXException, TikaException {
        Metadata metadata = Metadata.newInstance(context);
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(getContentHandlerFactory(type));
        try (TikaInputStream tis = TikaInputStream.get(url, metadata)) {
            wrapper.parse(tis, handler, metadata, context);
        }
        JsonMetadataList.setPrettyPrinting(prettyPrint);
        try (Writer writer = getOutputWriter(output, encoding)) {
            List<Metadata> metadataList = handler.getMetadataList();
            tikaLoader.loadMetadataFilters().filter(metadataList);
            JsonMetadataList.toJson(metadataList, writer);
        }
    }

    /**
     * Process a file using forked JVM process for isolation.
     * This provides protection against parser crashes, OOM, and other issues.
     */
    private void processWithFork(TikaInputStream tis, Metadata metadata, OutputStream output) throws Exception {
        PipesForkParserConfig config = new PipesForkParserConfig();

        // Set handler type based on output type
        config.setContentHandlerFactory(getContentHandlerFactory(type));

        // Set parse mode based on recursiveJSON flag
        if (recursiveJSON) {
            config.setParseMode(ParseMode.RMETA);
        } else {
            config.setParseMode(ParseMode.CONCATENATE);
        }

        // Set timeout
        config.setTimeoutLimits(new TimeoutLimits(
                TimeoutLimits.DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS, forkTimeout));

        // Set JVM args if provided
        if (forkJvmArgs != null && !forkJvmArgs.isEmpty()) {
            config.setJvmArgs(forkJvmArgs);
        }

        // Set plugins directory if provided
        if (forkPluginsDir != null) {
            config.setPluginsDir(Paths.get(forkPluginsDir));
        }

        // Set embedded limits if configured
        if (maxEmbeddedDepth != EmbeddedLimits.UNLIMITED || maxEmbeddedCount != EmbeddedLimits.UNLIMITED) {
            EmbeddedLimits limits = new EmbeddedLimits();
            if (maxEmbeddedDepth != EmbeddedLimits.UNLIMITED) {
                limits.setMaxDepth(maxEmbeddedDepth);
            }
            if (maxEmbeddedCount != EmbeddedLimits.UNLIMITED) {
                limits.setMaxCount(maxEmbeddedCount);
            }
            config.setEmbeddedLimits(limits);
        }

        try (PipesForkParser parser = new PipesForkParser(config)) {
            PipesForkResult result = parser.parse(tis, metadata);

            if (result.isProcessCrash()) {
                LOG.error("Fork process crashed: {}", result.getStatus());
                System.err.println("Fork process crashed: " + result.getStatus());
                return;
            }

            List<Metadata> metadataList = result.getMetadataList();

            // Output based on type
            if (recursiveJSON) {
                // Output as JSON metadata list
                JsonMetadataList.setPrettyPrinting(prettyPrint);
                try (Writer writer = getOutputWriter(output, encoding)) {
                    JsonMetadataList.toJson(metadataList, writer);
                }
            } else if (type == JSON || type == METADATA) {
                // Output metadata (first item only for single-file mode)
                if (!metadataList.isEmpty()) {
                    Metadata m = metadataList.get(0);
                    if (type == JSON) {
                        JsonMetadata.setPrettyPrinting(prettyPrint);
                        try (Writer writer = getOutputWriter(output, encoding)) {
                            JsonMetadata.toJson(m, writer);
                        }
                    } else {
                        try (PrintWriter writer = new PrintWriter(getOutputWriter(output, encoding))) {
                            String[] names = m.names();
                            Arrays.sort(names);
                            for (String name : names) {
                                for (String value : m.getValues(name)) {
                                    writer.println(name + ": " + value);
                                }
                            }
                            writer.flush();
                        }
                    }
                }
            } else {
                // Output content (text, xml, html)
                if (!metadataList.isEmpty()) {
                    String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
                    if (content != null) {
                        try (Writer writer = getOutputWriter(output, encoding)) {
                            writer.write(content);
                            writer.flush();
                        }
                    }
                }
            }
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
        out.println();
        out.println("    --config=<tika-config.xml>");
        out.println("        TikaConfig file. Must be specified before -g, -s, -f or the dump-x-config !");
        // TODO: TIKA-XXXX - Re-enable config dump options once JSON serialization is complete
        // These options are not yet implemented in 4.x due to the migration from XML to JSON config
        // out.println("    --dump-minimal-config  Print minimal TikaConfig");
        // out.println("    --dump-current-config  Print current TikaConfig");
        // out.println("    --dump-static-config   Print static config");
        // out.println("    --dump-static-full-config  Print static explicit config");
        out.println("    --convert-config-xml-to-json=<input.xml>,<output.json>");
        out.println("        Convert legacy XML config to JSON format (parsers section only)");
        out.println("");
        out.println("    -x  or --xml           Output XHTML content (default)");
        out.println("    -h  or --html          Output HTML content");
        out.println("    -t  or --text          Output plain text content (body)");
        out.println("    --md                   Output Markdown content (body)");
        out.println("    -T  or --text-main     Output plain text content (main content only via boilerpipe handler)");
        out.println("    -A  or --text-all      Output all text content");
        out.println("    -m  or --metadata      Output only metadata");
        out.println("    -j  or --json          Output metadata in JSON");
        out.println("    -y  or --xmp           Output metadata in XMP");
        out.println("    -J  or --jsonRecursive Output metadata and content from all");
        out.println("                           embedded files (choose content type");
        out.println("                           with -x, -h, -t or -m; default is -x)");
        out.println("    -a  or --async         Run Tika in async mode; must specify details in a" + " tikaConfig file");
        out.println("    -l  or --language      Output only language");
        out.println("    -d  or --detect        Detect document type");
        out.println("           --digest=X      Include digest X (md2, md5, sha1,");
        out.println("                               sha256, sha384, sha512");
        out.println("    -eX or --encoding=X    Use output encoding X");
        out.println("    -pX or --password=X    Use document password X");
        out.println("    -z  or --extract       Extract all attachements into current directory");
        out.println("    --extract-dir=<dir>    Specify target directory for -z");
        out.println("    --maxEmbeddedDepth=X   Maximum depth for embedded document extraction");
        out.println("    --maxEmbeddedCount=X   Maximum number of embedded documents to extract");
        out.println("    -r  or --pretty-print  For JSON, XML and XHTML outputs, adds newlines and");
        out.println("                           whitespace, for better readability");
        out.println();
        out.println("Fork Mode (process isolation):");
        out.println("    -f  or --fork          Run parsing in a forked JVM process for isolation");
        out.println("                           Protects against parser crashes, OOM, and timeouts");
        out.println("    --fork-timeout=<ms>    Parse timeout in milliseconds (default: 60000)");
        out.println("    --fork-jvm-args=<args> JVM args for forked process (comma-separated)");
        out.println("                           e.g., --fork-jvm-args=-Xmx512m,-Dsome.prop=value");
        out.println("    --fork-plugins-dir=<dir> Directory containing plugin zips");
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
        out.println("Batch/Pipes Options:");
        out.println("    -i                         Input directory");
        out.println("    -o                         Output directory");
        out.println("    -n                         Number of forked processes");
        out.println("    -X                         -Xmx in the forked processes");
        out.println("    -T                         Timeout in milliseconds");
        out.println("    -Z                         Recursively unpack all the attachments, too");
        out.println("    --unpack-format=<format>   Output format: REGULAR (default) or FRICTIONLESS");
        out.println("    --unpack-mode=<mode>       Output mode: ZIPPED (default) or DIRECTORY");
        out.println("    --unpack-include-metadata  Include metadata.json in Frictionless output");
        out.println();
        out.println();
    }

    private void version() {
        System.out.println(Tika.getString());
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
        if (args.length == 2 && !args[0].startsWith("-") && !args[1].startsWith("-")) {
            Path inputCand = Paths.get(args[0]);
            Path outputCand = Paths.get(args[1]);
            if (Files.isDirectory(inputCand) && !Files.isRegularFile(outputCand)) {
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
        if (configFilePath != null) {
            tikaLoader = TikaLoader.load(Paths.get(configFilePath));
        } else {
            String warn = "As a convenience, TikaCLI has turned on several non-default features\n" +
                    "as specified in tika-app/src/main/resources/tika-config-default-single-file.json.\n" +
                    "See: TIKA-2374, TIKA-4017, TIKA-4354 and TIKA-4472).\n" +
                    "This is not the default behavior in Tika generally or in tika-server.";
            LOG.info(warn);
            Path tempConfig = Files.createTempFile("tika-config-", ".json");
            try {
                try (InputStream is = getClass().getResourceAsStream("/tika-config-default-single-file.json")) {
                    Files.copy(is, tempConfig, StandardCopyOption.REPLACE_EXISTING);
                }
                tikaLoader = TikaLoader.load(tempConfig);
            } finally {
                Files.deleteIfExists(tempConfig);
            }
        }
        if (networkURI != null) {
            parser = new NetworkParser(networkURI);
        } else {
            parser = tikaLoader.loadAutoDetectParser();
        }

        // Load configs from tika-config.json and merge into existing context
        // (preserves EmbeddedDocumentExtractor and other items set before configure())
        ParseContext loadedContext = tikaLoader.loadParseContext();
        context.copyFrom(loadedContext);

        // Override DigesterFactory in ParseContext if configured via --digest= command line
        if (digesterFactory != null) {
            context.set(DigesterFactory.class, digesterFactory);
        }
        // Set EmbeddedLimits if any limits were specified via command line
        if (maxEmbeddedDepth != EmbeddedLimits.UNLIMITED || maxEmbeddedCount != EmbeddedLimits.UNLIMITED) {
            EmbeddedLimits limits = new EmbeddedLimits();
            if (maxEmbeddedDepth != EmbeddedLimits.UNLIMITED) {
                limits.setMaxDepth(maxEmbeddedDepth);
            }
            if (maxEmbeddedCount != EmbeddedLimits.UNLIMITED) {
                limits.setMaxCount(maxEmbeddedCount);
            }
            context.set(EmbeddedLimits.class, limits);
        }
        detector = tikaLoader.loadDetectors();
        context.set(Parser.class, parser);
        context.set(PasswordProvider.class, new SimplePasswordProvider(password));
    }

    private void displayMetModels() {
        Class<?>[] modelClasses = Metadata.class.getInterfaces();
        Arrays.sort(modelClasses, Comparator.comparing(Class::getName));

        for (Class<?> modelClass : modelClasses) {
            // we don't care about internal Tika met classes
            // if we do, then we can take this conditional out
            if (!modelClass
                    .getSimpleName()
                    .contains("Tika")) {
                System.out.println(modelClass.getSimpleName());
                Field[] keyFields = modelClass.getFields();
                Arrays.sort(keyFields, Comparator.comparing(Field::getName));
                for (Field keyField : keyFields) {
                    System.out.println(" " + keyField.getName());
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
            ParserDecorator pd = (ParserDecorator) p;
            decorated = " (Wrapped by " + pd.getDecorationName() + ")";
            p = pd.getWrappedParser();
        }

        boolean isComposite = (p instanceof CompositeParser);
        String name = p
                .getClass()
                .getName();

        if (apt) {
            name = name.substring(0, name.lastIndexOf(".") + 1) + "{{{./api/" + name.replace(".", "/") + "}" + name.substring(name.lastIndexOf(".") + 1) + "}}";
        } else if (decorated != null) {
            name += decorated;
        }
        if ((apt && !isComposite) || !apt) {    // Don't display Composite parsers in the apt output.
            System.out.println(indent(i) + ((apt) ? "* " : "") + name + (isComposite ? " (Composite Parser):" : ""));
            if (apt) {
                System.out.println();
            }
            if (includeMimeTypes && !isComposite) {
                for (MediaType mt : p.getSupportedTypes(context)) {
                    System.out.println(indent(i + 3) + ((apt) ? "* " : "") + mt);
                    if (apt) {
                        System.out.println();
                    }
                }
            }
        }

        if (isComposite) {
            Parser[] subParsers = sortParsers(invertMediaTypeMap(((CompositeParser) p).getParsers()));
            for (Parser sp : subParsers) {
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
        String name = d
                .getClass()
                .getName();
        System.out.println(indent(i) + name + (isComposite ? " (Composite Detector):" : ""));
        if (isComposite) {
            List<Detector> subDetectors = ((CompositeDetector) d).getDetectors();
            for (Detector sd : subDetectors) {
                displayDetector(sd, i + 2);
            }
        }
    }

    private String indent(int indent) {
        return "                     ".substring(0, indent);
    }

    private Parser[] sortParsers(Map<Parser, Set<MediaType>> parsers) {
        // Get a nicely sorted list of the parsers
        Parser[] sortedParsers = parsers
                .keySet()
                .toArray(new Parser[0]);
        Arrays.sort(sortedParsers, (p1, p2) -> {
            String name1 = p1
                    .getClass()
                    .getName();
            String name2 = p2
                    .getClass()
                    .getName();
            return name1.compareTo(name2);
        });
        return sortedParsers;
    }

    private Map<Parser, Set<MediaType>> invertMediaTypeMap(Map<MediaType, Parser> supported) {
        Map<Parser, Set<MediaType>> parsers = new HashMap<>();
        for (Entry<MediaType, Parser> e : supported.entrySet()) {
            if (!parsers.containsKey(e.getValue())) {
                parsers.put(e.getValue(), new HashSet<>());
            }
            parsers
                    .get(e.getValue())
                    .add(e.getKey());
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
                    p = ((CompositeParser) p)
                            .getParsers()
                            .get(type);
                }
                System.out.println("  parser:    " + p
                        .getClass()
                        .getName());
            }
        }
    }

    /**
     * Compares our mime types registry with the File(1) tool's
     * directory of (uncompiled) Magic entries.
     * (Well, those with mimetypes anyway)
     *
     * @param magicDir Path to the magic directory
     */
    private void compareFileMagic(String magicDir) throws Exception {
        Set<String> tikaLacking = new TreeSet<>();
        Set<String> tikaNoMagic = new TreeSet<>();

        // Plausibility check
        File dir = new File(magicDir);
        if ((new File(dir, "elf")).exists() && (new File(dir, "mime")).exists() && (new File(dir, "vorbis")).exists()) {
            // Looks plausible
        } else {
            throw new IllegalArgumentException(magicDir + " doesn't seem to hold uncompressed file magic entries");
        }

        // Find all the mimetypes in the directory
        Set<String> fileMimes = new HashSet<>();
        for (File mf : dir.listFiles()) {
            if (mf.isFile()) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(mf), UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("!:mime") || line.startsWith("#!:mime")) {
                            String mime = line
                                    .substring(7)
                                    .trim();
                            fileMimes.add(mime);
                        }
                    }
                }
            }
        }

        // See how those compare to the Tika ones
        TikaLoader loader = TikaLoader.loadDefault();
        MimeTypes mimeTypes = TikaLoader.getMimeTypes();
        MediaTypeRegistry registry = loader.getMediaTypeRegistry();
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
                            if (parent == MediaType.APPLICATION_XML || parent == MediaType.TEXT_PLAIN || parent == MediaType.OCTET_STREAM) {
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
            tikaAliases += registry
                    .getAliases(type)
                    .size();
        }

        // Report
        System.out.println("Tika knows about " + tikaTypes + " unique mime types");
        System.out.println("Tika knows about " + (tikaTypes + tikaAliases) + " mime types including aliases");
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

    private static class NoDocumentMetHandler extends DefaultHandler {

        protected final Metadata metadata;

        protected PrintWriter writer;

        private boolean metOutput;

        public NoDocumentMetHandler(Metadata metadata, PrintWriter writer) {
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
                for (String value : metadata.getValues(name)) {
                    writer.println(name + ": " + value);
                }
            }
        }

        public boolean metOutput() {
            return this.metOutput;
        }

    }

    /**
     * Outputs the Tika metadata as XMP using the Tika XMP module
     */
    private static class NoDocumentXMPMetaHandler extends DefaultHandler {
        protected final Metadata metadata;

        protected PrintWriter writer;

        public NoDocumentXMPMetaHandler(Metadata metadata, PrintWriter writer) {
            this.metadata = metadata;
            this.writer = writer;
        }

        @Override
        public void endDocument() throws SAXException {
            try {
                XMPMetadata xmp = new XMPMetadata(metadata);
                String result;
                result = xmp.toString();
                writer.write(result);
                writer.flush();
            } catch (TikaException e) {
                throw new SAXException(e);
            }
        }
    }

    private static class SimplePasswordProvider implements PasswordProvider, Serializable {
        private final String password;

        public SimplePasswordProvider(String password) {
            this.password = password;
        }

        @Override
        public String getPassword(Metadata metadata) {
            return password;
        }
    }

    private class OutputType {
        public void process(TikaInputStream tis, OutputStream output, Metadata metadata) throws Exception {
            Parser p = parser;
            ContentHandler handler = getContentHandler(output, metadata);
            p.parse(tis, handler, metadata, context);
            // fix for TIKA-596: if a parser doesn't generate
            // XHTML output, the lack of an output document prevents
            // metadata from being output: this fixes that
            if (handler instanceof NoDocumentMetHandler) {
                NoDocumentMetHandler metHandler = (NoDocumentMetHandler) handler;
                if (!metHandler.metOutput()) {
                    metHandler.endDocument();
                }
            }
        }

        protected ContentHandler getContentHandler(OutputStream output, Metadata metadata) throws Exception {
            throw new UnsupportedOperationException();
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
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }
    }
}
