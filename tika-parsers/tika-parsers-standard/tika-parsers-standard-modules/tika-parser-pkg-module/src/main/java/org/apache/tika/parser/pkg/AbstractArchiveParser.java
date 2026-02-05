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
import java.util.Date;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Abstract base class for archive parsers that provides common functionality
 * for handling embedded documents within archives.
 */
public abstract class AbstractArchiveParser extends AbstractEncodingDetectorParser {

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
     * @param createAt   creation date (may be null)
     * @param modifiedAt modification date (may be null)
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
        if (createAt != null) {
            entrydata.set(TikaCoreProperties.CREATED, createAt);
        }
        if (modifiedAt != null) {
            entrydata.set(TikaCoreProperties.MODIFIED, modifiedAt);
        }
        if (size != null) {
            entrydata.set(Metadata.CONTENT_LENGTH, Long.toString(size));
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
}
