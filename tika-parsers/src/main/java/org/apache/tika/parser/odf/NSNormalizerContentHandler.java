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
package org.apache.tika.parser.odf;

import java.io.IOException;
import java.io.StringReader;

import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Content handler decorator that:<ul>
 * <li>Maps old OpenOffice 1.0 Namespaces to the OpenDocument ones</li>
 * <li>Returns a fake DTD when parser requests OpenOffice DTD</li>
 * </ul>
 */
public class NSNormalizerContentHandler extends ContentHandlerDecorator {

    private static final String OLD_NS =
        "http://openoffice.org/2000/";

    private static final String NEW_NS =
        "urn:oasis:names:tc:opendocument:xmlns:";

    private static final String DTD_PUBLIC_ID =
        "-//OpenOffice.org//DTD OfficeDocument 1.0//EN";

    public NSNormalizerContentHandler(ContentHandler handler) {
        super(handler);
    }

    private String mapOldNS(String ns) {
        if (ns != null && ns.startsWith(OLD_NS)) {
            return NEW_NS + ns.substring(OLD_NS.length()) + ":1.0";
        } else {
            return ns;
        }
    }

    @Override
    public void startElement(
            String namespaceURI, String localName, String qName,
            Attributes atts) throws SAXException {
        AttributesImpl natts = new AttributesImpl();
        for (int i = 0; i < atts.getLength(); i++) {
            natts.addAttribute(
                    mapOldNS(atts.getURI(i)), atts.getLocalName(i),
                    atts.getQName(i), atts.getType(i), atts.getValue(i));
        }
        super.startElement(mapOldNS(namespaceURI), localName, qName, atts);
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        super.endElement(mapOldNS(namespaceURI), localName, qName);
    }

    @Override
    public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
        super.startPrefixMapping(prefix, mapOldNS(uri));
    }

    /**
     * do not load any DTDs (may be requested by parser). Fake the DTD by
     * returning a empty string as InputSource
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId)
            throws IOException, SAXException {
        if ((systemId != null && systemId.toLowerCase().endsWith(".dtd"))
                || DTD_PUBLIC_ID.equals(publicId)) {
            return new InputSource(new StringReader(""));
        } else {
            return super.resolveEntity(publicId, systemId);
        }
    }

}
