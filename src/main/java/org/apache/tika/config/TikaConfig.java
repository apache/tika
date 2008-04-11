/**
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
package org.apache.tika.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;
import org.apache.tika.parser.Parser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parse xml config file.
 */
public class TikaConfig {

    public static final String DEFAULT_CONFIG_LOCATION = 
        "/org/apache/tika/tika-config.xml";

    private final Map<String, Parser> parsers = new HashMap<String, Parser>();
    
    private static MimeTypes mimeTypes;

    public TikaConfig(String file)
            throws TikaException, IOException, SAXException {
        this(new File(file));
    }

    public TikaConfig(File file)
            throws TikaException, IOException, SAXException {
        this(getBuilder().parse(file));
    }

    public TikaConfig(URL url)
            throws TikaException, IOException, SAXException {
        this(getBuilder().parse(url.toString()));
    }

    public TikaConfig(InputStream stream)
            throws TikaException, IOException, SAXException {
        this(getBuilder().parse(stream));
    }

    public TikaConfig(Document document) throws TikaException, IOException {
        this(document.getDocumentElement());
    }

    public TikaConfig(Element element) throws TikaException, IOException {
        Element mtr = getChild(element, "mimeTypeRepository");
        if (mtr != null) {
            mimeTypes = MimeTypesFactory.create(mtr.getAttribute("resource"));
        }

        NodeList nodes = element.getElementsByTagName("parser");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            String name = node.getAttribute("class");
            try {
                Parser parser = (Parser) Class.forName(name).newInstance();
                NodeList mimes = node.getElementsByTagName("mime");
                for (int j = 0; j < mimes.getLength(); j++) {
                    Element mime = (Element) mimes.item(j);
                    parsers.put(mime.getTextContent().trim(), parser);
                }
            } catch (Exception e) {
                throw new TikaException(
                        "Invalid parser configuration: " + name, e);
            }
        }
    }

    /**
     * Returns the parser instance configured for the given MIME type.
     * Returns <code>null</code> if the given MIME type is unknown.
     *
     * @param mimeType MIME type
     * @return configured Parser instance, or <code>null</code>
     */
    public Parser getParser(String mimeType) {
        return parsers.get(mimeType);
    }

    public Map<String, Parser> getParsers() {
        return parsers;
    }

    public MimeTypes getMimeRepository(){
        return mimeTypes;
    }

    /**
     * Provides a default configuration (TikaConfig).  Currently creates a
     * new instance each time it's called; we may be able to have it
     * return a shared instance once it is completely immutable.
     *
     * @return default configuration
     * @throws TikaException if the default configuration is not available
     */
    public static TikaConfig getDefaultConfig() throws TikaException {
        try {
            InputStream stream =
                TikaConfig.class.getResourceAsStream(DEFAULT_CONFIG_LOCATION);
            return new TikaConfig(stream);
        } catch (IOException e) {
            throw new TikaException("Unable to read default configuration", e);
        } catch (SAXException e) {
            throw new TikaException("Unable to parse default configuration", e);
        }
    }

    private static DocumentBuilder getBuilder() throws TikaException {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new TikaException("XML parser not available", e);
        }
    }

    private static Element getChild(Element element, String name) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && name.equals(child.getNodeName())) {
                return (Element) child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

}
