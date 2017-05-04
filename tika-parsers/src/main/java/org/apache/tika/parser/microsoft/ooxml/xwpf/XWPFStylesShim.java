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

package org.apache.tika.parser.microsoft.ooxml.xwpf;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.OOXMLWordAndPowerPointTextHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * For Tika, all we need (so far) is a mapping between styleId and a style's name.
 *
 * This class uses SAX to scrape that info out of the styles.xml file.  If
 * either the styleId or the style's name is null, no information is recorded.
 */
public class XWPFStylesShim {

    /**
     * Empty singleton to be used when there is no style info
     */
    public static XWPFStylesShim EMPTY_STYLES = new EmptyXWPFStyles();

    private Map<String, String> styles = new HashMap<>();

    private XWPFStylesShim() {

    }

    public XWPFStylesShim(PackagePart part, ParseContext parseContext) throws IOException, TikaException, SAXException {

        try (InputStream is = part.getInputStream()) {
            onDocumentLoad(parseContext, is);
        }
    }

    private void onDocumentLoad(ParseContext parseContext, InputStream stream) throws TikaException, IOException, SAXException {
        parseContext.getSAXParser().parse(stream,
                new OfflineContentHandler(new StylesStripper()));
    }

    /**
     *
     * @param styleId
     * @return style's name or null if styleId is null or can't be found
     */
    public String getStyleName(String styleId) {
        if (styleId == null) {
            return null;
        }
        return styles.get(styleId);
    }

    private static class EmptyXWPFStyles extends XWPFStylesShim {

        @Override
        public String getStyleName(String styleId) {
            return null;
        }
    }

    private class StylesStripper extends DefaultHandler {

        String currentStyleId = null;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if (uri == null || OOXMLWordAndPowerPointTextHandler.W_NS.equals(uri)) {
                if ("style".equals(localName)) {
                    currentStyleId = atts.getValue(OOXMLWordAndPowerPointTextHandler.W_NS, "styleId");
                } else if ("name".equals(localName)) {
                    String name = atts.getValue(OOXMLWordAndPowerPointTextHandler.W_NS, "val");
                    if (currentStyleId != null && name != null) {
                        styles.put(currentStyleId, name);
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (uri == null || OOXMLWordAndPowerPointTextHandler.W_NS.equals(uri)) {
                if ("style".equals(localName)) {
                    currentStyleId = null;
                }
            }
        }
    }

}
