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
package org.apache.tika.detect;

import java.io.ByteArrayInputStream;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.tika.sax.OfflineContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Utility class that uses a {@link javax.xml.parsers.SAXParser} to determine
 * the namespace URI and local name of the root element of an XML file.
 *
 * @since Apache Tika 0.4
 */
public class XmlRootExtractor {

    private final SAXParserFactory factory;

    public XmlRootExtractor() throws SAXException, ParserConfigurationException {
        factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (SAXNotRecognizedException e) {
            // TIKA-271: Some XML parsers do not support the secure-processing
            // feature, even though it's required by JAXP in Java 5. Ignoring
            // the exception is fine here, deployments without this feature
            // are inherently vulnerable to XML denial-of-service attacks.
        }

    }

    public QName extractRootElement(byte[] data) {
        ExtractorHandler handler = new ExtractorHandler();
        try {
            factory.newSAXParser().parse(
                    new ByteArrayInputStream(data),
                    new OfflineContentHandler(handler));
        } catch (Exception ignore) {
        }
        return handler.rootElement;
    }

    private static class ExtractorHandler extends DefaultHandler {

        private QName rootElement = null;

        @Override
        public void startElement(
                String uri, String local, String name, Attributes attributes)
                throws SAXException {
            this.rootElement = new QName(uri, local);
            throw new SAXException("Aborting: root element received");
        }

    }

}
