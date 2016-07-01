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

package org.apache.tika.parser.iwork.iwana;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class IWork13PackageParser extends AbstractParser {

    public enum IWork13DocumentType {
        KEYNOTE13(MediaType.application("vnd.apple.keynote.13")),
        NUMBERS13(MediaType.application("vnd.apple.numbers.13")),
        PAGES13(MediaType.application("vnd.apple.pages.13")),
        UNKNOWN13(MediaType.application("vnd.apple.unknown.13"));

        private final MediaType mediaType;

        IWork13DocumentType(MediaType mediaType) {
            this.mediaType = mediaType;
        }

        public MediaType getType() {
            return mediaType;
        }

        public static MediaType detect(ZipFile zipFile) {
            ZipArchiveEntry entry = zipFile.getEntry("Index/MasterSlide.iwa");
            if (zipFile.getEntry("Index/MasterSlide.iwa") != null ||
                    zipFile.getEntry("Index/Slide.iwa") != null) {
                return KEYNOTE13.getType();
            }
            //TODO: figure out how to distinguish numbers from pages
            return UNKNOWN13.getType();
        }
    }

    /**
     * All iWork 13 files contain this, so we can detect based on it
     */
    public final static String IWORK13_COMMON_ENTRY = "Metadata/BuildVersionHistory.plist";

    private final static Set<MediaType> supportedTypes = Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
            IWork13DocumentType.KEYNOTE13.getType(),
            IWork13DocumentType.NUMBERS13.getType(),
            IWork13DocumentType.PAGES13.getType()
            )));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        //no-op for now
    }
}
