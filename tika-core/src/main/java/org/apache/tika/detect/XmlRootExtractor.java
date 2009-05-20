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

import java.io.InputStream;

import javax.xml.namespace.QName;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Utility class that uses a {@link SAXParser} to determine the namespace URI and local name of
 * the root element of an XML file.
 *
 * @since Apache Tika 0.4
 */
public class XmlRootExtractor {

    public static QName extractRootElement(byte[] data) {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        parserFactory.setValidating(false);

        ExtractorHandler handler = new ExtractorHandler();
        try {
            SAXParser parser = parserFactory.newSAXParser();
            InputStream in = new java.io.ByteArrayInputStream(data);
            parser.parse(in, handler);
        } catch (Exception e) {
            //ignore
        }
        return handler.rootElement;
    }

    private static class ExtractorHandler extends DefaultHandler {

        private QName rootElement;

        /** @inheritDoc */
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            this.rootElement = new QName(uri, localName);
            throw new SAXException("Aborting: root element received");
        }

    }

}
