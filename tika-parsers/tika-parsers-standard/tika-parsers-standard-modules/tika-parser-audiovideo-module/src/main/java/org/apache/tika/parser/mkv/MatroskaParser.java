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
package org.apache.tika.parser.mkv;

import java.io.IOException;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.video.SubtitleTracks;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Matroska and WebM. Extracts text subtitle tracks (SubRip, ASS/SSA, WebVTT) as embedded
 * {@code text/vtt} documents; no container metadata yet.
 */
@TikaComponent(name = "matroska-parser")
public class MatroskaParser implements Parser {

    private static final long serialVersionUID = 6012879212349816312L;

    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(
            MediaType.application("x-matroska"), MediaType.video("x-matroska"),
            MediaType.audio("x-matroska"), MediaType.video("webm"));

    private boolean extractSubtitles = true;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /** Whether text subtitle tracks are extracted as embedded documents. Default true. */
    public boolean isExtractSubtitles() {
        return extractSubtitles;
    }

    public void setExtractSubtitles(boolean extractSubtitles) {
        this.extractSubtitles = extractSubtitles;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        if (extractSubtitles) {
            SubtitleTracks.readMatroska(tis, metadata, context).emit(xhtml);
        }
        xhtml.endDocument();
    }
}
