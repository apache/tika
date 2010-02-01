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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract base class for parsers that deal with package formats.
 * Subclasses can call the
 * {@link #parseArchive(ArchiveInputStream, ContentHandler, Metadata, ParseContext)}
 * method to parse the package stream. Package entries will be written
 * to the XHTML event stream as &lt;div class="package-entry"&gt; elements
 * that contain the (optional) entry name as a &lt;h1&gt; element and the full
 * structured body content of the parsed entry.
 */
public abstract class PackageParser extends DelegatingParser {

    /**
     * Parses the given stream as a package of multiple underlying files.
     * The package entries are parsed using the delegate parser instance.
     * It is not an error if the entry can not be parsed, in that case
     * just the entry name (if given) is emitted.
     *
     * @param stream package stream
     * @param handler content handler
     * @param metadata package metadata
     * @throws IOException if an IO error occurs
     * @throws SAXException if a SAX error occurs
     */
    protected void parseArchive(
            ArchiveInputStream archive, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        ArchiveEntry entry = archive.getNextEntry();
        while (entry != null) {
            if (!entry.isDirectory()) {
                xhtml.startElement("div", "class", "package-entry");
                Metadata entrydata = new Metadata();
                String name = entry.getName();
                if (name != null && name.length() > 0) {
                    entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
                    xhtml.element("h1", name);
                }
                try {
                    // Use the delegate parser to parse this entry
                    super.parse(
                            new CloseShieldInputStream(archive),
                            new EmbeddedContentHandler(
                                    new BodyContentHandler(xhtml)),
                            entrydata, context);
                } catch (TikaException e) {
                    // Could not parse the entry, just skip the content
                }
                xhtml.endElement("div");
            }
            entry = archive.getNextEntry();
        }

        xhtml.endDocument();
    }

}
