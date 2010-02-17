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
import java.util.Map.Entry;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFileFormat.Type;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AudioParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.audio("basic"),
                MediaType.audio("x-wav"),
                MediaType.audio("x-aiff"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // AudioSystem expects the stream to support the mark feature
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(stream);
            Type type = fileFormat.getType();
            if (type == Type.AIFC || type == Type.AIFF) {
                metadata.set(Metadata.CONTENT_TYPE, "audio/x-aiff");
            } else if (type == Type.AU || type == Type.SND) {
                metadata.set(Metadata.CONTENT_TYPE, "audio/basic");
            } else if (type == Type.WAVE) {
                metadata.set(Metadata.CONTENT_TYPE, "audio/x-wav");
            }

            AudioFormat audioFormat = fileFormat.getFormat();
            int channels = audioFormat.getChannels();
            if (channels != AudioSystem.NOT_SPECIFIED) {
                metadata.set("channels", String.valueOf(channels));
                // TODO: Use XMPDM.TRACKS? (see also frame rate in AudioFormat)
            }
            float rate = audioFormat.getSampleRate();
            if (rate != AudioSystem.NOT_SPECIFIED) {
                metadata.set("samplerate", String.valueOf(rate));
                metadata.set(
                        XMPDM.AUDIO_SAMPLE_RATE,
                        Integer.toString((int) rate));
            }
            int bits = audioFormat.getSampleSizeInBits();
            if (bits != AudioSystem.NOT_SPECIFIED) {
                metadata.set("bits", String.valueOf(bits));
                if (bits == 8) {
                    metadata.set(XMPDM.AUDIO_SAMPLE_TYPE, "8Int");
                } else if (bits == 16) {
                    metadata.set(XMPDM.AUDIO_SAMPLE_TYPE, "16Int");
                } else if (bits == 32) {
                    metadata.set(XMPDM.AUDIO_SAMPLE_TYPE, "32Int");
                }
            }
            metadata.set("encoding", audioFormat.getEncoding().toString());

            // Javadoc suggests that some of the following properties might
            // be available, but I had no success in finding any:

            // "duration" Long playback duration of the file in microseconds
            // "author" String name of the author of this file
            // "title" String title of this file
            // "copyright" String copyright message
            // "date" Date date of the recording or release
            // "comment" String an arbitrary text

            for (Entry<String, Object> entry : fileFormat.properties().entrySet()) {
                metadata.set(entry.getKey(), entry.getValue().toString());
            }
            for (Entry<String, Object> entry : audioFormat.properties().entrySet()) {
                metadata.set(entry.getKey(), entry.getValue().toString());
            }
        } catch (UnsupportedAudioFileException e) {
            // There is no way to know whether this exception was
            // caused by the document being corrupted or by the format
            // just being unsupported. So we do nothing.
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
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
