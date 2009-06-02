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
package org.apache.tika.parser.pkg;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract base class for parsers that deal with package formats.
 * Subclasses can call the
 * {@link #parseEntry(InputStream, XHTMLContentHandler, Metadata)}
 * method to parse the given package entry using the configured
 * entry parser. The entries will be written to the XHTML event stream
 * as &lt;div class="package-entry"&gt; elements that contain the
 * (optional) entry name as a &lt;h1&gt; element and the full
 * structured body content of the parsed entry.
 */
public abstract class PackageParser extends DelegatingParser {

    /**
     * Parses the given entry entry using the delegate parser instance.
     * It is not an error if the entry can not be parsed, in that case
     * just the entry name (if given) is emitted.
     *
     * @param stream entry stream
     * @param xhtml XHTML event handler
     * @param metadata entry metadata
     * @throws IOException if an IO error occurs
     * @throws SAXException if a SAX error occurs
     */
    protected void parseEntry(
            InputStream stream, XHTMLContentHandler xhtml, Metadata metadata)
            throws IOException, SAXException {
        xhtml.startElement("div", "class", "package-entry");

        String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (name != null) {
            xhtml.element("h1", name);
        }

        try {
            super.parse(
                    new CloseShieldInputStream(stream),
                    new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                    metadata);
        } catch (TikaException e) {
            // Could not parse the entry, just skip the content
        }

        xhtml.endElement("div");
    }

}
