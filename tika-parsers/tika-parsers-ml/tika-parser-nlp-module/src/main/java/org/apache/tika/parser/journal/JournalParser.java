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

package org.apache.tika.parser.journal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParser;

public class JournalParser implements Parser {

    /**
     * Generated serial ID
     */
    private static final long serialVersionUID = 4664255544154296438L;

    private static final MediaType TYPE = MediaType.application("pdf");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(TYPE);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        TikaInputStream tis = TikaInputStream.get(stream, new TemporaryResources(), metadata);
        File tmpFile = tis.getFile();

        GrobidRESTParser grobidParser = new GrobidRESTParser();
        grobidParser.parse(tmpFile.getAbsolutePath(), handler, metadata, context);

        try (InputStream pdfStream = new FileInputStream(tmpFile)) {
            PDFParser parser = new PDFParser();
            parser.parse(pdfStream, handler, metadata, context);
        }
    }
}
