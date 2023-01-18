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

package org.apache.tika.eval.core.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class ContentTagParser {

    private static final ParseContext EMPTY_PARSE_CONTEXT = new ParseContext();

    public static ContentTags parseXML(String html, Set<String> uppercaseTagsOfInterest)
            throws TikaException, IOException, SAXException {
        Map<String, Integer> tags = new HashMap<>();
        XHTMLContentTagHandler xhtmlContentTagHandler =
                new XHTMLContentTagHandler(uppercaseTagsOfInterest, tags);
        XMLReaderUtils.parseSAX(new StringReader(html),
                xhtmlContentTagHandler, EMPTY_PARSE_CONTEXT);
        return new ContentTags(xhtmlContentTagHandler.toString(), tags);
    }

    public static ContentTags parseHTML(String html, Set<String> uppercaseTagsOfInterest)
            throws SAXException, IOException {
        Map<String, Integer> tags = new HashMap<>();
        XHTMLContentTagHandler xhtmlContentTagHandler =
                new XHTMLContentTagHandler(uppercaseTagsOfInterest, tags);
        SAXParserImpl.newInstance(null)
                .parse(new InputSource(new StringReader(html)), xhtmlContentTagHandler);
        return new ContentTags(xhtmlContentTagHandler.toString(), tags);
    }


    private static class XHTMLContentTagHandler extends ToTextContentHandler {
        //Used to have a stack to make sure that starting/ending tags were matched
        //However, this was a non-starter because tag soup fixes non-matching tags for html
        //and the straight SAXParser throws an exception for mismatched tags in xml

        private final Map<String, Integer> tags;
        private final Set<String> uppercaseTagsOfInterest;

        public XHTMLContentTagHandler(Set<String> uppercaseTagsOfInterest,
                                      Map<String, Integer> tags) {
            this.uppercaseTagsOfInterest = uppercaseTagsOfInterest;
            this.tags = tags;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            super.startElement(uri, localName, qName, atts);
            String uc = (qName == null) ? "" : qName.toUpperCase(Locale.ENGLISH);
            if (uppercaseTagsOfInterest.contains(uc)) {
                Integer i = tags.get(uc);
                if (i == null) {
                    i = 1;
                } else {
                    i++;
                }
                tags.put(uc, i);
            }
        }
    }
}
