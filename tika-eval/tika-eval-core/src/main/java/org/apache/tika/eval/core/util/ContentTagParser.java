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
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

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
        Document document = Jsoup.parse(html);
        NodeTraversor.filter(new TikaNodeFilter(xhtmlContentTagHandler), document);

        return new ContentTags(xhtmlContentTagHandler.toString(), tags);
    }

    private static class TikaNodeFilter implements NodeFilter {
        boolean ignore = true;
        ContentHandler handler;

        private TikaNodeFilter(ContentHandler handler) {
            this.handler = handler;
        }

        @Override
        public NodeFilter.FilterResult head(Node node, int i) {
            //skip document fragment
            if ("html".equals(node.nodeName())) {
                ignore = false;
            }
            if (ignore) {
                return FilterResult.CONTINUE;
            }
            if (node instanceof TextNode) {
                String txt = ((TextNode) node).getWholeText();
                if (txt != null) {
                    char[] chars = txt.toCharArray();
                    try {
                        if (chars.length > 0) {
                            handler.characters(chars, 0, chars.length);
                        }
                    } catch (SAXException e) {
                        throw new RuntimeSAXException(e);
                    }
                }
                return NodeFilter.FilterResult.CONTINUE;
            } else if (node instanceof DataNode) {
                //maybe handle script data directly here instead of
                //passing it through to the HTMLHandler?
                String txt = ((DataNode) node).getWholeData();
                if (txt != null) {
                    char[] chars = txt.toCharArray();
                    try {
                        if (chars.length > 0) {
                            handler.characters(chars, 0, chars.length);
                        }
                    } catch (SAXException e) {
                        throw new RuntimeSAXException(e);
                    }
                }
                return NodeFilter.FilterResult.CONTINUE;
            }
            AttributesImpl attributes = new AttributesImpl();
            Iterator<Attribute> jsoupAttrs = node
                    .attributes()
                    .iterator();
            while (jsoupAttrs.hasNext()) {
                Attribute jsoupAttr = jsoupAttrs.next();
                attributes.addAttribute("", jsoupAttr.getKey(), jsoupAttr.getKey(), "", jsoupAttr.getValue());
            }
            try {
                handler.startElement("", node.nodeName(), node.nodeName(), attributes);
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
            return NodeFilter.FilterResult.CONTINUE;
        }

        @Override
        public NodeFilter.FilterResult tail(Node node, int i) {
            if ("html".equals(node.nodeName())) {
                ignore = true;
            }
            if (ignore) {
                return FilterResult.CONTINUE;
            }
            if (node instanceof TextNode || node instanceof DataNode) {
                return NodeFilter.FilterResult.CONTINUE;
            }

            try {
                handler.endElement(XMLConstants.NULL_NS_URI, node.nodeName(), node.nodeName());
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
            return NodeFilter.FilterResult.CONTINUE;
        }
    }

    private static class RuntimeSAXException extends RuntimeException {
        private SAXException wrapped;

        private RuntimeSAXException(SAXException e) {
            this.wrapped = e;
        }

        SAXException getWrapped() {
            return wrapped;
        }
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
