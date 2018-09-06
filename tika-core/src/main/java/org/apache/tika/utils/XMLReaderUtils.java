/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.utils;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility functions for reading XML.  If you are doing SAX parsing, make sure
 * to use the {@link org.apache.tika.sax.OfflineContentHandler} to guard against
 * XML External Entity attacks.
 */
public class XMLReaderUtils implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 6110455808615143122L;

    private static final Logger LOG = Logger.getLogger(XMLReaderUtils.class.getName());

    /**
     * Parser pool size
     */
    private static int POOL_SIZE = 10;

    private static long LAST_LOG = -1;

    private static final String JAXP_ENTITY_EXPANSION_LIMIT_KEY = "jdk.xml.entityExpansionLimit";
    private static final int DEFAULT_MAX_ENTITY_EXPANSIONS = 20;

    private static int MAX_ENTITY_EXPANSIONS = determineMaxEntityExpansions();

    private static int determineMaxEntityExpansions() {
        Properties properties = System.getProperties();
        if (properties != null && properties.containsKey(JAXP_ENTITY_EXPANSION_LIMIT_KEY)) {
            try {
                return Integer.parseInt(properties.getProperty(JAXP_ENTITY_EXPANSION_LIMIT_KEY));
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Couldn't parse an integer for the entity expansion limit:"+
                        properties.getProperty(JAXP_ENTITY_EXPANSION_LIMIT_KEY)+
                        "; backing off to default: "+DEFAULT_MAX_ENTITY_EXPANSIONS);
            }
        }
        return DEFAULT_MAX_ENTITY_EXPANSIONS;
    }

    //TODO: figure out if the rw lock is any better than a simple lock
    private static final ReentrantReadWriteLock SAX_READ_WRITE_LOCK = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock DOM_READ_WRITE_LOCK = new ReentrantReadWriteLock();

    private static ArrayBlockingQueue<SAXParser> SAX_PARSERS = new ArrayBlockingQueue<>(POOL_SIZE);
    private static ArrayBlockingQueue<DocumentBuilder> DOM_BUILDERS = new ArrayBlockingQueue<>(POOL_SIZE);

    static {
        try {
            setPoolSize(POOL_SIZE);
        } catch (TikaException e) {
            throw new RuntimeException("problem initializing SAXParser and DOMBuilder pools", e);
        }
    }


    private static final EntityResolver IGNORING_SAX_ENTITY_RESOLVER = new EntityResolver() {
        public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
            return new InputSource(new StringReader(""));
        }
    };

    private static final XMLResolver IGNORING_STAX_ENTITY_RESOLVER =
            new XMLResolver() {
                @Override
                public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace) throws
                        XMLStreamException {
                    return "";
                }
            };

    /**
     * Set the maximum number of entity expansions allowable in SAX/DOM/StAX parsing.
     * <b>NOTE:</b>A value less than or equal to zero indicates no limit.
     * This will override the system property {@link #JAXP_ENTITY_EXPANSION_LIMIT_KEY}
     * and the {@link #DEFAULT_MAX_ENTITY_EXPANSIONS} value for pa
     *
     * @param maxEntityExpansions -- maximum number of allowable entity expansions
     * @since Apache Tika 1.19
     */
    public static void setMaxEntityExpansions(int maxEntityExpansions) {
        MAX_ENTITY_EXPANSIONS = maxEntityExpansions;
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
     * Make sure to wrap your handler in the {@link org.apache.tika.sax.OfflineContentHandler} to
     * prevent XML External Entity attacks
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
     * <p>
     * Make sure to wrap your handler in the {@link org.apache.tika.sax.OfflineContentHandler} to
     * prevent XML External Entity attacks
     * </p>
     *
     * @return SAX parser factory
     * @since Apache Tika 0.8
     */
    public static SAXParserFactory getSAXParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        try {
            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException e) {
        } catch (SAXNotSupportedException e) {
        } catch (SAXNotRecognizedException e) {
            // TIKA-271: Some XML parsers do not support the
            // secure-processing feature, even though it's required by
            // JAXP in Java 5. Ignoring the exception is fine here, as
            // deployments without this feature are inherently vulnerable
            // to XML denial-of-service attacks.
        }

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
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        factory.setValidating(false);

        trySetSAXFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetSAXFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetSAXFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetSAXFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        trySetSAXFeature(factory, "http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
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
     * using the {@link #IGNORING_STAX_ENTITY_RESOLVER}.
     *
     * @return StAX input factory
     * @since Apache Tika 1.13
     */
    public static XMLInputFactory getXMLInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();

        tryToSetStaxProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
        tryToSetStaxProperty(factory, XMLInputFactory.IS_VALIDATING, false);

        factory.setXMLResolver(IGNORING_STAX_ENTITY_RESOLVER);
        trySetStaxSecurityManager(factory);
        return factory;
    }

    private static void trySetSAXFeature(DocumentBuilderFactory documentBuilderFactory, String feature, boolean enabled) {
        try {
            documentBuilderFactory.setFeature(feature, enabled);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "SAX Feature unsupported: " + feature, e);
        } catch (AbstractMethodError ame) {
            LOG.log(Level.WARNING, "Cannot set SAX feature because outdated XML parser in classpath: " + feature, ame);
        }
    }

    private static void tryToSetStaxProperty(XMLInputFactory factory, String key, boolean value) {
        try {
            factory.setProperty(key, value);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "StAX Feature unsupported: " + key, e);
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
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return transformerFactory.newTransformer();
        } catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
            throw new TikaException("Transformer not available", e);
        }
    }

    /**
     * This checks context for a user specified {@link DocumentBuilder}.
     * If one is not found, this reuses a DocumentBuilder from the pool.
     *
     * @since Apache Tika 1.19
     * @param is InputStream to parse
     * @param context context to use
     * @return a document
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     */
    public static Document buildDOM(InputStream is, ParseContext context) throws TikaException, IOException, SAXException {
        DocumentBuilder builderFromContext = context.get(DocumentBuilder.class);
        DocumentBuilder builder = (builderFromContext == null) ? acquireDOMBuilder() : builderFromContext;

        try {
            return builder.parse(is);
        } finally {
            if (builderFromContext == null) {
                releaseDOMBuilder(builder);
            }
        }
    }

    /**
     * This checks context for a user specified {@link SAXParser}.
     * If one is not found, this reuses a SAXParser from the pool.
     *
     * @since Apache Tika 1.19
     * @param is InputStream to parse
     * @param contentHandler handler to use
     * @param context context to use
     * @return
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     */
    public static void parseSAX(InputStream is, DefaultHandler contentHandler, ParseContext context)
            throws TikaException, IOException, SAXException {
        SAXParser contextParser = context.get(SAXParser.class);
        SAXParser parser = (contextParser == null) ? acquireSAXParser() : contextParser;
        try {
            parser.parse(is, contentHandler);
        } finally {
            if (contextParser == null) {
                releaseParser(parser);
            }
        }
    }

    /**
     * Acquire a SAXParser from the pool.  Make sure to
     * {@link #releaseParser(SAXParser)} in
     * a <code>finally</code> block every time you call this.
     *
     * @return a SAXParser
     * @throws TikaException
     */
    private static DocumentBuilder acquireDOMBuilder()
            throws TikaException {
        int waiting = 0;
        while (true) {
            DocumentBuilder builder = null;
            try {
                DOM_READ_WRITE_LOCK.readLock().lock();
                builder = DOM_BUILDERS.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new TikaException("interrupted while waiting for DOMBuilder", e);
            } finally {
                DOM_READ_WRITE_LOCK.readLock().unlock();
            }
            if (builder != null) {
                return builder;
            }
            waiting++;
            if (waiting > 3000) {
                //freshen the pool.  Something went very wrong...
                setPoolSize(POOL_SIZE);
                //better to get an exception than have permahang by a bug in one of our parsers
                throw new TikaException("Waited more than 5 minutes for a DocumentBuilder; " +
                        "This could indicate that a parser has not correctly released its DocumentBuilder. " +
                        "Please report this to the Tika team: dev@tika.apache.org");

            }
        }
    }

    /**
     * Return parser to the pool for reuse.
     *
     * @param builder builder to return
     */
    private static void releaseDOMBuilder(DocumentBuilder builder) {
        try {
            builder.reset();
        } catch (UnsupportedOperationException e) {
            //ignore
        }
        try {
            DOM_READ_WRITE_LOCK.readLock().lock();
            //if there are extra parsers (e.g. after a reset of the pool to a smaller size),
            // this parser will not be added and will then be gc'd
            boolean success = DOM_BUILDERS.offer(builder);
            if (! success) {
                LOG.warning("DocumentBuilder not taken back into pool.  If you haven't resized the pool, this could " +
                        "be a sign that there are more calls to 'acquire' than to 'release'");
            }
        } finally {
            DOM_READ_WRITE_LOCK.readLock().unlock();
        }
    }


    /**
     * Acquire a SAXParser from the pool.  Make sure to
     * {@link #releaseParser(SAXParser)} in
     * a <code>finally</code> block every time you call this.
     *
     * @return a SAXParser
     * @throws TikaException
     */
    private static SAXParser acquireSAXParser()
            throws TikaException {
        int waiting = 0;
        while (true) {
            SAXParser parser = null;
            try {
                SAX_READ_WRITE_LOCK.readLock().lock();
                parser = SAX_PARSERS.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new TikaException("interrupted while waiting for SAXParser", e);
            } finally {
                SAX_READ_WRITE_LOCK.readLock().unlock();
            }
            if (parser != null) {
                return parser;
            }
            waiting++;
            if (waiting > 3000) {
                //freshen the pool.  Something went very wrong...
                setPoolSize(POOL_SIZE);
                //better to get an exception than have permahang by a bug in one of our parsers
                throw new TikaException("Waited more than 5 minutes for a SAXParser; " +
                        "This could indicate that a parser has not correctly released its SAXParser. " +
                        "Please report this to the Tika team: dev@tika.apache.org");

            }
        }
    }

    /**
     * Return parser to the pool for reuse
     *
     * @param parser parser to return
     */
    private static void releaseParser(SAXParser parser) {
        try {
            parser.reset();
        } catch (UnsupportedOperationException e) {
            //ignore
        }
        try {
            SAX_READ_WRITE_LOCK.readLock().lock();
            //if there are extra parsers (e.g. after a reset of the pool to a smaller size),
            // this parser will not be added and will then be gc'd
            boolean success = SAX_PARSERS.offer(parser);
            if (! success) {
                LOG.warning("SAXParser not taken back into pool.  If you haven't resized the pool, this could " +
                        "be a sign that there are more calls to 'acquire' than to 'release'");
            }
        } finally {
            SAX_READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /**
     * Set the pool size for cached XML parsers.
     *
     * @since Apache Tika 1.19
     * @param poolSize
     */
    public static void setPoolSize(int poolSize) throws TikaException {
        try {
            //stop the world with a write lock.
            //parsers that are currently in use will be offered later (once the lock is released),
            //but not accepted and will be gc'd.  We have to do this locking and
            //the read locking in case one thread resizes the pool when the
            //parsers have already started.  We could have an NPE on SAX_PARSERS
            //if we didn't lock.
            SAX_READ_WRITE_LOCK.writeLock().lock();
            if (SAX_PARSERS.size() != poolSize) {
                SAX_PARSERS = new ArrayBlockingQueue<>(poolSize);
                for (int i = 0; i < poolSize; i++) {
                    SAX_PARSERS.offer(getSAXParser());
                }
            }
        } finally {
            SAX_READ_WRITE_LOCK.writeLock().unlock();
        }
        try {
            DOM_READ_WRITE_LOCK.writeLock().lock();

            if (DOM_BUILDERS.size() != poolSize) {
                DOM_BUILDERS = new ArrayBlockingQueue<>(poolSize);
                for (int i = 0; i < poolSize; i++) {
                    DOM_BUILDERS.offer(getDocumentBuilder());
                }
            }
        } finally {
            DOM_READ_WRITE_LOCK.writeLock().unlock();
        }
        POOL_SIZE = poolSize;
    }

    private static void trySetXercesSecurityManager(DocumentBuilderFactory factory) {
        //from POI
        // Try built-in JVM one first, standalone if not
        for (String securityManagerClassName : new String[] {
                //"com.sun.org.apache.xerces.internal.util.SecurityManager",
                "org.apache.xerces.util.SecurityManager"
        }) {
            try {
                Object mgr = Class.forName(securityManagerClassName).newInstance();
                Method setLimit = mgr.getClass().getMethod("setEntityExpansionLimit", Integer.TYPE);
                setLimit.invoke(mgr, MAX_ENTITY_EXPANSIONS);
                factory.setAttribute("http://apache.org/xml/properties/security-manager", mgr);
                // Stop once one can be setup without error
                return;
            } catch (ClassNotFoundException e) {
                // continue without log, this is expected in some setups
            } catch (Throwable e) {     // NOSONAR - also catch things like NoClassDefError here
                // throttle the log somewhat as it can spam the log otherwise
                if(System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                    LOG.log(Level.WARNING, "SAX Security Manager could not be setup [log suppressed for 5 minutes]", e);
                    LAST_LOG = System.currentTimeMillis();
                }
            }
        }

        // separate old version of Xerces not found => use the builtin way of setting the property
        try {
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit", MAX_ENTITY_EXPANSIONS);
        } catch (IllegalArgumentException e) {     // NOSONAR - also catch things like NoClassDefError here
            // throttle the log somewhat as it can spam the log otherwise
            if(System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                LOG.log(Level.WARNING, "SAX Security Manager could not be setup [log suppressed for 5 minutes]", e);
                LAST_LOG = System.currentTimeMillis();
            }
        }
    }

    private static void trySetXercesSecurityManager(SAXParser parser) {
        //from POI
        // Try built-in JVM one first, standalone if not
        for (String securityManagerClassName : new String[] {
                //"com.sun.org.apache.xerces.internal.util.SecurityManager",
                "org.apache.xerces.util.SecurityManager"
        }) {
            try {
                Object mgr = Class.forName(securityManagerClassName).newInstance();
                Method setLimit = mgr.getClass().getMethod("setEntityExpansionLimit", Integer.TYPE);
                setLimit.invoke(mgr, MAX_ENTITY_EXPANSIONS);
                parser.setProperty("http://apache.org/xml/properties/security-manager", mgr);
                // Stop once one can be setup without error
                return;
            } catch (ClassNotFoundException e) {
                // continue without log, this is expected in some setups
            } catch (Throwable e) {     // NOSONAR - also catch things like NoClassDefError here
                // throttle the log somewhat as it can spam the log otherwise
                if(System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                    LOG.log(Level.WARNING, "SAX Security Manager could not be setup [log suppressed for 5 minutes]", e);
                    LAST_LOG = System.currentTimeMillis();
                }
            }
        }

        // separate old version of Xerces not found => use the builtin way of setting the property
        try {
            parser.setProperty("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit", MAX_ENTITY_EXPANSIONS);
        } catch (SAXException e) {     // NOSONAR - also catch things like NoClassDefError here
            // throttle the log somewhat as it can spam the log otherwise
            if(System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                LOG.log(Level.WARNING, "SAX Security Manager could not be setup [log suppressed for 5 minutes]", e);
                LAST_LOG = System.currentTimeMillis();
            }
        }
    }

    private static void trySetStaxSecurityManager(XMLInputFactory inputFactory) {
        try {
            inputFactory.setProperty("com.ctc.wstx.maxEntityCount", MAX_ENTITY_EXPANSIONS);
        } catch (IllegalArgumentException e) {
            // throttle the log somewhat as it can spam the log otherwise
            if(System.currentTimeMillis() > LAST_LOG + TimeUnit.MINUTES.toMillis(5)) {
                LOG.log(Level.WARNING, "SAX Security Manager could not be setup [log suppressed for 5 minutes]", e);
                LAST_LOG = System.currentTimeMillis();
            }
        }
    }
}
