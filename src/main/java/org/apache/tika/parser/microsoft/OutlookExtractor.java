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
package org.apache.tika.parser.microsoft;

import java.io.IOException;

import org.apache.poi.hsmf.datatypes.Chunks;
import org.apache.poi.hsmf.datatypes.StringChunk;
import org.apache.poi.hsmf.exceptions.ChunkNotFoundException;
import org.apache.poi.hsmf.parsers.POIFSChunkParser;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Outlook Message Parser.
 */
class OutlookExtractor {

    private static final Chunks CHUNKS = Chunks.getInstance();

    private final POIFSChunkParser parser;

    public OutlookExtractor(POIFSFileSystem filesystem) throws TikaException {
        try {
            this.parser = new POIFSChunkParser(filesystem);
        } catch (IOException e) {
            throw new TikaException("Failed to parse Outlook chunks", e);
        }
    }

    public void parse(XHTMLContentHandler xhtml, Metadata metadata)
            throws TikaException, SAXException {
        String subject = getChunk(CHUNKS.subjectChunk);
        String from = getChunk(CHUNKS.displayFromChunk);

        metadata.set(Metadata.AUTHOR, from);
        metadata.set(Metadata.TITLE, subject);
        metadata.set(Metadata.SUBJECT, getChunk(CHUNKS.conversationTopic));

        xhtml.element("h1", subject);
        xhtml.characters("\n");

        xhtml.startElement("dl");
        header(xhtml, "From", from);
        header(xhtml, "To", getChunk(CHUNKS.displayToChunk));
        header(xhtml, "Cc", getChunk(CHUNKS.displayCCChunk));
        header(xhtml, "Bcc", getChunk(CHUNKS.displayBCCChunk));
        xhtml.endElement("dl");
        xhtml.characters("\n");

        xhtml.element("p", getChunk(CHUNKS.textBodyChunk));
    }

    private void header(XHTMLContentHandler xhtml, String key, String value)
            throws SAXException {
        if (value.length() > 0) {
            xhtml.element("dt", key);
            xhtml.characters("\t");
            xhtml.element("dd", value);
            xhtml.characters("\n");
        }
    }

    /**
     * Returns the content of the identified string chunk in the
     * current document. Returns the empty string if the identified
     * chunk does not exist in the current document.
     *
     * @param chunk string chunk identifier
     * @return content of the identified chunk, or the empty string
     */
    private String getChunk(StringChunk chunk) {
        try {
            return parser.getDocumentNode(chunk).toString();
        } catch (ChunkNotFoundException e) {
            return "";
        }
    }

}
