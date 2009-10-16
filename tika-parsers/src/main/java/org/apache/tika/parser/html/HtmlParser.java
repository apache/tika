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
import java.util.Collections;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * HTML parser. Uses CyberNeko to turn the input document to HTML SAX events,
 * and post-processes the events to produce XHTML and metadata expected by
 * Tika clients.
 */
public class HtmlParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, Map<String, Object> context)
            throws IOException, SAXException, TikaException {
        // Protect the stream from being closed by CyberNeko
        stream = new CloseShieldInputStream(stream);

        // Prepare the input source using the encoding hint if available
        InputSource source = new InputSource(stream); 
        String encoding = metadata.get(Metadata.CONTENT_ENCODING); 
        if (encoding != null) { 
            source.setEncoding(encoding);
        }

        // Parse the HTML document
        org.ccil.cowan.tagsoup.Parser parser =
            new org.ccil.cowan.tagsoup.Parser();
        parser.setContentHandler(
                new XHTMLDowngradeHandler(new HtmlHandler(handler, metadata)));
        parser.parse(source);
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        Map<String, Object> context = Collections.emptyMap();
        parse(stream, handler, metadata, context);
    }

}
