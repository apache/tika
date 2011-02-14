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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.IOException;

import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Interface implemented by all Tika OOXML extractors.
 * 
 * @see org.apache.poi.POIXMLTextExtractor
 */
public interface OOXMLExtractor {

    /**
     * Returns the opened document.
     * 
     * @see POIXMLTextExtractor#getDocument()
     */
    POIXMLDocument getDocument();

    /**
     * {@link POIXMLTextExtractor#getMetadataTextExtractor()} not yet supported
     * for OOXML by POI.
     */
    MetadataExtractor getMetadataExtractor();

    /**
     * Parses the document into a sequence of XHTML SAX events sent to the
     * given content handler.
     */
    void getXHTML(ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, XmlException, IOException, TikaException;
}
