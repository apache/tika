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
import java.io.InputStream;

import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Office Open XML (OOXML) parser.
 * 
 */
public class OOXMLParser implements Parser {

    /**
     * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
     *      org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata)
     */
    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata) throws IOException, SAXException, TikaException {

        try {
            OOXMLExtractor extractor = OOXMLExtractorFactory
                    .createExtractor((POIXMLTextExtractor) ExtractorFactory
                            .createExtractor(stream));
            extractor.getXHTML(handler, metadata);
            extractor.getMetadataExtractor().extract(metadata);

        } catch (InvalidFormatException e) {
            throw new TikaException("Error creating OOXML extractor", e);
        } catch (OpenXML4JException e) {
            throw new TikaException("Error creating OOXML extractor", e);
        } catch (XmlException e) {
            throw new TikaException("Error creating OOXML extractor", e);
        }
    }

}
