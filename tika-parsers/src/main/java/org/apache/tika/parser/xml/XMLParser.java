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
package org.apache.tika.parser.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.TextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * XML parser.
 * <p>
 * This class uses the following parsing context entries:
 * <dl>
 *   <dt>javax.xml.parsers.SAXParser</dt>
 *   <dd>
 *     The SAX parser ({@link SAXParser} instance) to be used for parsing
 *     the XML input documents. Optional.
 *   </dd>
 *   <dt>javax.xml.parsers.SAXParserFactory</dt>
 *   <dd>
 *     The SAX parser factory ({@link SAXParserFactory} instance) used to
 *     create a SAX parser if one has not been explicitly specified. Optional.
 *   </dd>
 * </dl>
 */
public class XMLParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.application("xml"),
                MediaType.image("svg+xml"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if (metadata.get(Metadata.CONTENT_TYPE) == null) {
            metadata.set(Metadata.CONTENT_TYPE, "application/xml");
        }

        final XHTMLContentHandler xhtml =
            new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("p");

        getSAXParser(context).parse(
                new CloseShieldInputStream(stream),
                new OfflineContentHandler(
                        getContentHandler(handler, metadata)));

        xhtml.endElement("p");
        xhtml.endDocument();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

    protected ContentHandler getContentHandler(
            ContentHandler handler, Metadata metadata) {
        return new TextContentHandler(handler);
    }

    /**
     * Returns the SAX parser specified in the parsing context. If a parse
     * is not explicitly specified, then one is created using the specified
     * or the default SAX parser factory.
     *
     * @see #getSAXParserFactory()
     * @param context parsing context
     * @return SAX parser
     * @throws TikaException if a SAX parser could not be created
     */
    private SAXParser getSAXParser(ParseContext context)
            throws TikaException {
        SAXParser parser = context.get(SAXParser.class);
        if (parser instanceof SAXParser) {
            return parser;
        } else {
            try {
                return getSAXParserFactory(context).newSAXParser();
            } catch (ParserConfigurationException e) {
                throw new TikaException("Unable to configure a SAX parser", e);
            } catch (SAXException e) {
                throw new TikaException("Unable to create a SAX parser", e);
            }
        }
    }

    /**
     * Returns the SAX parser factory specified in the parsing context.
     * If a factory is not explicitly specified, then a default factory
     * instance is created and returned.
     *
     * @see #getDefaultSAXParserFactory()
     * @param context parsing context
     * @return SAX parser factory
     */
    private SAXParserFactory getSAXParserFactory(ParseContext context) {
        SAXParserFactory factory = context.get(SAXParserFactory.class);
        if (factory != null) {
            return factory;
        } else {
            return getDefaultSAXParserFactory();
        }
    }

    /**
     * Creates and returns a default SAX parser factory. The factory is
     * configured to be namespace-aware and to use secure XML processing.
     *
     * @see XMLConstants#FEATURE_SECURE_PROCESSING
     * @return default SAX parser factory
     */
    private SAXParserFactory getDefaultSAXParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException e) {
        } catch (SAXNotSupportedException e) {
        } catch (SAXNotRecognizedException e) {
            // TIKA-271: Some XML parsers do not support the secure-processing
            // feature, even though it's required by JAXP in Java 5. Ignoring
            // the exception is fine here, as deployments without this feature
            // are inherently vulnerable to XML denial-of-service attacks.
        }
        return factory;
    }

}
