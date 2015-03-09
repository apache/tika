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


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
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

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        Set<MediaType> types = new HashSet<MediaType>();
        MediaType type = MediaType.application("mock+xml");
        types.add(type);
        return types;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        Document doc = null;
        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = null;
        try {
            docBuilder = fact.newDocumentBuilder();
            doc = docBuilder.parse(stream);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        }
        Node root = doc.getDocumentElement();
        NodeList actions = root.getChildNodes();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        for (int i = 0; i < actions.getLength(); i++) {
            executeAction(actions.item(i), metadata, xhtml);
        }
        xhtml.endDocument();
    }

    private void executeAction(Node action, Metadata metadata, XHTMLContentHandler xhtml) throws SAXException,
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
        } else {
            throw new IllegalArgumentException("Didn't recognize mock action: "+name);
        }
    }

    private void print(Node action, String name) {
        String content = action.getTextContent();
        if ("print_out".equals(name)) {
            System.out.println(content);
        } else if ("print_err".equals(name)) {
            System.err.println(content);
        } else {
            throw new IllegalArgumentException("must be print_out or print_err");
        }
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

    private void write(Node action, XHTMLContentHandler xhtml) throws SAXException {
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
        List<int[]> ints = new ArrayList<int[]>();

        while (true) {
            int[] intArr = new int[32000];
            ints.add(intArr);
        }
    }

    private void hangHeavy(long maxMillis, long pulseCheckMillis, boolean interruptible) {
        //do some heavy computation and occasionally check for
        //whether time has exceeded maxMillis (see TIKA-1132 for inspiration)
        //or whether the thread was interrupted
        long start = new Date().getTime();
        int lastChecked = 0;
        while (true) {
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                for (int j = 1; j < Integer.MAX_VALUE; j++) {
                    double div = (double) i / (double) j;
                    lastChecked++;
                    if (lastChecked > pulseCheckMillis) {
                        lastChecked = 0;
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
        long start = new Date().getTime();
        long millisRemaining = maxMillis;
        while (true) {
            try {
                Thread.sleep(millisRemaining);
            } catch (InterruptedException e) {
                if (isInterruptible) {
                    return;
                }
            }
            long elapsed = new Date().getTime()-start;
            millisRemaining = maxMillis - elapsed;
            if (millisRemaining <= 0) {
                break;
            }
        }
    }
}