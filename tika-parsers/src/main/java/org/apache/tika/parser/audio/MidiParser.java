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
package org.apache.tika.parser.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Patch;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class MidiParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.application("x-midi"),
                MediaType.audio("midi"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "audio/midi");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // MidiSystem expects the stream to support the mark feature
        InputStream buffered = new BufferedInputStream(stream);
        try {
            Sequence sequence = MidiSystem.getSequence(buffered);

            Track[] tracks = sequence.getTracks();
            metadata.set("tracks", String.valueOf(tracks.length));
            // TODO: Use XMPDM.TRACKS?

            Patch[] patches = sequence.getPatchList();
            metadata.set("patches", String.valueOf(patches.length));

            float type = sequence.getDivisionType();
            if (type == Sequence.PPQ) {
                metadata.set("divisionType", "PPQ");
            } else if (type == Sequence.SMPTE_24) {
                metadata.set("divisionType", "SMPTE_24");
            } else if (type == Sequence.SMPTE_25) {
                metadata.set("divisionType", "SMPTE_25");
            } else if (type == Sequence.SMPTE_30) {
                metadata.set("divisionType", "SMPTE_30");
            } else if (type == Sequence.SMPTE_30DROP) {
                metadata.set("divisionType", "SMPTE_30DROP");
            } else if (type == Sequence.SMPTE_24) {
                metadata.set("divisionType", String.valueOf(type));
            }

            for (Track track : tracks) {
                xhtml.startElement("p");
                for (int i = 0; i < track.size(); i++) {
                    MidiMessage message = track.get(i).getMessage();
                    if (message instanceof MetaMessage) {
                        MetaMessage meta = (MetaMessage) message;
                        // Types 1-15 are reserved for text events
                        if (meta.getType() >= 1 && meta.getType() <= 15) {
                            // FIXME: What's the encoding?
                            xhtml.characters(
                                    new String(meta.getData(), "ISO-8859-1"));
                        }
                    }
                }
                xhtml.endElement("p");
            }
        } catch (InvalidMidiDataException ignore) {
            // There is no way to know whether this exception was
            // caused by the document being corrupted or by the format
            // just being unsupported. So we do nothing.
        }

        xhtml.endDocument();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
