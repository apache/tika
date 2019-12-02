package org.apache.tika.parser.mock;

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


import static java.nio.charset.StandardCharsets.UTF_8;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This class enables mocking of parser behavior for use in testing
 * wrappers and drivers of parsers.
 * <p>
 * See resources/test-documents/mock/example.xml in tika-parsers/test for the documentation
 * of all the options for this MockParser.
 * <p>
 * Tests for this class are in tika-parsers.
 * <p>
 * See also {@link org.apache.tika.parser.DummyParser} for another option.
 */

public class MockParser extends AbstractParser {


    private static final long serialVersionUID = 1L;
    private static PrintStream ORIG_STDERR;
    private static PrintStream ORIG_STDOUT;
    static {
        ORIG_STDERR = System.err;
        ORIG_STDOUT = System.out;
    }
    private final Random random = new Random();
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        Set<MediaType> types = new HashSet<>();
        MediaType type = MediaType.application("mock+xml");
        types.add(type);
        return types;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        if (Thread.currentThread().isInterrupted()) {
            throw new TikaException("interrupted", new InterruptedException());
        }
        Document doc = null;
        try {
            DocumentBuilder docBuilder = context.getDocumentBuilder();
            doc = docBuilder.parse(stream);
        } catch (SAXException e) {
            //to distinguish between SAX on read vs SAX while writing
            throw new IOExceptionWithCause(e);
        }
        Node root = doc.getDocumentElement();
        NodeList actions = root.getChildNodes();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        for (int i = 0; i < actions.getLength(); i++) {
            executeAction(actions.item(i), metadata, context, xhtml);
        }
        xhtml.endDocument();
    }

    private void executeAction(Node action, Metadata metadata, ParseContext context,
                               XHTMLContentHandler xhtml) throws SAXException,
            IOException, TikaException {

        if (action.getNodeType() != 1) {
            return;
        }

        String name = action.getNodeName();
        if ("metadata".equals(name)) {
            metadata(action, metadata);
        } else if("write".equals(name)) {
            write(action, xhtml);
        } else if ("throw".equals(name)) {
            throwIt(action);
        } else if ("hang".equals(name)) {
            hang(action);
        } else if ("oom".equals(name)) {
            kabOOM();
        } else if ("print_out".equals(name) || "print_err".equals(name)){
            print(action, name);
        } else if ("embedded".equals(name)) {
            handleEmbedded(action, xhtml, context);
        } else if ("throwIllegalChars".equals(name)) {
            throwIllegalChars();
        } else if ("system_exit".equals(name)) {
            System.exit(1);
        } else if ("thread_interrupt".equals(name)) {
            Thread.currentThread().interrupt();
        } else {
            throw new IllegalArgumentException("Didn't recognize mock action: "+name);
        }
    }

    private void throwIllegalChars() throws IOException {
        throw new IOException("Can't say \u0000 in xml or \u0001 or \u0002 or \u0003");
    }

    private void handleEmbedded(Node action, XHTMLContentHandler handler, ParseContext context)
            throws TikaException, SAXException, IOException {
        String fileName = "";
        String contentType = "";
        NamedNodeMap attrs = action.getAttributes();
        if (attrs != null) {
            Node n = attrs.getNamedItem("filename");
            if (n != null) {
                fileName = n.getNodeValue();
            }
            n = attrs.getNamedItem("content-type");
            if (n != null) {
                contentType = n.getNodeValue();
            }
        }

        String embeddedText = action.getTextContent();
        EmbeddedDocumentExtractor extractor = getEmbeddedDocumentExtractor(context);
        Metadata m = new Metadata();
        m.set(TikaMetadataKeys.RESOURCE_NAME_KEY, fileName);
        if (! "".equals(contentType)) {
            m.set(Metadata.CONTENT_TYPE, contentType);
        }
        InputStream is = new ByteArrayInputStream(embeddedText.getBytes(UTF_8));

        extractor.parseEmbedded(
                is,
                new EmbeddedContentHandler(handler),
                m, true);


    }

    protected EmbeddedDocumentExtractor getEmbeddedDocumentExtractor(ParseContext context) {
        EmbeddedDocumentExtractor extractor =
                context.get(EmbeddedDocumentExtractor.class);
        if (extractor == null) {
            Parser p = context.get(Parser.class);
            if (p == null) {
                context.set(Parser.class, new MockParser());
            }
            extractor = new ParsingEmbeddedDocumentExtractor(context);
        }
        return extractor;
    }

    private void print(Node action, String name) throws IOException {
        String content = action.getTextContent();
        boolean isStatic = (action.getAttributes().getNamedItem("static") == null) ? false : true;
        Node rand = action.getAttributes().getNamedItem("random");
        int randLength = -1;
        if (rand != null) {
            randLength = Integer.parseInt(rand.getNodeValue());
        }
        byte[] contentBytes = getBytes(content, randLength);
        if (isStatic) {
            if ("print_out".equals(name)) {
                ORIG_STDOUT.write(contentBytes);
            } else if ("print_err".equals(name)) {
                ORIG_STDERR.write(contentBytes);
            } else {
                throw new IllegalArgumentException("must be print_out or print_err");
            }
        } else {
            if ("print_out".equals(name)) {
                System.out.write(contentBytes);
            } else if ("print_err".equals(name)) {
                System.err.write(contentBytes);
            } else {
                throw new IllegalArgumentException("must be print_out or print_err");
            }
        }
    }

    private byte[] getBytes(String content, int randLength) {
        if (randLength < 0) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
        byte[] bytes = new byte[randLength];
        random.nextBytes(bytes);
        return bytes;
    }

    private void hang(Node action) {
        boolean interruptible = true;
        boolean heavy = false;
        long millis = -1;
        long pulseMillis = -1;
        NamedNodeMap attrs = action.getAttributes();
        Node iNode = attrs.getNamedItem("interruptible");
        if (iNode != null) {
            interruptible = ("true".equals(iNode.getNodeValue()));
        }
        Node hNode = attrs.getNamedItem("heavy");
        if (hNode != null) {
            heavy = ("true".equals(hNode.getNodeValue()));
        }

        Node mNode = attrs.getNamedItem("millis");
        if (mNode == null) {
            throw new RuntimeException("Must specify \"millis\" attribute for hang.");
        }
        String millisString = mNode.getNodeValue();
        try {
            millis = Long.parseLong(millisString);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Value for \"millis\" attribute must be a long.");
        }

        if (heavy) {
            Node pNode = attrs.getNamedItem("pulse_millis");
            if (pNode == null) {
                throw new RuntimeException("Must specify attribute \"pulse_millis\" if the hang is \"heavy\"");
            }
            String pulseMillisString = mNode.getNodeValue();
            try {
                pulseMillis = Long.parseLong(pulseMillisString);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Value for \"millis\" attribute must be a long.");
            }
        }
        if (heavy) {
            hangHeavy(millis, pulseMillis, interruptible);
        } else {
            sleep(millis, interruptible);
        }
    }

    private void throwIt(Node action) throws IOException,
            SAXException, TikaException {
        NamedNodeMap attrs = action.getAttributes();
        String className = attrs.getNamedItem("class").getNodeValue();
        String msg = action.getTextContent();
        throwIt(className, msg);
    }

    private void metadata(Node action, Metadata metadata) {
        NamedNodeMap attrs = action.getAttributes();
        //throws npe unless there is a name
        String name = attrs.getNamedItem("name").getNodeValue();
        String value = action.getTextContent();
        Node actionType = attrs.getNamedItem("action");
        if (actionType == null) {
            metadata.add(name, value);
        } else {
            if ("set".equals(actionType.getNodeValue())) {
                metadata.set(name, value);
            } else {
                metadata.add(name, value);
            }
        }
    }

    protected void write(Node action, XHTMLContentHandler xhtml) throws SAXException {
        NamedNodeMap attrs = action.getAttributes();
        Node eNode = attrs.getNamedItem("element");
        String elementType = "p";
        if (eNode != null) {
            elementType = eNode.getTextContent();
        }
        String text = action.getTextContent();
        xhtml.startElement(elementType);
        xhtml.characters(text);
        xhtml.endElement(elementType);
    }


    private void throwIt(String className, String msg) throws IOException,
            SAXException, TikaException {
        Throwable t = null;
        if (msg == null || msg.equals("")) {
            try {
                t = (Throwable) Class.forName(className).newInstance();
            } catch (Exception e) {
                throw new RuntimeException("couldn't create throwable class:"+className, e);
            }
        } else {
            try {
                Class<?> clazz = Class.forName(className);
                Constructor<?> con = clazz.getConstructor(String.class);
                t = (Throwable) con.newInstance(msg);
            } catch (Exception e) {
                throw new RuntimeException("couldn't create throwable class:" + className, e);
            }
        }
        if (t instanceof SAXException) {
            throw (SAXException)t;
        } else if (t instanceof IOException) {
            throw (IOException) t;
        } else if (t instanceof TikaException) {
            throw (TikaException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else {
            //wrap the throwable in a RuntimeException
            throw new RuntimeException(t);
        }
    }

    private void kabOOM() {
        List<int[]> ints = new ArrayList<>();

        while (true) {
            int[] intArr = new int[32000];
            ints.add(intArr);
        }
    }

    private void hangHeavy(long maxMillis, long pulseCheckMillis, boolean interruptible) {
        //do some heavy computation and occasionally check for
        //whether time has exceeded maxMillis (see TIKA-1132 for inspiration)
        //or whether the thread was interrupted.
        //By creating a new Date in the inner loop, we're also intentionally
        //triggering the gc most likely.
        long start = new Date().getTime();
        long lastChecked = start;
        while (true) {
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                for (int j = 1; j < Integer.MAX_VALUE; j++) {
                    double div = (double) i / (double) j;

                    long elapsedSinceLastCheck = new Date().getTime()-lastChecked;
                    if (elapsedSinceLastCheck > pulseCheckMillis) {
                        lastChecked = new Date().getTime();
                        if (interruptible && Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        long elapsed = new Date().getTime()-start;
                        if (elapsed > maxMillis) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private void sleep(long maxMillis, boolean isInterruptible) {
        long start = System.currentTimeMillis();
        long millisRemaining = maxMillis;
        while (true) {
            try {
                Thread.sleep(millisRemaining);
            } catch (InterruptedException e) {
                if (isInterruptible) {
                    return;
                }
            }
            long elapsed = System.currentTimeMillis()-start;
            millisRemaining = maxMillis - elapsed;
            if (millisRemaining <= 0) {
                break;
            }
        }
    }

}
