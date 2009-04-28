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
import java.util.Map.Entry;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AudioParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // AudioSystem expects the stream to support the mark feature
        InputStream buffered = new BufferedInputStream(stream);
        try {
            AudioFormat format =
                AudioSystem.getAudioFileFormat(buffered).getFormat();

            float rate = format.getSampleRate();
            if (rate != AudioSystem.NOT_SPECIFIED) {
                metadata.set("samplerate", String.valueOf(rate));
            }

            int channels = format.getChannels();
            if (channels != AudioSystem.NOT_SPECIFIED) {
                metadata.set("channels", String.valueOf(channels));
            }

            int bits = format.getSampleSizeInBits();
            if (bits != AudioSystem.NOT_SPECIFIED) {
                metadata.set("bits", String.valueOf(bits));
            }

            metadata.set("encoding", format.getEncoding().toString());

            // Javadoc suggests that some of the following properties might
            // be available, but I had no success in finding any:

            // "duration" Long playback duration of the file in microseconds
            // "author" String name of the author of this file
            // "title" String title of this file
            // "copyright" String copyright message
            // "date" Date date of the recording or release
            // "comment" String an arbitrary text

            for (Entry<String, Object> entry : format.properties().entrySet()) {
                metadata.set(entry.getKey(), entry.getValue().toString());
            }
        } catch (UnsupportedAudioFileException e) {
            // There is no way to know whether this exception was
            // caused by the document being corrupted or by the format
            // just being unsupported. So we do nothing.
        }

        xhtml.endDocument();
    }

}
