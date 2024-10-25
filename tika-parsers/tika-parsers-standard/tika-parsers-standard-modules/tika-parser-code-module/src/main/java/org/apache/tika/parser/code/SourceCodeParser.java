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
package org.apache.tika.parser.code;

import static org.codelibs.jhighlight.renderer.XhtmlRendererFactory.CPP;
import static org.codelibs.jhighlight.renderer.XhtmlRendererFactory.GROOVY;
import static org.codelibs.jhighlight.renderer.XhtmlRendererFactory.JAVA;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.codelibs.jhighlight.renderer.Renderer;
import org.codelibs.jhighlight.renderer.XhtmlRendererFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;
import org.jsoup.select.NodeTraversor;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Generic Source code parser for Java, Groovy, C++.
 * Aware: This parser uses JHightlight library (https://github.com/codelibs/jhighlight) under CDDL/LGPL dual license
 *
 * @author Hong-Thai.Nguyen
 * @since 1.6
 */
public class SourceCodeParser extends AbstractEncodingDetectorParser {

    private static final long serialVersionUID = -4543476498190054160L;

    private static final Pattern AUTHORPATTERN = Pattern.compile("(?im)@author (.*) *$");

    private static final Map<MediaType, String> TYPES_TO_RENDERER = new HashMap<MediaType, String>() {
        private static final long serialVersionUID = -741976157563751152L;

        {
            put(MediaType.text("x-c++src"), CPP);
            put(MediaType.text("x-java-source"), JAVA);
            put(MediaType.text("x-groovy"), GROOVY);
        }
    };

    public SourceCodeParser() {
        super();
    }

    public SourceCodeParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return TYPES_TO_RENDERER.keySet();
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try (AutoDetectReader reader = new AutoDetectReader(CloseShieldInputStream.wrap(stream),
                metadata, getEncodingDetector(context))) {
            Charset charset = reader.getCharset();
            String mediaType = metadata.get(Metadata.CONTENT_TYPE);
            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            MediaType type = null;
            if (mediaType != null) {
                type = MediaType.parse(mediaType);
                metadata.set(Metadata.CONTENT_TYPE, type.toString());
                metadata.set(Metadata.CONTENT_ENCODING, charset.name());
            } else {
                throw new TikaException("media type must be set in metadata before parse");
            }
            StringBuilder out = new StringBuilder();
            String line;
            int nbLines = 0;
            while ((line = reader.readLine()) != null) {
                out
                        .append(line)
                        .append(System.getProperty("line.separator"));
                String author = parserAuthor(line);
                if (author != null) {
                    metadata.add(TikaCoreProperties.CREATOR, author);
                }
                nbLines++;
            }
            metadata.set("LoC", String.valueOf(nbLines));
            Renderer renderer = getRenderer(type.toString());

            String codeAsHtml = renderer.highlight(name, out.toString(), charset.name(), false);
            Document document = Jsoup.parse(codeAsHtml);
            document.quirksMode(Document.QuirksMode.quirks);
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            try {
                NodeTraversor.filter(new TikaNodeFilter(xhtml), document);
            } catch (RuntimeSAXException e) {
                throw e.getWrapped();
            } finally {
                xhtml.endDocument();
            }
        }
    }

    private Renderer getRenderer(String mimeType) throws TikaException {
        MediaType mt = MediaType.parse(mimeType);
        String type = TYPES_TO_RENDERER.get(mt);
        if (type == null) {
            throw new TikaException("unparseable content type " + mimeType);
        }
        return XhtmlRendererFactory.getRenderer(type);
    }


    private String parserAuthor(String line) {
        Matcher m = AUTHORPATTERN.matcher(line);
        if (m.find()) {
            return m
                    .group(1)
                    .trim();
        }

        return null;
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
}
