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
package org.apache.tika.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.spi.ServiceRegistry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parse xml config file.
 */
public class TikaConfig {

    private final CompositeParser parser;
    
    private final MimeTypes mimeTypes;

    private TikaConfig(CompositeParser parser, MimeTypes mimeTypes) {
        this.parser = parser;
        this.mimeTypes = mimeTypes;
    }

    private TikaConfig(CompositeParser parser) {
        this(parser, MimeTypes.getDefaultMimeTypes());
    }

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

    /**
     * @deprecated This method will be removed in Apache Tika 1.0
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
     */
    public TikaConfig(InputStream stream, Parser delegate)
            throws TikaException, IOException, SAXException {
        this(stream);
    }

    public TikaConfig(Document document) throws TikaException, IOException {
        this(document.getDocumentElement());
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
     */
    public TikaConfig(Document document, Parser delegate)
            throws TikaException, IOException {
        this(document);
    }

    public TikaConfig(Element element) throws TikaException, IOException {
        Element mtr = getChild(element, "mimeTypeRepository");
        if (mtr != null && mtr.hasAttribute("resource")) {
            mimeTypes = MimeTypesFactory.create(mtr.getAttribute("resource"));
        } else {
            mimeTypes = MimeTypes.getDefaultMimeTypes();
        }

        List<Parser> parsers = new ArrayList<Parser>();
        NodeList nodes = element.getElementsByTagName("parser");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            String name = node.getAttribute("class");

            try {
                Class<?> parserClass = Class.forName(name);
                Object instance = parserClass.newInstance();
                if (!(instance instanceof Parser)) {
                    throw new TikaException(
                            "Configured class is not a Tika Parser: " + name);
                }
                Parser parser = (Parser) instance;

                NodeList mimes = node.getElementsByTagName("mime");
                if (mimes.getLength() > 0) {
                    Set<MediaType> types = new HashSet<MediaType>();
                    for (int j = 0; j < mimes.getLength(); j++) {
                        String mime = getText(mimes.item(j));
                        MediaType type = MediaType.parse(mime);
                        if (type != null) {
                            types.add(type);
                        } else {
                            throw new TikaException(
                                    "Invalid media type name: " + mime);
                        }
                    }
                    parser = ParserDecorator.withTypes(parser, types);
                }

                parsers.add(parser);
            } catch (ClassNotFoundException e) {
                throw new TikaException(
                        "Configured parser class not found: " + name, e);
            } catch (IllegalAccessException e) {
                throw new TikaException(
                        "Unable to access a parser class: " + name, e);
            } catch (InstantiationException e) {
                throw new TikaException(
                        "Unable to instantiate a parser class: " + name, e);
            }
        }
        this.parser =
            new CompositeParser(mimeTypes.getMediaTypeRegistry(), parsers);
    }

    /**
     * Creates a Tika configuration from the built-in media type rules
     * and all the {@link Parser} implementations available through the
     * {@link ServiceRegistry service provider mechanism} in the given
     * class loader.
     *
     * @since Apache Tika 0.8
     * @param loader the class loader through which parser implementations
     *               are loaded, or <code>null</code> for no parsers
     * @throws MimeTypeException if the built-in media type rules are broken
     * @throws IOException  if the built-in media type rules can not be read
     */
    public TikaConfig(ClassLoader loader)
            throws MimeTypeException, IOException {
        this(new DefaultParser(loader));
    }

    /**
     * Creates a Tika configuration from the built-in media type rules
     * and all the {@link Parser} implementations available through the
     * {@link ServiceRegistry service provider mechanism} in the context
     * class loader of the current thread.
     *
     * @throws MimeTypeException if the built-in media type rules are broken
     * @throws IOException  if the built-in media type rules can not be read
     */
    public TikaConfig() throws MimeTypeException, IOException {
        this(new DefaultParser());
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
     */
    public TikaConfig(Element element, Parser delegate)
            throws TikaException, IOException {
        this(element);
    }

    private String getText(Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            return node.getNodeValue();
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            StringBuilder builder = new StringBuilder();
            NodeList list = node.getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                builder.append(getText(list.item(i)));
            }
            return builder.toString();
        } else {
            return "";
        }
    }

    /**
     * @deprecated Use the {@link #getParser()} method instead
     */
    public Parser getParser(MediaType mimeType) {
        return parser.getParsers().get(mimeType);
    }

    /**
     * Returns the configured parser instance.
     *
     * @return configured parser
     */
    public Parser getParser() {
        return parser;
    }

    /**
     * @deprecated Use the {@link #getParser()} method instead
     */
    public Map<MediaType, Parser> getParsers() {
        return parser.getParsers();
    }

    public MimeTypes getMimeRepository(){
        return mimeTypes;
    }

    public MediaTypeRegistry getMediaTypeRegistry() {
        return mimeTypes.getMediaTypeRegistry();
    }

    /**
     * Provides a default configuration (TikaConfig).  Currently creates a
     * new instance each time it's called; we may be able to have it
     * return a shared instance once it is completely immutable.
     *
     * @return default configuration
     */
    public static TikaConfig getDefaultConfig() {
        try {
            return new TikaConfig();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to read default configuration", e);
        } catch (TikaException e) {
            throw new RuntimeException(
                    "Unable to access default configuration", e);
        }
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
     */
    public static TikaConfig getDefaultConfig(Parser delegate)
            throws TikaException {
        return getDefaultConfig();
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
