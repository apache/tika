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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;

import javax.sound.sampled.AudioFileFormat;
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

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata) throws IOException, SAXException, TikaException {
        parse(stream, metadata);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    public void parse(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        String type = metadata.get(Metadata.CONTENT_TYPE);
        if (type != null) {
            try {

                AudioFileFormat fileFormat = AudioSystem
                        .getAudioFileFormat(stream);

                AudioFormat format = fileFormat.getFormat();

                metadata.set("samplerate", Integer.toString((int) format
                        .getSampleRate()));
                metadata
                        .set("channels", Integer.toString(format.getChannels()));
                metadata.set("bits", Integer.toString(format
                        .getSampleSizeInBits()));
                metadata.set("encoding", format.getEncoding().toString());

                // Javadoc suggests that some of the following properties might
                // be available, but I had no success in finding any:

                // "duration" Long playback duration of the file in microseconds
                // "author" String name of the author of this file
                // "title" String title of this file
                // "copyright" String copyright message
                // "date" Date date of the recording or release
                // "comment" String an arbitrary text

                for (Entry<String, Object> entry : format.properties()
                        .entrySet()) {
                    metadata.set(entry.getKey(), entry.getValue().toString());
                }

            } catch (UnsupportedAudioFileException e) {
                // cannot parse, unknown format
            }

        }
    }
}
