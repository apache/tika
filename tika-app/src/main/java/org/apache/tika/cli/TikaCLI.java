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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.gui.TikaGUI;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.LanguageProfilerBuilder;
import org.apache.tika.language.ProfilingHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.NetworkParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.xmp.XMPMetadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.gson.Gson;

/**
 * Simple command line interface for Apache Tika.
 */
public class TikaCLI {
    private File extractDir = new File(".");

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure(
                new WriterAppender(new SimpleLayout(), System.err));
        Logger.getRootLogger().setLevel(Level.INFO);

        TikaCLI cli = new TikaCLI();
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
            return getTransformerHandler(output, "html", encoding, prettyPrint);
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
            return new ProfilingHandler() {
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
    
    
    /* Creates ngram profile */
    private final OutputType CREATE_PROFILE = new OutputType() {
        @Override
        public void process(
                InputStream stream, OutputStream output, Metadata metadata)
                throws Exception {
            ngp = LanguageProfilerBuilder.create(profileName, stream, encoding);
            FileOutputStream fos = new FileOutputStream(new File(profileName + ".ngp"));
            ngp.save(fos);//saves ngram profile
            fos.close();
            PrintWriter writer = new PrintWriter(getOutputWriter(output, encoding));
            writer.println("ngram profile location:=" + new File(ngp.getName()).getCanonicalPath());
            writer.flush();
        }
    };

    private ParseContext context;
    
    private Detector detector;

    private Parser parser;

    private OutputType type = XML;
    
    private LanguageProfilerBuilder ngp = null;

    /**
     * Output character encoding, or <code>null</code> for platform default
     */
    private String encoding = null;

    /**
     * Password for opening encrypted documents, or <code>null</code>.
     */
    private String password = System.getenv("TIKA_PASSWORD");

    private boolean pipeMode = true;

    private boolean serverMode = false;

    private boolean fork = false;

    private String profileName = null;

    private boolean prettyPrint;
    
    public TikaCLI() throws Exception {
        context = new ParseContext();
        detector = new DefaultDetector();
        parser = new AutoDetectParser(detector);
        context.set(Parser.class, parser);
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return password;
            }
        });
    }

    public void process(String arg) throws Exception {
        if (arg.equals("-?") || arg.equals("--help")) {
            pipeMode = false;
            usage();
        } else if (arg.equals("-V") || arg.equals("--version")) {
            pipeMode = false;
            version();
        } else if (arg.equals("-v") || arg.equals("--verbose")) {
            Logger.getRootLogger().setLevel(Level.DEBUG);
        } else if (arg.equals("-g") || arg.equals("--gui")) {
            pipeMode = false;
            TikaGUI.main(new String[0]);
        } else if (arg.equals("--list-parser") || arg.equals("--list-parsers")) {
            pipeMode = false;
            displayParsers(false);
        } else if (arg.equals("--list-detector") || arg.equals("--list-detectors")) {
           pipeMode = false;
           displayDetectors();
        } else if (arg.equals("--list-parser-detail") || arg.equals("--list-parser-details")) {
            pipeMode = false;
            displayParsers(true);
        } else if(arg.equals("--list-met-models")){
            pipeMode = false;
            displayMetModels();
        } else if(arg.equals("--list-supported-types")){
            pipeMode = false;
            displaySupportedTypes();
        } else if (arg.equals("--container-aware")
                || arg.equals("--container-aware-detector")) {
            // ignore, as container-aware detectors are now always used
        } else if (arg.equals("-f") || arg.equals("--fork")) {
            fork = true;
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
        } else  if (arg.equals("-y") || arg.equals("--xmp")) {
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
            extractDir = new File(arg.substring("--extract-dir=".length()));
        } else if (arg.equals("-z") || arg.equals("--extract")) {
            type = NO_OUTPUT;
            context.set(EmbeddedDocumentExtractor.class, new FileEmbeddedDocumentExtractor());
        } else if (arg.equals("-r") || arg.equals("--pretty-print")) {
            prettyPrint = true;
        } else if (arg.equals("-p") || arg.equals("--port")
                || arg.equals("-s") || arg.equals("--server")) {
            serverMode = true;
            pipeMode = false;
        } else if (arg.startsWith("-c")) {
            URI uri = new URI(arg.substring("-c".length()));
            parser = new NetworkParser(uri);
        } else if (arg.startsWith("--client=")) {
            URI uri = new URI(arg.substring("--client=".length()));
            parser = new NetworkParser(uri);
        } else if(arg.startsWith("--create-profile=")){
            profileName = arg.substring("--create-profile=".length());
            type = CREATE_PROFILE;
        } else {
            pipeMode = false;
            if (serverMode) {
                new TikaServer(Integer.parseInt(arg)).start();
            } else if (arg.equals("-")) {
                InputStream stream =
                    TikaInputStream.get(new CloseShieldInputStream(System.in));
                try {
                    type.process(stream, System.out, new Metadata());
                } finally {
                    stream.close();
                }
            } else {
                URL url;
                File file = new File(arg);
                if (file.isFile()) {
                    url = file.toURI().toURL();
                } else {
                    url = new URL(arg);
                }
                Metadata metadata = new Metadata();
                InputStream input = TikaInputStream.get(url, metadata);
                try {
                    type.process(input, System.out, metadata);
                } finally {
                    input.close();
                    System.out.flush();
                }
            }
        }
    }

    private void usage() {
        PrintStream out = System.out;
        out.println("usage: java -jar tika-app.jar [option...] [file|port...]");
        out.println();
        out.println("Options:");
        out.println("    -?  or --help          Print this usage message");
        out.println("    -v  or --verbose       Print debug level messages");
        out.println("    -V  or --version       Print the Apache Tika version number");
        out.println();
        out.println("    -g  or --gui           Start the Apache Tika GUI");
        out.println("    -s  or --server        Start the Apache Tika server");
        out.println("    -f  or --fork          Use Fork Mode for out-of-process extraction");
        out.println();
        out.println("    -x  or --xml           Output XHTML content (default)");
        out.println("    -h  or --html          Output HTML content");
        out.println("    -t  or --text          Output plain text content");
        out.println("    -T  or --text-main     Output plain text content (main content only)");
        out.println("    -m  or --metadata      Output only metadata");
        out.println("    -j  or --json          Output metadata in JSON");
        out.println("    -y  or --xmp           Output metadata in XMP");
        out.println("    -l  or --language      Output only language");
        out.println("    -d  or --detect        Detect document type");
        out.println("    -eX or --encoding=X    Use output encoding X");
        out.println("    -pX or --password=X    Use document password X");
        out.println("    -z  or --extract       Extract all attachements into current directory");
        out.println("    --extract-dir=<dir>    Specify target directory for -z");
        out.println("    -r  or --pretty-print  For XML and XHTML outputs, adds newlines and");
        out.println("                           whitespace, for better readability");
        out.println();
        out.println("    --create-profile=X");
        out.println("         Create NGram profile, where X is a profile name");
        out.println("    --list-parsers");
        out.println("         List the available document parsers");
        out.println("    --list-parser-details");
        out.println("         List the available document parsers, and their supported mime types");
        out.println("    --list-detectors");
        out.println("         List the available document detectors");
        out.println("    --list-met-models");
        out.println("         List the available metadata models, and their supported keys");
        out.println("    --list-supported-types");
        out.println("         List all known media types and related information");
        out.println();
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
        out.println("- Server mode");
        out.println();
        out.println("    Use the \"--server\" (or \"-s\") option to start the");
        out.println("    Apache Tika server. The server will listen to the");
        out.println("    ports you specify as one or more arguments.");
        out.println();
    }

    private void version() {
        System.out.println(new Tika().toString());
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
    private void displayParsers(boolean includeMimeTypes) {
        displayParser(parser, includeMimeTypes, 0);
    }
     
    private void displayParser(Parser p, boolean includeMimeTypes, int i) {
        boolean isComposite = (p instanceof CompositeParser);
        String name = (p instanceof ParserDecorator) ?
                      ((ParserDecorator) p).getWrappedParser().getClass().getName() :
                      p.getClass().getName();
        System.out.println(indent(i) + name + (isComposite ? " (Composite Parser):" : ""));
        if (includeMimeTypes && !isComposite) {
            for (MediaType mt : p.getSupportedTypes(context)) {
                System.out.println(indent(i+2) + mt);
            }
        }
        
        if (isComposite) {
            Parser[] subParsers = sortParsers(invertMediaTypeMap(((CompositeParser) p).getParsers()));
            for(Parser sp : subParsers) {
                displayParser(sp, includeMimeTypes, i+2);
            }
        }
    }

    /*
     * Displays loaded detectors and their mime types
     * If a detector is a composite detector, it will list the
     *  sub detectors.
     */
    private void displayDetectors() {
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
                System.out.println("  parser:    " + p.getClass().getName());
            }
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
                .toLowerCase().startsWith("mac os x")) {
            // TIKA-324: Override the default encoding on Mac OS X
            return new OutputStreamWriter(output, "UTF-8");
        } else {
            return new OutputStreamWriter(output);
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
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);

            if (name == null) {
                name = "file" + count++;
            }

            MediaType contentType = detector.detect(inputStream, metadata);

            if (name.indexOf('.')==-1 && contentType!=null) {
                try {
                    name += config.getMimeRepository().forName(
                            contentType.toString()).getExtension();
                } catch (MimeTypeException e) {
                    e.printStackTrace();
                }
            }

            String relID = metadata.get(Metadata.EMBEDDED_RELATIONSHIP_ID);
            if (relID != null && !name.startsWith(relID)) {
              name = relID + "_" + name;
            }

            File outputFile = new File(extractDir, name);
            if (outputFile.exists()) {
                System.err.println("File '"+name+"' already exists; skipping");
                return;
            }

            System.out.println("Extracting '"+name+"' ("+contentType+")");

            FileOutputStream os = new FileOutputStream(outputFile);

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

            os.close();
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
                    InputStream contents = new DocumentInputStream((DocumentEntry) entry);
                    try {
                        destDir.createDocument(entry.getName(), contents);
                    } finally {
                        contents.close();
                    }
                }
            }
        }
    }

    private class TikaServer extends Thread {

        private final ServerSocket server;

        public TikaServer(int port) throws IOException {
            super("Tika server at port " + port);
            server = new ServerSocket(port);
        }

        @Override
        public void run() {
            try {
                try {
                    while (true) {
                        processSocketInBackground(server.accept());
                    }
                } finally {
                    server.close();
                }
            } catch (IOException e) { 
                e.printStackTrace();
            }
        }

        private void processSocketInBackground(final Socket socket) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        try {
                            InputStream input = socket.getInputStream();
                            OutputStream output = socket.getOutputStream();
                            type.process(input, output, new Metadata());
                            output.flush();
                        } finally {
                            socket.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
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
    
    /**
     * Uses GSON to do the JSON escaping, but does
     *  the general JSON glueing ourselves.
     */
    private class NoDocumentJSONMetHandler extends NoDocumentMetHandler {
        private NumberFormat formatter;
        private Gson gson;
       
        public NoDocumentJSONMetHandler(Metadata metadata, PrintWriter writer){
            super(metadata, writer);
            
            formatter = NumberFormat.getInstance();
            gson = new Gson();
        }
        
        @Override
        public void outputMetadata(String[] names) {
           writer.print("{ ");
           boolean first = true;
           for (String name : names) {
              if(! first) {
                 writer.println(", ");
              } else {
                 first = false;
              }
              gson.toJson(name, writer);
              writer.print(":");
              outputValues(metadata.getValues(name));
           }
           writer.print(" }");
        }
        
        public void outputValues(String[] values) {
           if(values.length > 1) {
              writer.print("[");
           }
           for(int i=0; i<values.length; i++) {
              String value = values[i];
              if(i > 0) {
                 writer.print(", ");
              }
              
              if(value == null || value.length() == 0) {
                 writer.print("null");
              } else {
                 // Is it a number?
                 ParsePosition pos = new ParsePosition(0);
                 formatter.parse(value, pos);
                 if(value.length() == pos.getIndex()) {
                    // It's a number. Remove leading zeros and output
                    value = value.replaceFirst("^0+(\\d)", "$1");
                    writer.print(value);
                 } else {
                    // Not a number, escape it
                    gson.toJson(value, writer);
                 }
              }
           }
           if(values.length > 1) {
              writer.print("]");
           }
        }
    }
}
