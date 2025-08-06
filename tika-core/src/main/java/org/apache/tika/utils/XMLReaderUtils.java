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

package org.apache.tika.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.OfflineContentHandler;


/**
 * Utility functions for reading XML.
 */
public class XMLReaderUtils implements Serializable {

    /**
     * Default size for the pool of SAX Parsers
     * and the pool of DOM builders
     */
    public static final int DEFAULT_POOL_SIZE = 10;
    public static final int DEFAULT_MAX_ENTITY_EXPANSIONS = 20;
    public static final int DEFAULT_NUM_REUSES = 100;
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 6110455808615143122L;
    private static final Logger LOG = LoggerFactory.getLogger(XMLReaderUtils.class);
    private static final String XERCES_SECURITY_MANAGER = "org.apache.xerces.util.SecurityManager";
    private static final String XERCES_SECURITY_MANAGER_PROPERTY =
            "http://apache.org/xml/properties/security-manager";

    private static final AtomicBoolean HAS_WARNED_STAX = new AtomicBoolean(false);
    private static final ContentHandler IGNORING_CONTENT_HANDLER = new DefaultHandler();
    private static final DTDHandler IGNORING_DTD_HANDLER = new DTDHandler() {
        @Override
        public void notationDecl(String name, String publicId, String systemId)
                throws SAXException {

        }

        @Override
        public void unparsedEntityDecl(String name, String publicId, String systemId,
                                       String notationName) throws SAXException {

        }
    };
    private static final ErrorHandler IGNORING_ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) throws SAXException {

        }

        @Override
        public void error(SAXParseException exception) throws SAXException {

        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {

        }
    };
    private static final String JAXP_ENTITY_EXPANSION_LIMIT_KEY = "jdk.xml.entityExpansionLimit";
    //TODO: figure out if the rw lock is any better than a simple lock
    //these lock the pool arrayblocking queues so that there isn't a race condition
    //of trying to acquire a parser while the pool is being resized
    private static final ReentrantReadWriteLock SAX_POOL_LOCK = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock DOM_POOL_LOCK = new ReentrantReadWriteLock();
    private static final AtomicInteger POOL_GENERATION = new AtomicInteger();
    private static final EntityResolver IGNORING_SAX_ENTITY_RESOLVER =
            (publicId, systemId) -> new InputSource(new StringReader(""));
    /**
     * Parser pool size
     */
    private static int POOL_SIZE = DEFAULT_POOL_SIZE;
    private static int MAX_NUM_REUSES = DEFAULT_NUM_REUSES;
    private static long LAST_LOG = -1;
    private static volatile int MAX_ENTITY_EXPANSIONS = determineMaxEntityExpansions();
    private static ArrayBlockingQueue<PoolSAXParser> SAX_PARSERS =
            new ArrayBlockingQueue<>(POOL_SIZE);
    private static ArrayBlockingQueue<PoolDOMBuilder> DOM_BUILDERS =
            new ArrayBlockingQueue<>(POOL_SIZE);

    static {
        try {
            setPoolSize(POOL_SIZE);
        } catch (TikaException e) {
            throw new RuntimeException("problem initializing SAXParser and DOMBuilder pools", e);
        }
    }

    private static int determineMaxEntityExpansions() {
        String expansionLimit = System.getProperty(JAXP_ENTITY_EXPANSION_LIMIT_KEY);
        if (expansionLimit != null) {
            try {
                return Integer.parseInt(expansionLimit);
            } catch (NumberFormatException e) {
                LOG.warn(
                        "Couldn't parse an integer for the entity expansion limit: {}; " +
                                "backing off to default: {}",
                        expansionLimit, DEFAULT_MAX_ENTITY_EXPANSIONS);
            }
        }
        return DEFAULT_MAX_ENTITY_EXPANSIONS;
    }

    /**
     * Returns the XMLReader specified in this parsing context. If a reader
     * is not explicitly specified, then one is created using the specified
     * or the default SAX parser.
     *
     * @return XMLReader
     * @throws TikaException
     * @see #getSAXParser()
     * @since Apache Tika 1.13
     */
    public static XMLReader getXMLReader() throws TikaException {
        XMLReader reader;
        try {
            reader = getSAXParser().getXMLReader();
        } catch (SAXException e) {
            throw new TikaException("Unable to create an XMLReader", e);
        }
        reader.setEntityResolver(IGNORING_SAX_ENTITY_RESOLVER);
        return reader;
    }

    /**
     * Returns the SAX parser specified in this parsing context. If a parser
     * is not explicitly specified, then one is created using the specified
     * or the default SAX parser factory.
     * <p>
     * If you call reset() on the parser, make sure to replace the
     * SecurityManager which will be cleared by xerces2 on reset().
     * </p>
     *
     * @return SAX parser
     * @throws TikaException if a SAX parser could not be created
     * @see #getSAXParserFactory()
     * @since Apache Tika 0.8
     */
    public static SAXParser getSAXParser() throws TikaException {
        try {
            SAXParser parser = getSAXParserFactory().newSAXParser();
            trySetXercesSecurityManager(parser);
            return parser;
        } catch (ParserConfigurationException e) {
            throw new TikaException("Unable to configure a SAX parser", e);
        } catch (SAXException e) {
            throw new TikaException("Unable to create a SAX parser", e);
        }
    }

    /**
     * Returns the SAX parser factory specified in this parsing context.
     * If a factory is not explicitly specified, then a default factory
     * instance is created and returned. The default factory instance is
     * configured to be namespace-aware, not validating, and to use
     * {@link XMLConstants#FEATURE_SECURE_PROCESSING secure XML processing}.
     *
     * @return SAX parser factory
     * @since Apache Tika 0.8
     */
    public static SAXParserFactory getSAXParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        if (LOG.isDebugEnabled()) {
            LOG.debug("SAXParserFactory class {}", factory.getClass());
        }
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        trySetSAXFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetSAXFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetSAXFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetSAXFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        trySetSAXFeature(factory, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar",
                false);

        return factory;
    }

    /**
     * Returns the DOM builder factory specified in this parsing context.
     * If a factory is not explicitly specified, then a default factory
     * instance is created and returned. The default factory instance is
     * configured to be namespace-aware and to apply reasonable security
     * features.
     *
     * @return DOM parser factory
     * @since Apache Tika 1.13
     */
    public static DocumentBuilderFactory getDocumentBuilderFactory() {
        //borrowed from Apache POI
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        if (LOG.isDebugEnabled()) {
            LOG.debug("DocumentBuilderFactory class {}", factory.getClass());
        }

        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        factory.setValidating(false);

        trySetSAXFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetSAXFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetSAXFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetSAXFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        trySetSAXFeature(factory, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar",
                false);
        trySetXercesSecurityManager(factory);
        return factory;
    }

    /**
     * Returns the DOM builder specified in this parsing context.
     * If a builder is not explicitly specified, then a builder
     * instance is created and returned. The builder instance is
     * configured to apply an {@link #IGNORING_SAX_ENTITY_RESOLVER},
     * and it sets the ErrorHandler to <code>null</code>.
     *
     * @return DOM Builder
     * @since Apache Tika 1.13
     */
    public static DocumentBuilder getDocumentBuilder() throws TikaException {
        try {
            DocumentBuilderFactory documentBuilderFactory = getDocumentBuilderFactory();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver(IGNORING_SAX_ENTITY_RESOLVER);
            documentBuilder.setErrorHandler(null);
            return documentBuilder;
        } catch (ParserConfigurationException e) {
            throw new TikaException("XML parser not available", e);
        }
    }

    /**
     * Returns the StAX input factory specified in this parsing context.
     * If a factory is not explicitly specified, then a default factory
     * instance is created and returned. The default factory instance is
     * configured to be namespace-aware and to apply reasonable security
     * precautions.
     *
     * @return StAX input factory
     * @since Apache Tika 1.13
     */
    public static XMLInputFactory getXMLInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        if (LOG.isDebugEnabled()) {
            LOG.debug("XMLInputFactory class {}", factory.getClass());
        }
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tryToSetStaxProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
        tryToSetStaxProperty(factory, XMLInputFactory.IS_VALIDATING, false);
        tryToSetStaxProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        tryToSetStaxProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        trySetStaxSecurityManager(factory);
        return factory;
    }

    private static void trySetTransformerAttribute(TransformerFactory transformerFactory,
                                                   String attribute, String value) {
        try {
            transformerFactory.setAttribute(attribute, value);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Transformer Attribute unsupported: {}", attribute, e);
        } catch (AbstractMethodError ame) {
            LOG.warn(
                    "Cannot set Transformer attribute because outdated XML parser in classpath: {}",
                    attribute, ame);
        }
    }

    private static void trySetSAXFeature(SAXParserFactory saxParserFactory, String feature,
                                         boolean enabled) {
        try {
            saxParserFactory.setFeature(feature, enabled);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("SAX Feature unsupported: {}", feature, e);
        } catch (AbstractMethodError ame) {
            LOG.warn("Cannot set SAX feature because outdated XML parser in classpath: {}", feature,
                    ame);
        }
    }

    private static void trySetSAXFeature(DocumentBuilderFactory documentBuilderFactory,
                                         String feature, boolean enabled) {
        try {
            documentBuilderFactory.setFeature(feature, enabled);
        } catch (Exception e) {
            LOG.warn("SAX Feature unsupported: {}", feature, e);
        } catch (AbstractMethodError ame) {
            LOG.warn("Cannot set SAX feature because outdated XML parser in classpath: {}", feature,
                    ame);
        }
    }

    private static void tryToSetStaxProperty(XMLInputFactory factory, String key, boolean value) {
        try {
            factory.setProperty(key, value);
        } catch (IllegalArgumentException e) {
            LOG.warn("StAX Feature unsupported: {}", key, e);
        }
    }

    /**
     * Returns a new transformer
     * <p>
     * The transformer instance is configured to to use
     * {@link XMLConstants#FEATURE_SECURE_PROCESSING secure XML processing}.
     *
     * @return Transformer
     * @throws TikaException when the transformer can not be created
     * @since Apache Tika 1.17
     */
    public static Transformer getTransformer() throws TikaException {
        TransformerFactory transformerFactory = getTransformerFactory();
        try {
            return transformerFactory.newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new TikaException("Transformer not available", e);
        }
    }

    /**
     * Returns a TransformerFactory. The factory is configured with
     * {@link XMLConstants#FEATURE_SECURE_PROCESSING secure XML processing} and other
     * settings to prevent XXE.
     *
     * @return TransformerFactory
     * @throws TikaException
     */
    public static TransformerFactory getTransformerFactory() throws TikaException {
        try {

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySetTransformerAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            trySetTransformerAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            return transformerFactory;
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            throw new TikaException("Transformer not available", e);
        }
    }

    /**
     * Returns a SAXTransformerFactory. The factory is configured with
     * {@link XMLConstants#FEATURE_SECURE_PROCESSING secure XML processing} and other
     * settings to prevent XXE.
     *
     * @return TransformerFactory
     * @throws TikaException
     */
    public static SAXTransformerFactory getSAXTransformerFactory() throws TikaException {
        try {

            SAXTransformerFactory transformerFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySetTransformerAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            trySetTransformerAttribute(transformerFactory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            return transformerFactory;
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            throw new TikaException("Transformer not available", e);
        }
    }

    /**
     * This checks context for a user specified {@link DocumentBuilder}.
     * If one is not found, this reuses a DocumentBuilder from the pool.
     *
     * @param is      InputStream to parse
     * @param context context to use
     * @return a document
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     * @since Apache Tika 1.19
     */
    public static Document buildDOM(InputStream is, ParseContext context)
            throws TikaException, IOException, SAXException {
        DocumentBuilder builder = context.get(DocumentBuilder.class);
        PoolDOMBuilder poolBuilder = null;
        if (builder == null) {
            if (POOL_SIZE == 0) {
                builder = getDocumentBuilder();
            } else {
                poolBuilder = acquireDOMBuilder();
                if (poolBuilder != null) {
                    builder = poolBuilder.getDocumentBuilder();
                } else {
                    builder = getDocumentBuilder();
                }
            }
        }

        try {
            return builder.parse(is);
        } finally {
            releaseDOMBuilder(poolBuilder);
        }
    }

    /**
     * This checks context for a user specified {@link DocumentBuilder}.
     * If one is not found, this reuses a DocumentBuilder from the pool.
     *
     * @param reader  reader (character stream) to parse
     * @param context context to use
     * @return a document
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     * @since Apache Tika 2.5
     */
    public static Document buildDOM(Reader reader, ParseContext context)
            throws TikaException, IOException, SAXException {
        DocumentBuilder builder = context.get(DocumentBuilder.class);
        PoolDOMBuilder poolBuilder = null;
        if (builder == null) {
            if (POOL_SIZE == 0) {
                builder = getDocumentBuilder();
            } else {
                poolBuilder = acquireDOMBuilder();
                if (poolBuilder != null) {
                    builder = poolBuilder.getDocumentBuilder();
                } else {
                    builder = getDocumentBuilder();
                }
            }
        }

        try {
            return builder.parse(new InputSource(reader));
        } finally {
            releaseDOMBuilder(poolBuilder);
        }
    }

    /**
     * Builds a Document with a DocumentBuilder from the pool
     *
     * @param path path to parse
     * @return a document
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     * @since Apache Tika 1.19.1
     */
    public static Document buildDOM(Path path) throws TikaException, IOException, SAXException {
        try (InputStream is = Files.newInputStream(path)) {
            return buildDOM(is);
        }
    }

    /**
     * Builds a Document with a DocumentBuilder from the pool
     *
     * @param uriString uriString to process
     * @return a document
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     * @since Apache Tika 1.19.1
     */
    public static Document buildDOM(String uriString)
            throws TikaException, IOException, SAXException {
        PoolDOMBuilder poolBuilder = null;
        DocumentBuilder builder = null;
        if (POOL_SIZE == 0) {
            builder = getDocumentBuilder();
        } else {
            poolBuilder = acquireDOMBuilder();
            if (poolBuilder != null) {
                builder = poolBuilder.getDocumentBuilder();
            } else {
                builder = getDocumentBuilder();
            }
        }

        try {
            return builder.parse(uriString);
        } finally {
            releaseDOMBuilder(poolBuilder);
        }
    }

    /**
     * Builds a Document with a DocumentBuilder from the pool
     *
     * @return a document
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     * @since Apache Tika 1.19.1
     */
    public static Document buildDOM(InputStream is)
            throws TikaException, IOException, SAXException {
        PoolDOMBuilder poolBuilder = null;
        DocumentBuilder builder = null;
        if (POOL_SIZE == 0) {
            builder = getDocumentBuilder();
        } else {
            poolBuilder = acquireDOMBuilder();
            if (poolBuilder != null) {
                builder = poolBuilder.getDocumentBuilder();
            } else {
                builder = getDocumentBuilder();
            }
        }

        try {
            return builder.parse(is);
        } finally {
            releaseDOMBuilder(poolBuilder);
        }
    }

    /**
     * This checks context for a user specified {@link SAXParser}.
     * If one is not found, this reuses a SAXParser from the pool.
     *
     * @param is             InputStream to parse
     * @param contentHandler handler to use; this wraps a {@link OfflineContentHandler}
     *                       to the content handler as an extra layer of defense against
     *                       external entity vulnerabilities
     * @param context        context to use
     * @return
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     * @since Apache Tika 1.19
     */
    public static void parseSAX(InputStream is, ContentHandler contentHandler, ParseContext context)
            throws TikaException, IOException, SAXException {
        SAXParser saxParser = context.get(SAXParser.class);
        PoolSAXParser poolSAXParser = null;
        if (saxParser == null) {
            if (POOL_SIZE == 0) {
                saxParser = getSAXParser();
            } else {
                poolSAXParser = acquireSAXParser();
                if (poolSAXParser != null) {
                    saxParser = poolSAXParser.getSAXParser();
                } else {
                    saxParser = getSAXParser();
                }
            }
        }
        try {
            saxParser.parse(is, new OfflineContentHandler(contentHandler));
        } finally {
            releaseParser(poolSAXParser);
        }
    }

    /**
     * This checks context for a user specified {@link SAXParser}.
     * If one is not found, this reuses a SAXParser from the pool.
     *
     * @param reader         reader (character stream) to parse
     * @param contentHandler handler to use; this wraps a {@link OfflineContentHandler}
     *                       to the content handler as an extra layer of defense against
     *                       external entity vulnerabilities
     * @param context        context to use
     * @return
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     * @since Apache Tika 2.5
     */
    public static void parseSAX(Reader reader, ContentHandler contentHandler, ParseContext context)
            throws TikaException, IOException, SAXException {
        SAXParser saxParser = context.get(SAXParser.class);
        PoolSAXParser poolSAXParser = null;
        if (saxParser == null) {
            if (POOL_SIZE == 0) {
                saxParser = getSAXParser();
            } else {
                poolSAXParser = acquireSAXParser();
                if (poolSAXParser != null) {
                    saxParser = poolSAXParser.getSAXParser();
                } else {
                    saxParser = getSAXParser();
                }
            }
        }
        try {
            saxParser.parse(new InputSource(reader), new OfflineContentHandler(contentHandler));
        } finally {
            releaseParser(poolSAXParser);
        }
    }

    /**
     * Acquire a DOMBuilder from the pool.  Make sure to
     * {@link #releaseDOMBuilder(PoolDOMBuilder)} in
     * a <code>finally</code> block every time you call this.
     *
     * @return a DocumentBuilder or null if no DOMBuilders are available
     * @throws TikaException
     */
    private static PoolDOMBuilder acquireDOMBuilder() throws TikaException {

        PoolDOMBuilder builder = null;
        DOM_POOL_LOCK
                .readLock()
                .lock();
        try {
            builder = DOM_BUILDERS.poll();
        } finally {
            DOM_POOL_LOCK
                    .readLock()
                    .unlock();
        }
        if (builder == null) {
            LOG.warn("Contention waiting for a DOMBuilder. " +
                    "Consider increasing the XMLReaderUtils.POOL_SIZE");

        }
        return builder;
    }

    /**
     * Return parser to the pool for reuse.
     *
     * @param builder builder to return
     */
    private static void releaseDOMBuilder(PoolDOMBuilder builder) {
        if (builder == null) {
            return;
        }
        if (builder.getPoolGeneration() != POOL_GENERATION.get()) {
            return;
        }
        try {
            builder.reset();
        } catch (UnsupportedOperationException e) {
            //ignore
        }
        DOM_POOL_LOCK
                .readLock().lock();
        builder.incrementUses();
        if (builder.numUses >= MAX_NUM_REUSES) {
            try {
                builder = new PoolDOMBuilder(builder.getPoolGeneration(), getDocumentBuilderFactory().newDocumentBuilder());
            } catch (ParserConfigurationException e) {
                LOG.warn("Exception trying to configure a new dom builder?!", e);
                return;
            }
        }
        try {
            //if there are extra parsers (e.g. after a reset of the pool to a smaller size),
            // this parser will not be added and will then be gc'd
            boolean success = DOM_BUILDERS.offer(builder);
            if (!success) {
                LOG.warn(
                        "DocumentBuilder not taken back into pool.  If you haven't resized the " +
                                "pool, this could be a sign that there are more calls to " +
                                "'acquire' than to 'release'");
            }
        } finally {
            DOM_POOL_LOCK
                    .readLock().unlock();
        }
    }

    /**
     * Acquire a SAXParser from the pool.  Make sure to
     * {@link #releaseParser(PoolSAXParser)} in
     * a <code>finally</code> block every time you call this.
     *
     * @return a SAXParser or null if a parser is not available
     * @throws TikaException
     */
    private static PoolSAXParser acquireSAXParser() throws TikaException {
        PoolSAXParser parser = null;

        //this locks around the pool so that there's
        //no race condition with it being resized
        SAX_POOL_LOCK
                .readLock()
                .lock();
        try {
            parser = SAX_PARSERS.poll();
        } finally {
            SAX_POOL_LOCK
                    .readLock()
                    .unlock();
        }
        if (parser == null) {
            LOG.warn("Contention waiting for a SAXParser. " +
                    "Consider increasing the XMLReaderUtils.POOL_SIZE");
        }
        return parser;
    }

    /**
     * Return parser to the pool for reuse
     *
     * @param parser parser to return
     */
    private static void releaseParser(PoolSAXParser parser) {
        if (parser == null) {
            return;
        }
        try {
            parser.reset();
        } catch (UnsupportedOperationException e) {
            //TIKA-3009 -- we really shouldn't have to do this... :(
        }
        //if this is a different generation, don't put it back
        //in the pool
        if (parser.getGeneration() != POOL_GENERATION.get()) {
            return;
        }
        SAX_POOL_LOCK
                .readLock().lock();
        try {
            parser.incrementUses();
            if (parser.numUses >= MAX_NUM_REUSES) {
                try {
                    parser = buildPoolParser(parser.getGeneration(), getSAXParserFactory().newSAXParser());
                } catch (SAXException | ParserConfigurationException e) {
                    LOG.warn("Couldn't build new SAXParser after hitting max reuses", e);
                    return;
                }
            }
            //if there are extra parsers (e.g. after a reset of the pool to a smaller size),
            // this parser will not be added and will then be gc'd
            boolean success = SAX_PARSERS.offer(parser);
            if (!success) {
                LOG.warn(
                        "SAXParser not taken back into pool.  If you haven't resized the pool " +
                                "this could be a sign that there are more calls to 'acquire' " +
                                "than to 'release'");
            }
        } finally {
            SAX_POOL_LOCK
                    .readLock().unlock();
        }
    }

    private static void trySetXercesSecurityManager(DocumentBuilderFactory factory) {
        //from POI
        // Try built-in JVM one first, standalone if not
        for (String securityManagerClassName : new String[]{
                //"com.sun.org.apache.xerces.internal.util.SecurityManager",
                XERCES_SECURITY_MANAGER}) {
            try {
                Object mgr =
                        Class.forName(securityManagerClassName).getDeclaredConstructor().newInstance();
                Method setLimit = mgr.getClass().getMethod("setEntityExpansionLimit",
                        Integer.TYPE);
                setLimit.invoke(mgr, MAX_ENTITY_EXPANSIONS);
                factory.setAttribute(XERCES_SECURITY_MANAGER_PROPERTY, mgr);
                // Stop once one can be setup without error
                return;
            } catch (ClassNotFoundException e) {
                // continue without log, this is expected in some setups
            } catch (Throwable e) {     // NOSONAR - also catch things like NoClassDefError here
                // throttle the log somewhat as it can spam the log otherwise
                if (System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                    LOG.warn(
                            "SAX Security Manager could not be setup [log suppressed for 5 " +
                                    "minutes]",
                            e);
                    LAST_LOG = System.currentTimeMillis();
                }
            }
        }

        // separate old version of Xerces not found => use the builtin way of setting the property
        try {
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit",
                    MAX_ENTITY_EXPANSIONS);
        } catch (IllegalArgumentException e) {
            // NOSONAR - also catch things like NoClassDefError here
            // throttle the log somewhat as it can spam the log otherwise
            if (System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                LOG.warn("SAX Security Manager could not be setup [log suppressed for 5 minutes]",
                        e);
                LAST_LOG = System.currentTimeMillis();
            }
        }
    }

    private static void trySetXercesSecurityManager(SAXParser parser) {
        //from POI
        // Try built-in JVM one first, standalone if not
        for (String securityManagerClassName : new String[]{
                //"com.sun.org.apache.xerces.internal.util.SecurityManager",
                XERCES_SECURITY_MANAGER}) {
            try {
                Object mgr =
                        Class.forName(securityManagerClassName).getDeclaredConstructor().newInstance();
                Method setLimit = mgr.getClass().getMethod("setEntityExpansionLimit", Integer.TYPE);
                setLimit.invoke(mgr, MAX_ENTITY_EXPANSIONS);

                parser.setProperty(XERCES_SECURITY_MANAGER_PROPERTY, mgr);
                // Stop once one can be setup without error
                return;
            } catch (ClassNotFoundException e) {
                // continue without log, this is expected in some setups
            } catch (Throwable e) {
                // NOSONAR - also catch things like NoClassDefError here
                // throttle the log somewhat as it can spam the log otherwise
                if (System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                    LOG.warn(
                            "SAX Security Manager could not be setup [log suppressed for 5 " +
                                    "minutes]",
                            e);
                    LAST_LOG = System.currentTimeMillis();
                }
            }
        }

        // separate old version of Xerces not found => use the builtin way of setting the property
        try {
            parser.setProperty("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit",
                    MAX_ENTITY_EXPANSIONS);
        } catch (SAXException e) {     // NOSONAR - also catch things like NoClassDefError here
            // throttle the log somewhat as it can spam the log otherwise
            if (System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                LOG.warn("SAX Security Manager could not be setup [log suppressed for 5 minutes]",
                        e);
                LAST_LOG = System.currentTimeMillis();
            }
        }
    }

    private static void trySetStaxSecurityManager(XMLInputFactory inputFactory) {
        //try default java entity expansion, then fallback to woodstox, then warn...once.
        try {
            inputFactory.setProperty("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit",
                    MAX_ENTITY_EXPANSIONS);
        } catch (IllegalArgumentException e) {
            try {
                inputFactory.setProperty("com.ctc.wstx.maxEntityCount", MAX_ENTITY_EXPANSIONS);
            } catch (IllegalArgumentException e2) {
                if (HAS_WARNED_STAX.getAndSet(true) == false) {
                    LOG.warn("Could not set limit on maximum entity expansions for: " + inputFactory.getClass());
                }
            }

        }
    }

    /**
     * Get the maximum number of times a SAXParser or DOMBuilder may be reused.
     *
     * @return
     */
    public static int getMaxNumReuses() {
        return MAX_NUM_REUSES;
    }

    public static void setMaxNumReuses(int maxNumReuses) {
        MAX_NUM_REUSES = maxNumReuses;
    }

    public static int getPoolSize() {
        return POOL_SIZE;
    }

    /**
     * Set the pool size for cached XML parsers.  This has a side
     * effect of locking the pool, and rebuilding the pool from
     * scratch with the most recent settings, such as {@link #MAX_ENTITY_EXPANSIONS}
     *
     * As of Tika 3.2.1, if a value of <code>0</code> is passed in, no SAXParsers or DOMBuilders
     * will be pooled, and a new parser/builder will be built for each parse.
     *
     * @param poolSize
     * @since Apache Tika 1.19
     */
    public static void setPoolSize(int poolSize) throws TikaException {
        if (poolSize < 0) {
            throw new IllegalArgumentException("PoolSize must be >= 0");
        }
        //stop the world with a write lock.
        //parsers that are currently in use will be offered later (once the lock is released),
        //but not accepted and will be gc'd.  We have to do this locking and
        //the read locking in case one thread resizes the pool when the
        //parsers have already started.  We could have an NPE on SAX_PARSERS
        //if we didn't lock.
        SAX_POOL_LOCK
                .writeLock().lock();
        try {
            //free up any resources before emptying SAX_PARSERS
            for (PoolSAXParser parser : SAX_PARSERS) {
                parser.reset();
            }
            SAX_PARSERS.clear();
            if (poolSize > 0) {
                SAX_PARSERS = new ArrayBlockingQueue<>(poolSize);
                int generation = POOL_GENERATION.incrementAndGet();
                for (int i = 0; i < poolSize; i++) {
                    try {
                        SAX_PARSERS.offer(buildPoolParser(generation, getSAXParserFactory().newSAXParser()));
                    } catch (SAXException | ParserConfigurationException e) {
                        throw new TikaException("problem creating sax parser", e);
                    }
                }
            }
        } finally {
            SAX_POOL_LOCK
                    .writeLock().unlock();
        }

        DOM_POOL_LOCK
                .writeLock().lock();
        try {
            DOM_BUILDERS.clear();
            if (poolSize > 0) {
                DOM_BUILDERS = new ArrayBlockingQueue<>(poolSize);
                for (int i = 0; i < poolSize; i++) {
                    DOM_BUILDERS.offer(new PoolDOMBuilder(POOL_GENERATION.get(), getDocumentBuilder()));
                }
            }
        } finally {
            DOM_POOL_LOCK
                    .writeLock().unlock();
        }
        POOL_SIZE = poolSize;
    }

    public static int getMaxEntityExpansions() {
        return MAX_ENTITY_EXPANSIONS;
    }

    /**
     * Set the maximum number of entity expansions allowable in SAX/DOM/StAX parsing.
     * <b>NOTE:</b>A value less than or equal to zero indicates no limit.
     * This will override the system property {@link #JAXP_ENTITY_EXPANSION_LIMIT_KEY}
     * and the {@link #DEFAULT_MAX_ENTITY_EXPANSIONS} value for allowable entity expansions
     * <p>
     * <b>NOTE:</b> To trigger a rebuild of the pool of parsers with this setting,
     * the client must call {@link #setPoolSize(int)} to rebuild the SAX and DOM parsers
     * with this setting.
     * </p>
     *
     * @param maxEntityExpansions -- maximum number of allowable entity expansions
     * @since Apache Tika 1.19
     */
    public static void setMaxEntityExpansions(int maxEntityExpansions) {
        MAX_ENTITY_EXPANSIONS = maxEntityExpansions;
    }

    /**
     * @param localName
     * @param atts
     * @return attribute value with that local name or <code>null</code> if not found
     */
    public static String getAttrValue(String localName, Attributes atts) {
        for (int i = 0; i < atts.getLength(); i++) {
            if (localName.equals(atts.getLocalName(i))) {
                return atts.getValue(i);
            }
        }
        return null;
    }

    private static PoolSAXParser buildPoolParser(int generation, SAXParser parser) {
        boolean canReset = false;
        try {
            parser.reset();
            canReset = true;
        } catch (UnsupportedOperationException e) {
            canReset = false;
        }
        boolean hasSecurityManager = false;
        try {
            Object mgr =
                    Class.forName(XERCES_SECURITY_MANAGER).getDeclaredConstructor().newInstance();
            Method setLimit = mgr.getClass().getMethod("setEntityExpansionLimit", Integer.TYPE);
            setLimit.invoke(mgr, MAX_ENTITY_EXPANSIONS);

            parser.setProperty(XERCES_SECURITY_MANAGER_PROPERTY, mgr);
            hasSecurityManager = true;
        } catch (SecurityException e) {
            //don't swallow security exceptions
            throw e;
        } catch (ClassNotFoundException e) {
            // continue without log, this is expected in some setups
        } catch (Throwable e) {
            // NOSONAR - also catch things like NoClassDefError here
            // throttle the log somewhat as it can spam the log otherwise
            if (System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                LOG.warn("SAX Security Manager could not be setup [log suppressed for 5 minutes]",
                        e);
                LAST_LOG = System.currentTimeMillis();
            }
        }

        boolean canSetJaxPEntity = false;
        if (!hasSecurityManager) {
            // use the builtin way of setting the property
            try {
                parser.setProperty("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit",
                        MAX_ENTITY_EXPANSIONS);
                canSetJaxPEntity = true;
            } catch (SAXException e) {     // NOSONAR - also catch things like NoClassDefError here
                // throttle the log somewhat as it can spam the log otherwise
                if (System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                    LOG.warn(
                            "SAX Security Manager could not be setup [log suppressed for 5 " +
                                    "minutes]",
                            e);
                    LAST_LOG = System.currentTimeMillis();
                }
            }
        }

        if (!canReset && hasSecurityManager) {
            return new XercesPoolSAXParser(generation, parser);
        } else if (canReset && hasSecurityManager) {
            return new Xerces2PoolSAXParser(generation, parser);
        } else if (canReset && !hasSecurityManager && canSetJaxPEntity) {
            return new BuiltInPoolSAXParser(generation, parser);
        } else {
            return new UnrecognizedPoolSAXParser(generation, parser);
        }

    }

    private static void clearReader(XMLReader reader) {
        if (reader == null) {
            return;
        }
        reader.setContentHandler(IGNORING_CONTENT_HANDLER);
        reader.setDTDHandler(IGNORING_DTD_HANDLER);
        reader.setEntityResolver(IGNORING_SAX_ENTITY_RESOLVER);
        reader.setErrorHandler(IGNORING_ERROR_HANDLER);
    }

    private static class PoolDOMBuilder {
        private final int poolGeneration;
        private final DocumentBuilder documentBuilder;
        int numUses = 0;

        PoolDOMBuilder(int poolGeneration, DocumentBuilder documentBuilder) {
            this.poolGeneration = poolGeneration;
            this.documentBuilder = documentBuilder;
        }

        public int getPoolGeneration() {
            return poolGeneration;
        }

        public DocumentBuilder getDocumentBuilder() {
            return documentBuilder;
        }

        public void reset() {
            documentBuilder.reset();
            documentBuilder.setEntityResolver(IGNORING_SAX_ENTITY_RESOLVER);
            documentBuilder.setErrorHandler(null);
        }

        void incrementUses() {
            numUses = 0;
        }
    }

    private abstract static class PoolSAXParser {
        final int poolGeneration;
        final SAXParser saxParser;
        int numUses = 0;
        PoolSAXParser(int poolGeneration, SAXParser saxParser) {
            this.poolGeneration = poolGeneration;
            this.saxParser = saxParser;
        }

        abstract void reset();

        public int getGeneration() {
            return poolGeneration;
        }

        public SAXParser getSAXParser() {
            return saxParser;
        }

        void incrementUses() {
            numUses++;
        }

    }

    private static class XercesPoolSAXParser extends PoolSAXParser {
        public XercesPoolSAXParser(int generation, SAXParser parser) {
            super(generation, parser);
        }

        @Override
        public void reset() {
            //don't do anything
            try {
                XMLReader reader = saxParser.getXMLReader();
                clearReader(reader);
            } catch (SAXException e) {
                //swallow
            }
        }
    }

    private static class Xerces2PoolSAXParser extends PoolSAXParser {
        public Xerces2PoolSAXParser(int generation, SAXParser parser) {
            super(generation, parser);
        }

        @Override
        void reset() {
            try {
                Object object = saxParser.getProperty(XERCES_SECURITY_MANAGER_PROPERTY);
                saxParser.reset();
                saxParser.setProperty(XERCES_SECURITY_MANAGER_PROPERTY, object);
            } catch (SAXException e) {
                LOG.warn("problem resetting sax parser", e);
            }
            try {
                XMLReader reader = saxParser.getXMLReader();
                clearReader(reader);
            } catch (SAXException e) {
                // ignored
            }
        }
    }

    private static class BuiltInPoolSAXParser extends PoolSAXParser {
        public BuiltInPoolSAXParser(int generation, SAXParser parser) {
            super(generation, parser);
        }

        @Override
        void reset() {
            saxParser.reset();
            try {
                XMLReader reader = saxParser.getXMLReader();
                clearReader(reader);
            } catch (SAXException e) {
                // ignored
            }
        }
    }

    private static class UnrecognizedPoolSAXParser extends PoolSAXParser {
        //if unrecognized, try to set all protections
        //and try to reset every time
        public UnrecognizedPoolSAXParser(int generation, SAXParser parser) {
            super(generation, parser);
        }

        @Override
        void reset() {
            try {
                saxParser.reset();
            } catch (UnsupportedOperationException e) {
                // ignored
            }
            try {
                XMLReader reader = saxParser.getXMLReader();
                clearReader(reader);
            } catch (SAXException e) {
                // ignored
            }
            trySetXercesSecurityManager(saxParser);
        }
    }

    /**
     * Returns the DOM builder specified in this parsing context.
     * If a builder is not explicitly specified, then a builder
     * instance is created and returned. The builder instance is
     * configured to apply an {@link XMLReaderUtils#IGNORING_SAX_ENTITY_RESOLVER},
     * and it sets the ErrorHandler to <code>null</code>.
     * Consider using {@link XMLReaderUtils#buildDOM(InputStream, ParseContext)}
     * instead for more efficient reuse of document builders.
     *
     * @return DOM Builder
     */
    public static DocumentBuilder getDocumentBuilder(ParseContext context) throws TikaException {
        DocumentBuilder documentBuilder = context.get(DocumentBuilder.class);
        if (documentBuilder != null) {
            return documentBuilder;
        } else {
            return XMLReaderUtils.getDocumentBuilder();
        }
    }

    /**
     * Returns the StAX input factory specified in this parsing context.
     * If a factory is not explicitly specified, then a default factory
     * instance is created and returned. The default factory instance is
     * configured to be namespace-aware and to apply reasonable security
     * precautions.
     *
     * @return StAX input factory
     */
    public static XMLInputFactory getXMLInputFactory(ParseContext context) {
        XMLInputFactory factory = context.get(XMLInputFactory.class);
        if (factory != null) {
            return factory;
        }
        return XMLReaderUtils.getXMLInputFactory();
    }


    /**
     * Returns the transformer specified in this parsing context.
     * <p>
     * If a transformer is not explicitly specified, then a default transformer
     * instance is created and returned. The default transformer instance is
     * configured to to use
     * {@link XMLConstants#FEATURE_SECURE_PROCESSING secure XML processing}.
     *
     * @return Transformer
     * @throws TikaException when the transformer can not be created
     */
    public static Transformer getTransformer(ParseContext context) throws TikaException {

        Transformer transformer = context.get(Transformer.class);
        if (transformer != null) {
            return transformer;
        }

        return XMLReaderUtils.getTransformer();
    }
}
