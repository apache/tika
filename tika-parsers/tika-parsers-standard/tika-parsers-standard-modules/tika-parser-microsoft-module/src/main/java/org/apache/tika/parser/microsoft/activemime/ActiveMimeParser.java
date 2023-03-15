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
package org.apache.tika.parser.microsoft.activemime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.LittleEndian;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * ActiveMime is a macro container format used in some mso files.  See, e.g.
 * <a href="https://mastodon.social/@Ange/110027138524274526">Ange's toot</a>.
 */
public class ActiveMimeParser extends AbstractParser {

    private static final MediaType MEDIA_TYPE = MediaType.application("x-activemime");
    private static final Set<MediaType> SUPPORTED = Collections.singleton(MEDIA_TYPE);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        //based on: https://mastodon.social/@Ange/110027138524274526
        IOUtils.skipFully(stream, 12); //header
        IOUtils.skipFully(stream, 2); //version
        IOUtils.skipFully(stream, 4); //flag1 040000
        IOUtils.skipFully(stream, 4);//reserved ffffff
        IOUtils.skipFully(stream, 4);//flag2 0000
        long datasize = LittleEndian.readUInt(stream);//datasize
        long zlibOffset = LittleEndian.readUInt(stream);
        IOUtils.skipFully(stream, 4);//flag
        IOUtils.skipFully(stream, 4);//uncompressed size
        IOUtils.skipFully(stream, 4);//don't know

        IOUtils.skipFully(stream, zlibOffset);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        EmbeddedDocumentExtractor ex = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        try (InputStream payload = new DeflateCompressorInputStream(stream)) {
            try (POIFSFileSystem poifs = new POIFSFileSystem(payload)) {
                OfficeParser.extractMacros(poifs, xhtml, ex);
            }
        }
        xhtml.endDocument();
    }
}
