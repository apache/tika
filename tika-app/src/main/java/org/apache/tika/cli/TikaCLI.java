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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.util.Arrays;

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
import org.apache.tika.gui.TikaGUI;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.MetadataHelper;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
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

    private interface OutputType {
        ContentHandler getContentHandler() throws Exception;
    }

    private final OutputType XML = new OutputType() {
        public ContentHandler getContentHandler() throws Exception {
            return getTransformerHandler("xml", encoding);
        }
    };

    private final OutputType HTML = new OutputType() {
        public ContentHandler getContentHandler() throws Exception {
            return getTransformerHandler("html", encoding);
        }
    };

    private final OutputType TEXT = new OutputType() {
        public ContentHandler getContentHandler() throws Exception {
            return new BodyContentHandler(getSystemOutWriter(encoding));
        }
    };

    private final OutputType METADATA = new OutputType() {
        public ContentHandler getContentHandler() throws Exception {
            final PrintWriter writer =
                new PrintWriter(getSystemOutWriter(encoding));
            return new DefaultHandler() {
                public void endDocument() {
                    String[] names = metadata.names();
                    Arrays.sort(names);
                    for (String name : names) {
                        writer.println(name + ": " + metadata.get(name));
                    }

                    writer.flush();
                }
            };
        }
    };

    private ParseContext context;

    private Parser parser;

    private Metadata metadata;

    private OutputType type = XML;

    /**
     * Output character encoding, or <code>null</code> for platform default
     */
    private String encoding = null;

    private boolean pipeMode = true;

    public TikaCLI() throws TransformerConfigurationException {
        context = new ParseContext();
        parser = new AutoDetectParser();
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
        } else if (arg.equals("-m") || arg.equals("--metadata")) {
            type = METADATA;
        } else {
            pipeMode = false;
            metadata = new Metadata();
            if (arg.equals("-")) {
                parser.parse(
                        System.in, type.getContentHandler(),
                        metadata, context);
            } else {
                InputStream input;
                File file = new File(arg);
                if (file.isFile()) {
                    input = MetadataHelper.getInputStream(file, metadata);
                } else {
                    input =
                        MetadataHelper.getInputStream(new URL(arg), metadata);
                }
                try {
                    parser.parse(
                            input, type.getContentHandler(),
                            metadata, context);
                } finally {
                    input.close();
                }
            }
        }
    }

    private void usage() {
        PrintStream out = System.out;
        out.println("usage: tika [option] [file]");
        out.println();
        out.println("Options:");
        out.println("    -?  or --help        Print this usage message");
        out.println("    -v  or --verbose     Print debug level messages");
        out.println("    -g  or --gui         Start the Apache Tika GUI");
        out.println("    -eX or --encoding=X  Use output encoding X");
        out.println("    -x  or --xml         Output XHTML content (default)");
        out.println("    -h  or --html        Output HTML content");
        out.println("    -t  or --text        Output plain text content");
        out.println("    -m  or --metadata    Output only metadata");
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
        out.println("    Use the \"--gui\" (or \"-g\") option to start");
        out.println("    the Apache Tika GUI. You can drag and drop files");
        out.println("    from a normal file explorer to the GUI window to");
        out.println("    extract text content and metadata from the files.");
    }

    /**
     * Returns a {@link System#out} writer with the given output encoding.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return {@link System#out} writer
     * @throws UnsupportedEncodingException
     *         if the configured encoding is not supported
     */
    private static Writer getSystemOutWriter(String encoding)
            throws UnsupportedEncodingException {
        if (encoding != null) {
            return new OutputStreamWriter(System.out, encoding);
        } else if (System.getProperty("os.name")
                .toLowerCase().startsWith("mac os x")) {
            // TIKA-324: Override the default encoding on Mac OS X
            return new OutputStreamWriter(System.out, "UTF-8");
        } else {
            return new OutputStreamWriter(System.out);
        }
    }

    /**
     * Returns a transformer handler that serializes incoming SAX events
     * to XHTML or HTML (depending the given method) using the given output
     * encoding.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-277">TIKA-277</a>
     * @param method "xml" or "html"
     * @param encoding output encoding,
     *                 or <code>null</code> for the platform default
     * @return {@link System#out} transformer handler
     * @throws TransformerConfigurationException
     *         if the transformer can not be created
     */
    private static TransformerHandler getTransformerHandler(
            String method, String encoding)
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
        handler.setResult(new StreamResult(System.out));
        return handler;
    }

}
