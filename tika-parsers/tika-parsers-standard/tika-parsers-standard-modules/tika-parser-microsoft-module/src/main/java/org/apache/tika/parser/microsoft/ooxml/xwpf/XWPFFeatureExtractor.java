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
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * This is designed to extract features that are useful for forensics, e-discovery and digital preservation.
 * Specifically, the presence of: tracked changes, hidden text, comments and comment authors. Because several of these
 * features can be placed on run properties, which can be in lots of places, we're scraping
 * the document xml
 */
public class XWPFFeatureExtractor {

    public void process(XWPFDocument xwpfDocument, Metadata metadata, ParseContext parseContext) {
        try (InputStream is = xwpfDocument.getPackagePart()
                                          .getInputStream()) {
            FeatureHandler featureHandler = new FeatureHandler();
            XMLReaderUtils.parseSAX(is, featureHandler, parseContext);
            if (featureHandler.hasComments) {
                metadata.set(Office.HAS_COMMENTS, true);
            }
            if (featureHandler.hasHidden) {
                metadata.set(Office.HAS_HIDDEN_TEXT, true);
            }
            if (featureHandler.hasTrackChanges) {
                metadata.set(Office.HAS_TRACK_CHANGES, true);
            }
            if (! featureHandler.authors.isEmpty()) {
                for (String author : featureHandler.authors) {
                    metadata.add(Office.COMMENT_PERSONS, author);
                }
            }
        } catch (IOException | TikaException | SAXException e) {
            //swallow
        }
    }

    private static class FeatureHandler extends DefaultHandler {
        //see: https://www.ericwhite.com/blog/using-xml-dom-to-detect-tracked-revisions-in-an-open-xml-wordprocessingml-document/
        private static final Set<String> TRACK_CHANGES = Set.of("ins", "del", "moveFrom", "moveTo");
        private final Set<String> authors = new HashSet<>();
        private boolean hasHidden = false;
        private boolean hasTrackChanges = false;
        private boolean hasComments = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            //we could check to ensure that the vanish element actually surrounds text
            //the current check could lead to false positives where <w:vanish/> is around a space or no text.
            if ("vanish".equals(localName)) {
                hasHidden = true;
            } else if (TRACK_CHANGES.contains(localName)) {
                String trackChangesAuthor = XMLReaderUtils.getAttrValue("author", atts);
                if (!StringUtils.isBlank(trackChangesAuthor)) {
                    authors.add(trackChangesAuthor);
                }
                hasTrackChanges = true;
            } else if ("commentReference".equals(localName) || "commentRangeStart".equals(localName)) {
                hasComments = true;
            }
        }
    }
}
