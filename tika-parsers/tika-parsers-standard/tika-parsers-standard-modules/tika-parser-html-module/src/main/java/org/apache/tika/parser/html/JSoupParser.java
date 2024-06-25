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
package org.apache.tika.parser.html;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.config.Field;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;


/**
 * HTML parser. Uses JSoup to turn the input document to HTML SAX events,
 * and post-processes the events to produce XHTML and metadata expected by
 * Tika clients.
 */
public class JSoupParser extends AbstractEncodingDetectorParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 7895315240498733128L;

    public static final Charset DEFAULT_CHARSET = StandardCharsets.US_ASCII;

    private static final MediaType XHTML = MediaType.application("xhtml+xml");
    private static final MediaType WAP_XHTML = MediaType.application("vnd.wap.xhtml+xml");
    private static final MediaType X_ASP = MediaType.application("x-asp");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<MediaType>(Arrays.asList(MediaType.text("html"), XHTML, WAP_XHTML, X_ASP)));

    @Field
    private boolean extractScripts = false;

    public JSoupParser() {
        super();
    }

    public JSoupParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public boolean isExtractScripts() {
        return extractScripts;
    }

    /**
     * Whether or not to extract contents in script entities.
     * Default is <code>false</code>
     *
     * @param extractScripts
     */
    @Field
    public void setExtractScripts(boolean extractScripts) {
        this.extractScripts = extractScripts;
    }


    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        EncodingDetector encodingDetector = getEncodingDetector(context);
        Charset charset = encodingDetector.detect(stream, metadata);
        charset = charset == null ? DEFAULT_CHARSET : charset;
        String previous = metadata.get(Metadata.CONTENT_TYPE);
        MediaType contentType = null;
        if (previous == null || previous.startsWith("text/html")) {
            contentType = new MediaType(MediaType.TEXT_HTML, charset);
        } else if (previous.startsWith("application/xhtml+xml")) {
            contentType = new MediaType(XHTML, charset);
        } else if (previous.startsWith("application/vnd.wap.xhtml+xml")) {
            contentType = new MediaType(WAP_XHTML, charset);
        } else if (previous.startsWith("application/x-asp")) {
            contentType = new MediaType(X_ASP, charset);
        }
        if (contentType != null) {
            metadata.set(Metadata.CONTENT_TYPE, contentType.toString());
        }
        // deprecated, see TIKA-431
        metadata.set(Metadata.CONTENT_ENCODING, charset.name());

        // Get the HTML mapper from the parse context
        HtmlMapper mapper = context.get(HtmlMapper.class, new DefaultHtmlMapper());

        //do better with baseUri?
        Document document = Jsoup.parse(stream, charset.name(), "");
        document.quirksMode(Document.QuirksMode.quirks);
        ContentHandler xhtml = new XHTMLDowngradeHandler(
                new HtmlHandler(mapper, handler, metadata, context, extractScripts));
        xhtml.startDocument();
        try {
            NodeTraversor.filter(new TikaNodeFilter(xhtml), document);
        } catch (RuntimeSAXException e) {
            throw e.getWrapped();
        } finally {
            xhtml.endDocument();
        }
    }

    public void parseString(String html, ContentHandler handler, Metadata metadata, ParseContext context) throws SAXException {
        // Get the HTML mapper from the parse context
        HtmlMapper mapper = context.get(HtmlMapper.class, new DefaultHtmlMapper());

        //do better with baseUri?
        Document document = Jsoup.parse(html);
        document.quirksMode(Document.QuirksMode.quirks);
        ContentHandler xhtml = new XHTMLDowngradeHandler(
                new HtmlHandler(mapper, handler, metadata, context, extractScripts));
        xhtml.startDocument();
        try {
            NodeTraversor.filter(new TikaNodeFilter(xhtml), document);
        } catch (RuntimeSAXException e) {
            throw e.getWrapped();
        } finally {
            xhtml.endDocument();
        }
    }

    private class TikaNodeFilter implements NodeFilter {
        ContentHandler handler;

        private TikaNodeFilter(ContentHandler handler) {
            this.handler = handler;
        }

        @Override
        public NodeFilter.FilterResult head(Node node, int i) {

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
                return FilterResult.CONTINUE;
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
                return FilterResult.CONTINUE;
            }
            AttributesImpl attributes = new AttributesImpl();
            Iterator<Attribute> jsoupAttrs = node.attributes().iterator();
            while (jsoupAttrs.hasNext()) {
                Attribute jsoupAttr = jsoupAttrs.next();
                attributes.addAttribute("", jsoupAttr.getKey(), jsoupAttr.getKey(), "",
                        jsoupAttr.getValue());
            }
            try {
                handler.startElement(XMLConstants.NULL_NS_URI, node.nodeName(), node.nodeName(),
                        attributes);
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
            return FilterResult.CONTINUE;
        }

        @Override
        public NodeFilter.FilterResult tail(Node node, int i) {
            if (node instanceof TextNode || node instanceof DataNode) {
                return FilterResult.CONTINUE;
            }
            try {
                handler.endElement(XMLConstants.NULL_NS_URI, node.nodeName(), node.nodeName());
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
            return FilterResult.CONTINUE;
        }
    }

    private class RuntimeSAXException extends RuntimeException {
        private SAXException wrapped;

        private RuntimeSAXException(SAXException e) {
            this.wrapped = e;
        }

        SAXException getWrapped() {
            return wrapped;
        }
    }

    /**
     * Look for an EncodingDetetor in the ParseContext.  If it hasn't been
     * passed in, use the original EncodingDetector from initialization.
     *
     * @param parseContext
     * @return
     */
    protected EncodingDetector getEncodingDetector(ParseContext parseContext) {

        EncodingDetector fromParseContext = parseContext.get(EncodingDetector.class);
        if (fromParseContext != null) {
            return fromParseContext;
        }

        return getEncodingDetector();
    }

}
