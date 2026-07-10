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
package org.apache.tika.parser.image;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;


@TikaComponent
public class HeifParser extends AbstractImageParser {

    private static final Set<MediaType> SUPPORTED_TYPES = new HashSet<>(
            Arrays.asList(MediaType.image("heif"), MediaType.image("heif-sequence"),
                    MediaType.image("heic"), MediaType.image("heic-sequence")));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    void extractMetadata(InputStream stream, ContentHandler contentHandler, Metadata metadata,
                         ParseContext parseContext)
            throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp, metadata);
            File file = tis.getFile();   // spool so the file can be re-read below
            // XMP first so it is canonical; metadata-extractor (EXIF/GPS) fills gaps.
            // HEIF XMP is an application/rdf+xml item that is xpacket-wrapped in practice
            // (iPhone/Adobe), so the shared packet scanner finds it; if it is not wrapped,
            // fall back to locating the item via the meta/iinf/iloc boxes.
            if (!ImageXmp.scanAndExtract(tis, metadata, parseContext)) {
                HeifXmp.extract(file, metadata, parseContext);
            }
            try (InputStream heif = Files.newInputStream(file.toPath())) {
                new ImageMetadataExtractor(metadata).parseHeif(heif);
            }
        } finally {
            tmp.dispose();
        }
    }


}
