/**
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
package org.apache.tika.sax;

import java.io.OutputStream;
import java.io.Writer;

import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;
import org.xml.sax.ContentHandler;

/**
 * Content handler decorator that only passes everything inside
 * the XHTML &lt;body/&gt; tag to the underlying handler. Note that
 * the &lt;body/&gt; tag itself is <em>not</em> passed on.
 */
public class BodyContentHandler extends ContentHandlerDecorator {

    /**
     * XHTML XPath parser.
     */
    private static final XPathParser PARSER =
        new XPathParser("xhtml", XHTMLContentHandler.XHTML);

    /**
     * The XPath matcher used to select the XHTML body contents.
     */
    private static final Matcher MATCHER =
        PARSER.parse("/xhtml:html/xhtml:body/descendant:node()");

    /**
     * Creates a content handler that passes all XHTML body events to the
     * given underlying content handler.
     *
     * @param handler content handler
     */
    public BodyContentHandler(ContentHandler handler) {
        super(new MatchingContentHandler(handler, MATCHER));
    }

    /**
     * Creates a content handler that writes XHTML body character events to
     * the given writer.
     *
     * @param writer writer
     */
    public BodyContentHandler(Writer writer) {
        this(new XHTMLToTextContentHandler(new WriteOutContentHandler(writer)));
    }

    /**
     * Creates a content handler that writes XHTML body character events to
     * the given output stream using the default encoding.
     *
     * @param stream output stream
     */
    public BodyContentHandler(OutputStream stream) {
        this(new XHTMLToTextContentHandler(new WriteOutContentHandler(stream)));
    }

    /**
     * Creates a content handler that writes XHTML body character events to
     * an internal string buffer. The contents of the buffer can be retrieved
     * using the {@link #toString()} method.
     */
    public BodyContentHandler() {
        this(new XHTMLToTextContentHandler(new WriteOutContentHandler()));
    }

}
