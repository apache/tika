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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Abstract base class for archive parsers that provides common functionality
 * for handling embedded documents within archives.
 */
public abstract class AbstractArchiveParser extends AbstractEncodingDetectorParser {

    private static final DateTimeFormatter ZONELESS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);

    public AbstractArchiveParser() {
        super();
    }

    public AbstractArchiveParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    /**
     * Handles metadata for an archive entry and writes appropriate XHTML elements.
     *
     * @param name       the entry name
     * @param createAt   creation instant (may be null), stored as {@link FileSystem#CREATED}
     * @param modifiedAt modification instant (may be null), stored as {@link FileSystem#MODIFIED}
     * @param size       entry size (may be null)
     * @param xhtml      the XHTML content handler
     * @param context    the parse context
     * @return metadata object populated with entry information
     */
    public static Metadata handleEntryMetadata(String name, Date createAt, Date modifiedAt,
                                               Long size, XHTMLContentHandler xhtml,
                                               ParseContext context)
            throws SAXException, IOException, TikaException {
        Metadata entrydata = Metadata.newInstance(context);
        // entry times are file-system times, never the embedded document's own dates
        if (createAt != null) {
            entrydata.set(FileSystem.CREATED, createAt);
        }
        if (modifiedAt != null) {
            entrydata.set(FileSystem.MODIFIED, modifiedAt);
        }
        if (size != null) {
            entrydata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(size));
        }
        if (name != null && name.length() > 0) {
            name = name.replace("\\", "/");
            entrydata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            entrydata.set(TikaCoreProperties.INTERNAL_PATH, name);
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            attributes.addAttribute("", "id", "id", "CDATA", name);
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        }
        return entrydata;
    }

    /**
     * For formats that store local wall-clock time with no zone (MS-DOS times in zip, rar, arj):
     * the library resolved it in the JVM default zone, so undo that and store it zone-less.
     */
    static void setLocalTime(Metadata metadata, Property property, Date resolvedInDefaultZone) {
        if (resolvedInDefaultZone != null) {
            metadata.set(property, ZONELESS.format(
                    LocalDateTime.ofInstant(resolvedInDefaultZone.toInstant(), ZoneId.systemDefault())));
        }
    }
}
