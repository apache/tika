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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
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
import org.apache.tika.parser.AutoDetectParser;
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
        if (args.length == 0) {
            cli.process("-");
        }
    }

    private Parser parser;

    private Metadata metadata;

    private ContentHandler handler;

    public TikaCLI() throws TransformerConfigurationException {
        parser = new AutoDetectParser();
        handler = getXmlContentHandler();
    }

    public void process(String arg) throws Exception {
        if (arg.equals("-?") || arg.equals("--help")) {
            usage();
        } else if (arg.equals("-v") || arg.equals("--verbose")) {
            Logger.getRootLogger().setLevel(Level.DEBUG);
        } else if (arg.equals("-g") || arg.equals("--gui")) {
            TikaGUI.main(new String[0]);
        } else if (arg.equals("-x") || arg.equals("--xml")) {
            handler = getXmlContentHandler();
        } else if (arg.equals("-h") || arg.equals("--html")) {
            handler = getHtmlContentHandler();
        } else if (arg.equals("-t") || arg.equals("--text")) {
            handler = getTextContentHandler();
        } else if (arg.equals("-m") || arg.equals("--metadata")) {
            handler = getMetadataContentHandler();
        } else {
            metadata = new Metadata();
            if (arg.equals("-")) {
                parser.parse(System.in, handler, metadata);
            } else {
                InputStream input;
                File file = new File(arg);
                if (file.isFile()) {
                    metadata.set(Metadata.RESOURCE_NAME_KEY, file.getName());
                    input = new FileInputStream(file);
                } else {
                    URL url = new URL(arg);
                    String path = url.getPath();
                    int slash = path.lastIndexOf('/');
                    String name = path.substring(slash + 1);
                    if (name.length() > 0) {
                        metadata.set(Metadata.RESOURCE_NAME_KEY, name);
                    }
                    input = url.openStream();
                }
                try {
                    parser.parse(input, handler, metadata);
                } finally {
                    input.close();
                }
            }
        }
    }

    private void usage() {
        PrintStream out = System.out;
        out.println("usage: tika [option] file");
        out.println();
        out.println("Options:");
        out.println("    -? or --help       Print this usage message");
        out.println("    -v or --verbose    Print debug level messages");
        out.println("    -g or --gui        Start the Apache Tika GUI");
        out.println("    -x or --xml        Output XHTML content (default)");
        out.println("    -h or --html       Output HTML content");
        out.println("    -t or --text       Output plain text content");
        out.println("    -m or --metadata   Output only metadata");
        out.println();
        out.println("Description:");
        out.println("    Apache Tika will parse the file(s) specified on the");
        out.println("    command line and output the extracted text content");
        out.println("    or metadata to standard output.");
        out.println();
        out.println("    Instead of a file name you can also specify the URL");
        out.println("    of a document to be parsed.");
        out.println();
        out.println("    Use \"-\" as the file name to parse the standard");
        out.println("    input stream.");
        out.println();
        out.println("    Use the \"--gui\" (or \"-g\") option to start");
        out.println("    the Apache Tika GUI. You can drag and drop files");
        out.println("    from a normal file explorer to the GUI window to");
        out.println("    extract text content and metadata from the files.");
    }

    private ContentHandler getXmlContentHandler()
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = (SAXTransformerFactory)
            SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(System.out));
        return handler;
    }

    private ContentHandler getHtmlContentHandler()
            throws TransformerConfigurationException {
        SAXTransformerFactory factory = (SAXTransformerFactory)
        SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "html");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(System.out));
        return handler;
    }

    private ContentHandler getTextContentHandler() {
        return new BodyContentHandler(System.out);
    }

    private ContentHandler getMetadataContentHandler() {
        return new DefaultHandler() {
            public void endDocument() {
                String[] names = metadata.names();
                Arrays.sort(names);
                for (String name : names) {
                    System.out.println(name + ": " + metadata.get(name));
                }
            }
        };
    }

}
