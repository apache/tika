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
package org.apache.tika.parser.zip;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Zip File Parser.
 */
public class ZipParser extends AbstractParser {

    private Parser parser;

    /**
     * Parses the given stream as a Zip file.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, TikaException, SAXException {
        metadata.set(Metadata.CONTENT_TYPE, "application/zip");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // At the end we want to close the Zip stream to release any associated
        // resources, but the underlying document stream should not be closed
        ZipInputStream zip =
            new ZipInputStream(new CloseShieldInputStream(stream));
        try {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                parseEntry(xhtml, entry, zip);
                entry = zip.getNextEntry();
            }
        } finally {
            zip.close();
        }

        xhtml.endDocument();
    }

    /**
     * Parses the given Zip entry using the underlying parser instance.
     * It is not an error if the entry can not be parsed, in that case
     * just the entry name is emitted.
     *
     * @param xhtml XHTML event handler
     * @param entry zip entry
     * @param stream zip stream
     * @throws IOException if an IO error occurs
     * @throws SAXException if a SAX error occurs
     */
    private void parseEntry(
            XHTMLContentHandler xhtml, ZipEntry entry, InputStream stream)
            throws IOException, SAXException {
        xhtml.startElement("div", "class", "file");
        xhtml.element("h1", entry.getName());

        try {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, entry.getName());
            getParser().parse(
                    new CloseShieldInputStream(stream),
                    new BodyContentHandler(xhtml),
                    metadata);
        } catch (TikaException e) {
            // Could not parse the entry, just skip the content
        }

        xhtml.endElement("div");
    }

    public Parser getParser() {
        if (parser == null)
        {
            return new AutoDetectParser();
        }
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }
}
