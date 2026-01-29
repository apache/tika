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
import org.gagravarr.speex.SpeexFile;
import org.gagravarr.speex.SpeexInfo;
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
 * Parser for OGG Speex audio files.
 */
@TikaComponent
public class SpeexParser extends OggAudioParser {
    private static final long serialVersionUID = 5904981674814527529L;

    protected static final MediaType SPEEX_AUDIO =
            MediaType.parse(OggStreamIdentifier.SPEEX_AUDIO.mimetype);
    protected static final MediaType SPEEX_AUDIO_ALT =
            MediaType.parse(OggStreamIdentifier.SPEEX_AUDIO_ALT.mimetype);

    private static List<MediaType> TYPES = Arrays.asList(SPEEX_AUDIO, SPEEX_AUDIO_ALT);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return new HashSet<>(TYPES);
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        metadata.set(Metadata.CONTENT_TYPE, SPEEX_AUDIO.toString());
        metadata.set(XMPDM.AUDIO_COMPRESSOR, "Speex");

        // Open and process the files
        OggFile ogg = new OggFile(tis);
        SpeexFile speex = new SpeexFile(ogg);

        // Start
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        // Extract the common Speex info
        extractInfo(metadata, speex.getInfo());

        // Extract any Vorbis comments
        extractComments(metadata, xhtml, speex.getTags());

        // Extract the audio length
        extractDuration(metadata, xhtml, speex, speex);

        // Finish
        xhtml.endDocument();
        speex.close();
    }

    protected void extractInfo(Metadata metadata, SpeexInfo info) throws TikaException {
        metadata.set(XMPDM.AUDIO_SAMPLE_RATE, (int) info.getRate());
        metadata.add("version", "Speex " + info.getVersionId() + " - " + info.getVersionString());

        extractChannelInfo(metadata, info);
    }
}
