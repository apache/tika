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

import org.gagravarr.flac.FlacFile;
import org.gagravarr.flac.FlacInfo;
import org.gagravarr.flac.FlacOggFile;
import org.gagravarr.ogg.OggStreamIdentifier;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for FLAC audio files (both native FLAC and OGG-FLAC).
 */
@TikaComponent
public class FlacParser extends AbstractParser {
    private static final long serialVersionUID = -7546577301474546694L;

    protected static final MediaType NATIVE_FLAC =
            MediaType.parse(OggStreamIdentifier.NATIVE_FLAC.mimetype);
    protected static final MediaType OGG_FLAC =
            MediaType.parse(OggStreamIdentifier.OGG_FLAC.mimetype);

    private static List<MediaType> TYPES = Arrays.asList(NATIVE_FLAC, OGG_FLAC);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return new HashSet<>(TYPES);
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        metadata.set(XMPDM.AUDIO_COMPRESSOR, "FLAC");

        // Open the FLAC file
        FlacFile flac = FlacFile.open(tis);

        // Start
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        // Extract the common FLAC info
        extractInfo(metadata, flac.getInfo());
        if (flac instanceof FlacOggFile) {
            FlacOggFile ogg = (FlacOggFile) flac;
            metadata.add("version", "Flac " + ogg.getFirstPacket().getMajorVersion() +
                    "." + ogg.getFirstPacket().getMinorVersion());
            metadata.set(Metadata.CONTENT_TYPE, OGG_FLAC.toString());
        } else {
            metadata.set(Metadata.CONTENT_TYPE, NATIVE_FLAC.toString());
        }

        // Extract any Vorbis-style comments
        OggAudioParser.extractComments(metadata, xhtml, flac.getTags());

        // Extract duration if available from header
        FlacInfo info = flac.getInfo();
        if (info.getNumberOfSamples() > 0 && info.getSampleRate() > 0) {
            double duration = (double) info.getNumberOfSamples() / info.getSampleRate();
            OggAudioParser.extractDuration(metadata, xhtml, duration);
        }

        // Finish
        xhtml.endDocument();
        flac.close();
    }

    protected void extractInfo(Metadata metadata, FlacInfo info) throws TikaException {
        metadata.set(XMPDM.AUDIO_SAMPLE_RATE, (int) info.getSampleRate());
        OggAudioParser.extractChannelInfo(metadata, info.getNumChannels());
    }
}
