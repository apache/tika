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
import java.util.HashMap;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class MidiParser implements Parser {

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata) throws IOException, SAXException, TikaException {
        parse(stream, metadata);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    private static HashMap<Float, String> divisionTypes = new HashMap<Float, String>();

    static {
        divisionTypes.put(Sequence.PPQ, "PRQ");
        divisionTypes.put(Sequence.SMPTE_24, "SMPTE_24");
        divisionTypes.put(Sequence.SMPTE_25, "SMPTE_25");
        divisionTypes.put(Sequence.SMPTE_30, "SMPTE_30");
        divisionTypes.put(Sequence.SMPTE_30DROP, "SMPTE_30DROP");
    }

    public void parse(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        String type = metadata.get(Metadata.CONTENT_TYPE);
        if (type != null) {

            try {

                Sequence sequence = MidiSystem.getSequence(stream);

                metadata.set("tracks", Integer
                        .toString(sequence.getTracks().length));

                metadata.set("patches", Integer.toString(sequence
                        .getPatchList().length));

                metadata.set("divisionType", divisionTypes.get(sequence
                        .getDivisionType()));

            } catch (InvalidMidiDataException e) {
                // cannot parse format
            }

        }
    }
}
