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
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.gui.TikaGUI;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.ProfilingHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * Simple command line interface for Apache Tika.
 */
public class TikaCLI {

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure(
                new WriterAppender(new SimpleLayout(), System.err));
        Logger.getRootLogger().setLevel(Level.INFO);

        TikaCLI cli = new TikaCLI();
        for (int i = 0; i < args.length; i++) {
            cli.process(args[i]);
        }
        if (cli.pipeMode) {
            cli.process("-");
        }
    }

    private class OutputType {

        public void process(InputStream input, OutputStream output)
                throws Exception {
            Parser p = parser;
            if (fork) {
                p = new ForkParser(TikaCLI.class.getClassLoader(), p);
            }
            ContentHandler handler = getContentHandler(output);
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

        protected ContentHandler getContentHandler(OutputStream output)
                throws Exception {
            throw new UnsupportedOperationException();
        }
        
    }

    private final OutputType XML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output)
                throws Exception {
            return getTransformerHandler(output, "xml", encoding);
        }
    };

    private final OutputType HTML = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output)
                throws Exception {
            return getTransformerHandler(output, "html", encoding);
        }
    };

    private final OutputType TEXT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output)
                throws Exception {
            return new BodyContentHandler(getOutputWriter(output, encoding));
        }
    };

    private final OutputType NO_OUTPUT = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output) {
            return new DefaultHandler();
        }
    };

    private final OutputType TEXT_MAIN = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output)
                throws Exception {
            return new BoilerpipeContentHandler(getOutputWriter(output, encoding));
        }
    };
    
    private final OutputType METADATA = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output)
                throws Exception {
            final PrintWriter writer =
                new PrintWriter(getOutputWriter(output, encoding));
            return new NoDocumentMetHandler(writer);
        }
    };

    private final OutputType LANGUAGE = new OutputType() {
        @Override
        protected ContentHandler getContentHandler(OutputStream output)
                throws Exception {
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
        public void process(InputStream stream, OutputStream output)
                throws Exception {
            PrintWriter writer =
                new PrintWriter(getOutputWriter(output, encoding));
            writer.println(detector.detect(stream, metadata).toString());
            writer.flush();
        }
    };

    private ParseContext context;
    
    private Detector detector;

    private AutoDetectParser parser;

    private Metadata metadata;

    private OutputType type = XML;

    /**
     * Output character encoding, or <code>null</code> for platform default
     */
    private String encoding = null;

    private boolean pipeMode = true;

    private boolean portMode = false;

    private boolean fork = false;

    public TikaCLI() throws TransformerConfigurationException, IOException, TikaException, SAXException {
        context = new ParseContext();
        detector = new DefaultDetector();
        parser = new AutoDetectParser(detector);
        context.set(Parser.class, parser);
    }

    public void process(String arg) throws Exception {
        if (arg.equals("-?") || arg.equals("--help")) {
            pipeMode = false;
            usage();
        } else if (arg.equals("-v") || arg.equals("--verbose")) {
            Logger.getRootLogger().setLevel(Level.DEBUG);
        } else if (arg.equals("-g") || arg.equals("--gui")) {
            pipeMode = false;
            TikaGUI.main(new String[0]);
        } else if (arg.equals("--list-parser") || arg.equals("--list-parsers")) {
            pipeMode = false;
            displayParsers(false);
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
        } else if (arg.equals("-z") || arg.equals("--extract")) {
            type = NO_OUTPUT;
            context.set(EmbeddedDocumentExtractor.class, new FileEmbeddedDocumentExtractor());
        } else if (arg.equals("-p") || arg.equals("--port")) {
            portMode = true;
            pipeMode = false;
        } else {
            pipeMode = false;
            metadata = new Metadata();
            if (portMode) {
                new TikaServer(Integer.parseInt(arg)).start();
            } else if (arg.equals("-")) {
                InputStream stream =
                    TikaInputStream.get(new CloseShieldInputStream(System.in));
                try {
                    type.process(stream, System.out);
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
                InputStream input = TikaInputStream.get(url, metadata);
                try {
                    type.process(input, System.out);
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
        out.println("    -?  or --help        Print this usage message");
        out.println("    -v  or --verbose     Print debug level messages");
        out.println();
        out.println("    -g  or --gui         Start the Apache Tika GUI");
        out.println("    -s  or --server      Start the Apache Tika server");
        out.println();
        out.println("    -x  or --xml         Output XHTML content (default)");
        out.println("    -h  or --html        Output HTML content");
        out.println("    -t  or --text        Output plain text content");
        out.println("    -T  or --text-main   Output plain text content (main content only)");
        out.println("    -m  or --metadata    Output only metadata");
        out.println("    -l  or --language    Output only language");
        out.println("    -d  or --detect      Detect document type");
        out.println("    -eX or --encoding=X  Use output encoding X");
        out.println("    -z  or --extract     Extract all attachements into current directory");        
        out.println();
        out.println("    --list-parsers");
        out.println("         List the available document parsers");
        out.println("    --list-parser-details");
        out.println("         List the available document parsers, and their supported mime types");
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
        out.println("    is parsed.");
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
        out.println("    Use the \"-server\" (or \"-s\") option to start the");
        out.println("    Apache Tika server. The server will listen to the");
        out.println("    ports you specify as one or more arguments.");
        out.println();
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
            if (modelClass.getSimpleName().contains("Tika")) {
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
            Parser parser = parsers.get(type);
            if (parser != null) {
                System.out.println("  parser:    " + parser.getClass().getName());
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
            OutputStream output, String method, String encoding)
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, method);
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        if (encoding != null) {
            handler.getTransformer().setOutputProperty(
                    OutputKeys.ENCODING, encoding);
        }
        handler.setResult(new StreamResult(output));
        return handler;
    }

    private static class FileEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        private int count = 0;
        private final TikaConfig config = TikaConfig.getDefaultConfig();

        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);

            if (name == null) {
                name = Integer.toString(count);
            }

            String contentType = metadata.get(Metadata.CONTENT_TYPE);

            if (name.indexOf('.')==-1 && contentType!=null) {
                try {
                    name += config.getMimeRepository().forName(contentType).getExtension();
                } catch (MimeTypeException e) {
                    e.printStackTrace();
                }
            }

            File outputFile = new File(name);
            if (outputFile.exists()) {
                System.err.println("File '"+name+"' already exists; skipping");
                return;
            }

            System.out.println("Extracting '"+name+"' ("+contentType+")");

            FileOutputStream os = new FileOutputStream(outputFile);

            IOUtils.copy(inputStream, os);

            os.close();

            count++;
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
                            OutputStream output = socket.getOutputStream();
                            type.process(socket.getInputStream(), output);
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
    
    private class NoDocumentMetHandler extends DefaultHandler{
        
        private PrintWriter writer;
        
        private boolean metOutput;
        
        public NoDocumentMetHandler(PrintWriter writer){
            this.writer = writer;
            this.metOutput = false;
        }
        
        @Override
        public void endDocument() {
            String[] names = metadata.names();
            Arrays.sort(names);
            for (String name : names) {
                writer.println(name + ": " + metadata.get(name));
            }
            writer.flush();
            this.metOutput = true;
        }        
        
        public boolean metOutput(){
            return this.metOutput;
        }
        
    }

}
