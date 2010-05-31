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
package org.apache.tika.parser.iwork;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A parser for the IWork formats.
 *
 * Currently supported formats:
 * <ol>
 * <li>Keynote format version 2.x. Currently only tested with Keynote version 5.x
 * <li>Pages format version 1.x. Currently only tested with Pages version 4.0.x
 * <li>Numbers format version 1.x. Currently only tested with Numbers version 2.0.x
 * </ol>
 */
public class IWorkParser implements Parser {

    private final static Set<MediaType> supportedTypes =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.application("vnd.apple.keynote"),
                MediaType.application("vnd.apple.pages"),
                MediaType.application("vnd.apple.numbers")
        )));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        ArchiveInputStream zip =
            new ZipArchiveInputStream(new CloseShieldInputStream(stream));
        ArchiveEntry entry = zip.getNextEntry();
        while (entry != null) {
            if ("index.apxl".equals(entry.getName())) {
                metadata.set(
                        Metadata.CONTENT_TYPE, "application/vnd.apple.keynote");
                context.getSAXParser().parse(
                        new CloseShieldInputStream(zip),
                        new OfflineContentHandler(
                                new KeynoteContentHandler(xhtml, metadata)));
            } else if ("index.xml".equals(entry.getName())) {
                // Numbers and Pages have both index.xml as file, so the appropriate content handler can only be
                // selected based on the content in the file. In this case the content handler is selected
                // based on root element.
                IWorkRootElementDetectContentHandler detectHandler =
                    new IWorkRootElementDetectContentHandler(xhtml, metadata);
                context.getSAXParser().parse(
                        new CloseShieldInputStream(zip),
                        new OfflineContentHandler(detectHandler));
            }
            entry = zip.getNextEntry();
        }
        zip.close();

        xhtml.endDocument();
    }


    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }
}
