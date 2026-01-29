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
package org.apache.tika.parser.ogg;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.OggStreamIdentifier;
import org.gagravarr.vorbis.VorbisFile;
import org.gagravarr.vorbis.VorbisInfo;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for OGG Vorbis audio files.
 */
@TikaComponent
public class VorbisParser extends OggAudioParser {
    private static final long serialVersionUID = 5904981674814527529L;

    protected static final MediaType OGG_VORBIS =
            MediaType.parse(OggStreamIdentifier.OGG_VORBIS.mimetype);

    private static List<MediaType> TYPES = Arrays.asList(OGG_VORBIS);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return new HashSet<>(TYPES);
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        metadata.set(Metadata.CONTENT_TYPE, OGG_VORBIS.toString());
        metadata.set(XMPDM.AUDIO_COMPRESSOR, "Vorbis");

        // Open and process the files
        OggFile ogg = new OggFile(tis);
        VorbisFile vorbis = new VorbisFile(ogg);

        // Start
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        // Extract the common Vorbis info
        extractInfo(metadata, vorbis.getInfo());

        // Extract any Vorbis comments
        extractComments(metadata, xhtml, vorbis.getComment());

        // Extract the audio length
        extractDuration(metadata, xhtml, vorbis, vorbis);

        // Finish
        xhtml.endDocument();
        vorbis.close();
    }

    protected void extractInfo(Metadata metadata, VorbisInfo info) throws TikaException {
        metadata.set(XMPDM.AUDIO_SAMPLE_RATE, (int) info.getRate());
        metadata.add("version", "Vorbis " + info.getVersion());

        extractChannelInfo(metadata, info);
    }
}
