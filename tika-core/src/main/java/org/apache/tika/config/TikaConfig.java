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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.spi.ServiceRegistry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;
import org.apache.tika.parser.AutoDetectParser;
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

    private static MimeTypes getDefaultMimeTypes() {
        return MimeTypes.getDefaultMimeTypes();
    }

    private static Detector getDefaultDetector(
            MimeTypes types, ServiceLoader loader) {
        return new DefaultDetector(types, loader);
    }

    private static CompositeParser getDefaultParser(
            MimeTypes types, ServiceLoader loader) {
        return new DefaultParser(types.getMediaTypeRegistry(), loader);
    }

    private final CompositeParser parser;
    private final Detector detector;

    private final MimeTypes mimeTypes;

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
        this(url, ServiceLoader.getContextClassLoader());
    }

    public TikaConfig(URL url, ClassLoader loader)
            throws TikaException, IOException, SAXException {
        this(getBuilder().parse(url.toString()).getDocumentElement(), loader);
    }

    public TikaConfig(InputStream stream)
            throws TikaException, IOException, SAXException {
        this(getBuilder().parse(stream));
    }

    public TikaConfig(Document document) throws TikaException, IOException {
        this(document.getDocumentElement());
    }

    public TikaConfig(Element element) throws TikaException, IOException {
        this(element, new ServiceLoader());
    }

    public TikaConfig(Element element, ClassLoader loader)
            throws TikaException, IOException {
        this(element, new ServiceLoader(loader));
    }

    private TikaConfig(Element element, ServiceLoader loader)
            throws TikaException, IOException {
        this.mimeTypes = typesFromDomElement(element);
        this.detector = detectorFromDomElement(element, mimeTypes, loader);
        this.parser = parserFromDomElement(element, mimeTypes, loader);
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
        ServiceLoader serviceLoader = new ServiceLoader(loader);
        this.mimeTypes = getDefaultMimeTypes();
        this.detector = getDefaultDetector(mimeTypes, serviceLoader);
        this.parser = getDefaultParser(mimeTypes, serviceLoader);
    }

    /**
     * Creates a default Tika configuration.
     * First checks whether an XML config file is specified, either in
     * <ol>
     * <li>System property "tika.config", or</li>
     * <li>Environment variable TIKA_CONFIG</li>
     * </ol>
     * <p>If one of these have a value, try to resolve it relative to file
     * system or classpath.</p>
     * <p>If XML config is not specified, initialize from the built-in media
     * type rules and all the {@link Parser} implementations available through
     * the {@link ServiceRegistry service provider mechanism} in the context
     * class loader of the current thread.</p>
     *
     * @throws IOException if the configuration can not be read
     * @throws TikaException if problem with MimeTypes or parsing XML config
     */
    public TikaConfig() throws TikaException, IOException {
        ServiceLoader loader = new ServiceLoader();

        String config = System.getProperty("tika.config");
        if (config == null) {
            config = System.getenv("TIKA_CONFIG");
        }

        if (config == null) {
            this.mimeTypes = getDefaultMimeTypes();
            this.parser = getDefaultParser(mimeTypes, loader);
            this.detector = getDefaultDetector(mimeTypes, loader);
        } else {
            // Locate the given configuration file
            InputStream stream = null;
            File file = new File(config);
            if (file.isFile()) {
                stream = new FileInputStream(file);
            }
            if (stream == null) {
                try {
                    stream = new URL(config).openStream();
                } catch (IOException ignore) {
                }
            }
            if (stream == null) {
                stream = loader.getResourceAsStream(config);
            }
            if (stream == null) {
                throw new TikaException(
                        "Specified Tika configuration not found: " + config);
            }

            try {
                Element element =
                        getBuilder().parse(stream).getDocumentElement();
                this.mimeTypes = typesFromDomElement(element);
                this.parser =
                        parserFromDomElement(element, mimeTypes, loader);
                this.detector =
                        detectorFromDomElement(element, mimeTypes, loader);
            } catch (SAXException e) {
                throw new TikaException(
                        "Specified Tika configuration has syntax errors: "
                                + config, e);
            } finally {
                stream.close();
            }
        }
    }

    private static String getText(Node node) {
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
     * Returns the configured detector instance.
     *
     * @return configured detector
     */
    public Detector getDetector() {
        return detector;
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

    private static MimeTypes typesFromDomElement(Element element)
            throws TikaException, IOException {
        Element mtr = getChild(element, "mimeTypeRepository");
        if (mtr != null && mtr.hasAttribute("resource")) {
            return MimeTypesFactory.create(mtr.getAttribute("resource"));
        } else {
            return getDefaultMimeTypes();
        }
    }

    private static CompositeParser parserFromDomElement(
            Element element, MimeTypes mimeTypes, ServiceLoader loader)
            throws TikaException, IOException {
        List<Parser> parsers = new ArrayList<Parser>();
        NodeList nodes = element.getElementsByTagName("parser");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            String name = node.getAttribute("class");

            try {
                Class<? extends Parser> parserClass =
                        loader.getServiceClass(Parser.class, name);
                // https://issues.apache.org/jira/browse/TIKA-866
                if (AutoDetectParser.class.isAssignableFrom(parserClass)) {
                    throw new TikaException(
                            "AutoDetectParser not supported in a <parser>"
                            + " configuration element: " + name);
                }
                Parser parser = parserClass.newInstance();

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
                        "Unable to find a parser class: " + name, e);
            } catch (IllegalAccessException e) {
                throw new TikaException(
                        "Unable to access a parser class: " + name, e);
            } catch (InstantiationException e) {
                throw new TikaException(
                        "Unable to instantiate a parser class: " + name, e);
            }
        }
        if (parsers.isEmpty()) {
            return getDefaultParser(mimeTypes, loader);
        } else {
            MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
            return new CompositeParser(registry, parsers);
        }
    }

    private static Detector detectorFromDomElement(
          Element element, MimeTypes mimeTypes, ServiceLoader loader)
          throws TikaException, IOException {
       List<Detector> detectors = new ArrayList<Detector>();
       NodeList nodes = element.getElementsByTagName("detector");
       for (int i = 0; i < nodes.getLength(); i++) {
           Element node = (Element) nodes.item(i);
           String name = node.getAttribute("class");

           try {
               Class<? extends Detector> detectorClass =
                       loader.getServiceClass(Detector.class, name);
               detectors.add(detectorClass.newInstance());
           } catch (ClassNotFoundException e) {
               throw new TikaException(
                       "Unable to find a detector class: " + name, e);
           } catch (IllegalAccessException e) {
               throw new TikaException(
                       "Unable to access a detector class: " + name, e);
           } catch (InstantiationException e) {
               throw new TikaException(
                       "Unable to instantiate a detector class: " + name, e);
           }
       }
       if (detectors.isEmpty()) {
           return getDefaultDetector(mimeTypes, loader);
       } else {
           MediaTypeRegistry registry = mimeTypes.getMediaTypeRegistry();
           return new CompositeDetector(registry, detectors);
       }
    }
}
