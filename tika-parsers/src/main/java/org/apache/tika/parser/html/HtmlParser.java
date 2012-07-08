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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Schema;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * HTML parser. Uses TagSoup to turn the input document to HTML SAX events,
 * and post-processes the events to produce XHTML and metadata expected by
 * Tika clients.
 */
public class HtmlParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = 7895315240498733128L;

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.text("html"),
                MediaType.application("xhtml+xml"),
                MediaType.application("vnd.wap.xhtml+xml"),
                MediaType.application("x-asp"))));

    private static final ServiceLoader LOADER =
            new ServiceLoader(HtmlParser.class.getClassLoader());

    /**
     * HTML schema singleton used to amortise the heavy instantiation time.
     */
    private static final Schema HTML_SCHEMA = new HTMLSchema();

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // Automatically detect the character encoding
        AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata, LOADER);
        try {
            Charset charset = reader.getCharset();
            String previous = metadata.get(Metadata.CONTENT_TYPE);
            if (previous == null || previous.startsWith("text/html")) {
                MediaType type = new MediaType(MediaType.TEXT_HTML, charset);
                metadata.set(Metadata.CONTENT_TYPE, type.toString());
            }
            // deprecated, see TIKA-431
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());

            // Get the HTML mapper from the parse context
            HtmlMapper mapper =
                    context.get(HtmlMapper.class, new HtmlParserMapper());

            // Parse the HTML document
            org.ccil.cowan.tagsoup.Parser parser =
                    new org.ccil.cowan.tagsoup.Parser();

            // TIKA-528: Reuse share schema to avoid heavy instantiation
            parser.setProperty(
                    org.ccil.cowan.tagsoup.Parser.schemaProperty, HTML_SCHEMA);
            // TIKA-599: Shared schema is thread-safe only if bogons are ignored
            parser.setFeature(
                    org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true);

            parser.setContentHandler(new XHTMLDowngradeHandler(
                    new HtmlHandler(mapper, handler, metadata)));

            parser.parse(reader.asInputSource());
        } finally {
            reader.close();
        }
    }

    /**
     * Maps "safe" HTML element names to semantic XHTML equivalents. If the
     * given element is unknown or deemed unsafe for inclusion in the parse
     * output, then this method returns <code>null</code> and the element
     * will be ignored but the content inside it is still processed. See
     * the {@link #isDiscardElement(String)} method for a way to discard
     * the entire contents of an element.
     * <p>
     * Subclasses can override this method to customize the default mapping.
     *
     * @deprecated Use the {@link HtmlMapper} mechanism to customize
     *             the HTML mapping. This method will be removed in Tika 1.0.
     * @since Apache Tika 0.5
     * @param name HTML element name (upper case)
     * @return XHTML element name (lower case), or
     *         <code>null</code> if the element is unsafe 
     */
    protected String mapSafeElement(String name) {
        return DefaultHtmlMapper.INSTANCE.mapSafeElement(name);
    }

    /**
     * Checks whether all content within the given HTML element should be
     * discarded instead of including it in the parse output. Subclasses
     * can override this method to customize the set of discarded elements.
     *
     * @deprecated Use the {@link HtmlMapper} mechanism to customize
     *             the HTML mapping. This method will be removed in Tika 1.0.
     * @since Apache Tika 0.5
     * @param name HTML element name (upper case)
     * @return <code>true</code> if content inside the named element
     *         should be ignored, <code>false</code> otherwise
     */
    protected boolean isDiscardElement(String name) {
        return DefaultHtmlMapper.INSTANCE.isDiscardElement(name);
    }

    /**
    * @deprecated Use the {@link HtmlMapper} mechanism to customize
    *             the HTML mapping. This method will be removed in Tika 1.0.
    **/
    public String mapSafeAttribute(String elementName, String attributeName) {
        return DefaultHtmlMapper.INSTANCE.mapSafeAttribute(elementName,attributeName) ;
    }    
    
    /**
     * Adapter class that maintains backwards compatibility with the
     * protected HtmlParser methods. Making HtmlParser implement HtmlMapper
     * directly would require those methods to be public, which would break
     * backwards compatibility with subclasses.
     *
     * @deprecated Use the {@link HtmlMapper} mechanism to customize
     *             the HTML mapping. This class will be removed in Tika 1.0.
     */
    private class HtmlParserMapper implements HtmlMapper {
        public String mapSafeElement(String name) {
            return HtmlParser.this.mapSafeElement(name);
        }
        public boolean isDiscardElement(String name) {
            return HtmlParser.this.isDiscardElement(name);
        }
        public String mapSafeAttribute(String elementName, String attributeName){
            return HtmlParser.this.mapSafeAttribute(elementName,attributeName);
        }
    }

}
