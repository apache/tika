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
package org.apache.tika.parser.ms;

// JDK imports
import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.poifs.eventfilesystem.POIFSReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.XHTMLContentHandler;
import org.apache.tika.utils.RereadableInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Defines a Microsoft document content extractor.
 */
public abstract class MSParser implements Parser {

    private final int MEMORY_THRESHOLD = 1024 * 1024;

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        RereadableInputStream ris =
            new RereadableInputStream(stream, MEMORY_THRESHOLD, true, false);
        try {
            // First, extract properties
            POIFSReader reader = new POIFSReader();
            reader.registerListener(
                    new PropertiesReaderListener(metadata),
                    SummaryInformation.DEFAULT_STREAM_NAME);

            if (stream.available() > 0) {
                reader.read(ris);
            }
            while (ris.read() != -1) {
            }
            ris.rewind();
            // Extract document full text
            XHTMLContentHandler xhtml =
                new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            xhtml.element("p", extractText(ris));
            xhtml.endDocument();
        } catch (IOException e) {
            throw e;
        } catch (TikaException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaException("Parse error", e);
        } finally {
            ris.close();
        }
    }

    /**
     * Extracts the text content from a Microsoft document input stream.
     */
    protected abstract String extractText(InputStream input) throws Exception;

}