/**
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
package org.apache.tika.parser.microsoft;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Defines a Microsoft document content extractor.
 */
public abstract class OfficeParser implements Parser {

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        POIFSFileSystem filesystem = new POIFSFileSystem(stream);

        metadata.set(Metadata.CONTENT_TYPE, getContentType());
        getMetadata(
                filesystem, SummaryInformation.DEFAULT_STREAM_NAME, metadata);
        getMetadata(
                filesystem, DocumentSummaryInformation.DEFAULT_STREAM_NAME,
                metadata);

        parse(filesystem, handler, metadata);
    }

    /**
     * The content type of the document being parsed.
     *
     * @return MIME content type
     */
    protected abstract String getContentType();

    /**
     * Extracts the text content from a Microsoft document input stream.
     */
    protected abstract void parse(
            POIFSFileSystem filesystem, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException;

    private void getMetadata(
            POIFSFileSystem filesystem, String name, Metadata metadata)
            throws IOException, SAXException, TikaException {
        try {
            InputStream stream = filesystem.createDocumentInputStream(name);
            try {
                new PropertyParser().parse(stream, new DefaultHandler(), metadata);
            } finally {
                stream.close();
            }
        } catch (FileNotFoundException e) {
            // summary information not available, ignore
        }
    }

}
