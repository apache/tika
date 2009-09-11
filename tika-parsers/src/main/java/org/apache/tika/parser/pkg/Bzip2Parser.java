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
import java.util.Map;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Bzip2 parser.
 */
public class Bzip2Parser extends DelegatingParser {

    /**
     * Parses the given stream as a bzip2 file.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, Map<String, Object> context)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "application/x-bzip");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // At the end we want to close the bzip2 stream to release any associated
        // resources, but the underlying document stream should not be closed
        InputStream bzip2 =
            new BZip2CompressorInputStream(new CloseShieldInputStream(stream));
        try {
            Metadata entrydata = new Metadata();
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
            if (name != null) {
                if (name.endsWith(".tbz")) {
                    name = name.substring(0, name.length() - 4) + ".tar";
                } else if (name.endsWith(".tbz2")) {
                    name = name.substring(0, name.length() - 5) + ".tar";
                } else if (name.endsWith(".bz")) {
                    name = name.substring(0, name.length() - 3);
                } else if (name.endsWith(".bz2")) {
                    name = name.substring(0, name.length() - 4);
                }
                entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
            }
            // Use the delegate parser to parse the compressed document
            super.parse(
                    new CloseShieldInputStream(bzip2),
                    new EmbeddedContentHandler(
                            new BodyContentHandler(xhtml)),
                    entrydata, context);
        } finally {
            bzip2.close();
        }

        xhtml.endDocument();
    }

}
