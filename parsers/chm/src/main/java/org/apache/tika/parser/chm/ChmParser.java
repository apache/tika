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
package org.apache.tika.parser.chm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.core.ChmExtractor;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ChmParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = 5938777307516469802L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    MediaType.application("vnd.ms-htmlhelp"),
                    MediaType.application("chm"),
                    MediaType.application("x-chm"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        ChmExtractor chmExtractor = new ChmExtractor(stream);

        // metadata
        metadata.set(Metadata.CONTENT_TYPE, "application/vnd.ms-htmlhelp");

        // content
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        Iterator<DirectoryListingEntry> it =
                chmExtractor.getChmDirList().getDirectoryListingEntryList().iterator();
        while (it.hasNext()) {
            DirectoryListingEntry entry = it.next();
            if (entry.getName().endsWith(".html") || entry.getName().endsWith(".htm")) {
                xhtml.characters(extract(chmExtractor.extractChmEntry(entry)));
            }
        }

        xhtml.endDocument();
    }

    /**
     * Extracts data from byte[]
     */
    private String extract(byte[] byteObject) throws TikaException {// throws IOException
        StringBuilder wBuf = new StringBuilder();
        InputStream stream = null;
        Metadata metadata = new Metadata();
        HtmlParser htmlParser = new HtmlParser();
        BodyContentHandler handler = new BodyContentHandler(-1);// -1
        ParseContext parser = new ParseContext();
        try {
            stream = new ByteArrayInputStream(byteObject);
            htmlParser.parse(stream, handler, metadata, parser);
            wBuf.append(handler.toString()
                    + System.getProperty("line.separator"));
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            // Pushback overflow from tagsoup
        }
        return wBuf.toString();
    }


}
