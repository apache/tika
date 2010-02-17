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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * HTML parser. Uses TagSoup to turn the input document to HTML SAX events,
 * and post-processes the events to produce XHTML and metadata expected by
 * Tika clients.
 */
public class HtmlParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.text("html"),
                MediaType.application("xhtml+xml"),
                MediaType.application("vnd.wap.xhtml+xml"),
                MediaType.application("x-asp"))));

    /**
     * The default HTML mapping.
     */
    private static final HtmlMapper mapper = new DefaultHtmlMapper();

    // Use the widest, most common charset as our default.
    private static final String DEFAULT_CHARSET = "windows-1252";
    // TIKA-357 - use bigger buffer for meta tag sniffing (was 4K)
    private static final int META_TAG_BUFFER_SIZE = 8192;
    private static final Pattern HTTP_EQUIV_PATTERN = Pattern.compile(
                    "(?is)<meta\\s+http-equiv\\s*=\\s*['\\\"]\\s*" +
                    "Content-Type['\\\"]\\s+content\\s*=\\s*['\\\"]" +
                    "([^'\\\"]+)['\\\"]");

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * TIKA-332: Check for meta http-equiv tag with charset info in
     * HTML content.
     * <p>
     * TODO: Move this into core, along with CharsetDetector
     */ 
    private String getEncoding(InputStream stream, Metadata metadata) throws IOException {
        stream.mark(META_TAG_BUFFER_SIZE);
        char[] buffer = new char[META_TAG_BUFFER_SIZE];
        InputStreamReader isr = new InputStreamReader(stream, "us-ascii");
        int bufferSize = isr.read(buffer);
        stream.reset();

        if (bufferSize != -1) {
            String metaString = new String(buffer, 0, bufferSize);
            Matcher m = HTTP_EQUIV_PATTERN.matcher(metaString);
            if (m.find()) {
                // TIKA-349: flexible handling of attributes
                // We have one or more x or x=y attributes, separated by ';'
                String[] attrs = m.group(1).split(";");
                for (String attr : attrs) {
                    String[] keyValue = attr.trim().split("=");
                    if ((keyValue.length == 2) && keyValue[0].equalsIgnoreCase("charset")) {
                        String charset = keyValue[1];
                        if (Charset.isSupported(charset)) {
                            metadata.set(Metadata.CONTENT_ENCODING, charset);
                            return charset;
                        }
                    }
                }
            }
        }

        // No charset in a meta http-equiv tag, see if it's in the passed content-encoding
        // hint, or the passed content-type hint.
        CharsetDetector detector = new CharsetDetector();
        String incomingCharset = metadata.get(Metadata.CONTENT_ENCODING);
        String incomingType = metadata.get(Metadata.CONTENT_TYPE);
        if (incomingCharset == null && incomingType != null) {
            // TIKA-341: Use charset in content-type
            MediaType mt = MediaType.parse(incomingType);
            if (mt != null) {
                String charset = mt.getParameters().get("charset");
                if ((charset != null) && Charset.isSupported(charset)) {
                    incomingCharset = charset;
                }
            }
        }

        if (incomingCharset != null) {
            detector.setDeclaredEncoding(incomingCharset);
        }

        // TIKA-341 without enabling input filtering (stripping of tags) the
        // short HTML tests don't work well.
        detector.enableInputFilter(true);
        detector.setText(stream);
        for (CharsetMatch match : detector.detectAll()) {
            if (Charset.isSupported(match.getName())) {
                metadata.set(Metadata.CONTENT_ENCODING, match.getName());

                // TIKA-339: Don't set language, as it's typically not a very good
                // guess, and it can create ambiguity if another (better) language
                // value is specified by a meta tag in the HTML (or via HTTP response
                // header).
                /*
                String language = match.getLanguage();
                if (language != null) {
                    metadata.set(Metadata.CONTENT_LANGUAGE, match.getLanguage());
                    metadata.set(Metadata.LANGUAGE, match.getLanguage());
                }
                */
                
                break;
            }
        }

        String encoding = metadata.get(Metadata.CONTENT_ENCODING);
        if (encoding == null) {
            if (Charset.isSupported(DEFAULT_CHARSET)) {
                encoding = DEFAULT_CHARSET;
            } else {
                encoding = Charset.defaultCharset().name();
            }
            
            metadata.set(Metadata.CONTENT_ENCODING, encoding);
        }

        return encoding;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // The getEncoding() method depends on the mark feature
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }

        // Protect the stream from being closed by CyberNeko
        // TODO: Is this still needed, given our use of TagSoup?
        stream = new CloseShieldInputStream(stream);

        // Prepare the input source using the encoding hint if available
        InputSource source = new InputSource(stream); 
        source.setEncoding(getEncoding(stream, metadata));

        // Get the HTML mapper from the parse context
        HtmlMapper mapper =
            context.get(HtmlMapper.class, new HtmlParserMapper());

        // Parse the HTML document
        org.ccil.cowan.tagsoup.Parser parser =
            new org.ccil.cowan.tagsoup.Parser();
        parser.setContentHandler(new XHTMLDowngradeHandler(
                new HtmlHandler(mapper, handler, metadata)));
        parser.parse(source);
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
    throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
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
        return mapper.mapSafeElement(name);
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
        return "STYLE".equals(name) || "SCRIPT".equals(name);
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
    }

}
