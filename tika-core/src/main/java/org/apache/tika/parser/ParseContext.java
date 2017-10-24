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
package org.apache.tika.parser;

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
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Parse context. Used to pass context information to Tika parsers.
 *
 * @since Apache Tika 0.5
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
 */
public class ParseContext implements Serializable {

    /** Serial version UID. */
    private static final long serialVersionUID = -5921436862145826534L;

    /** Map of objects in this context */
    private final Map<String, Object> context = new HashMap<String, Object>();

    /**
     * Adds the given value to the context as an implementation of the given
     * interface.
     *
     * @param key the interface implemented by the given value
     * @param value the value to be added, or <code>null</code> to remove
     */
    public <T> void set(Class<T> key, T value) {
        if (value != null) {
            context.put(key.getName(), value);
        } else {
            context.remove(key.getName());
        }
    }

    /**
     * Returns the object in this context that implements the given interface.
     *
     * @param key the interface implemented by the requested object
     * @return the object that implements the given interface,
     *         or <code>null</code> if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> key) {
        return (T) context.get(key.getName());
    }

    /**
     * Returns the object in this context that implements the given interface,
     * or the given default value if such an object is not found.
     *
     * @param key the interface implemented by the requested object
     * @param defaultValue value to return if the requested object is not found
     * @return the object that implements the given interface,
     *         or the given default value if not found
     */
    public <T> T get(Class<T> key, T defaultValue) {
        T value = get(key);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns the XMLReader specified in this parsing context. If a reader
     * is not explicitly specified, then one is created using the specified
     * or the default SAX parser.
     *
     * @see #getSAXParser()
     * @since Apache Tika 1.13
     * @return XMLReader
     * @throws TikaException
     */
    public XMLReader getXMLReader() throws TikaException {
        XMLReader reader = get(XMLReader.class);
        if (reader != null) {
            return reader;
        }
        return XMLReaderUtils.getXMLReader();
    }

    /**
     * Returns the SAX parser specified in this parsing context. If a parser
     * is not explicitly specified, then one is created using the specified
     * or the default SAX parser factory.
     *
     * @see #getSAXParserFactory()
     * @since Apache Tika 0.8
     * @return SAX parser
     * @throws TikaException if a SAX parser could not be created
     */
    public SAXParser getSAXParser() throws TikaException {
        SAXParser parser = get(SAXParser.class);
        if (parser != null) {
            return parser;
        } else {
            return XMLReaderUtils.getSAXParser();
        }
    }

    /**
     * Returns the SAX parser factory specified in this parsing context.
     * If a factory is not explicitly specified, then a default factory
     * instance is created and returned. The default factory instance is
     * configured to be namespace-aware, not validating, and to use
     * {@link XMLConstants#FEATURE_SECURE_PROCESSING secure XML processing}.
     *
     * @since Apache Tika 0.8
     * @return SAX parser factory
     */
    public SAXParserFactory getSAXParserFactory() {
        SAXParserFactory factory = get(SAXParserFactory.class);
        if (factory == null) {
            factory = SAXParserFactory.newInstance();
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
     * @since Apache Tika 1.13
     * @return DOM parser factory
     */
    private DocumentBuilderFactory getDocumentBuilderFactory() {
        //borrowed from Apache POI
        DocumentBuilderFactory documentBuilderFactory = get(DocumentBuilderFactory.class);
        if (documentBuilderFactory != null) {
            return documentBuilderFactory;
        } else {
            return XMLReaderUtils.getDocumentBuilderFactory();
        }
    }

    /**
     * Returns the DOM builder specified in this parsing context.
     * If a builder is not explicitly specified, then a builder
     * instance is created and returned. The builder instance is
     * configured to apply an {@link XMLReaderUtils#IGNORING_SAX_ENTITY_RESOLVER},
     * and it sets the ErrorHandler to <code>null</code>.
     *
     * @since Apache Tika 1.13
     * @return DOM Builder
     */
    public DocumentBuilder getDocumentBuilder() throws TikaException {
        DocumentBuilder documentBuilder = get(DocumentBuilder.class);
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
     * using the {@link XMLReaderUtils#IGNORING_STAX_ENTITY_RESOLVER}.
     *
     * @since Apache Tika 1.13
     * @return StAX input factory
     */
    public XMLInputFactory getXMLInputFactory() {
        XMLInputFactory factory = get(XMLInputFactory.class);
        if (factory != null) {
            return factory;
        }
        return XMLReaderUtils.getXMLInputFactory();
    }
  
    
    /**
     * Returns the transformer specified in this parsing context.
     * 
     * If a transformer is not explicitly specified, then a default transformer
     * instance is created and returned. The default transformer instance is
     * configured to to use
     * {@link XMLConstants#FEATURE_SECURE_PROCESSING secure XML processing}.
     *
     * @since Apache Tika 1.17
     * @return Transformer
     * @throws TikaException when the transformer can not be created
     */
    public Transformer getTransformer() throws TikaException {
        
        Transformer transformer = get(Transformer.class);
        if ( transformer != null ) {
            return transformer;
        }
        
        return XMLReaderUtils.getTransformer();
    }

}
