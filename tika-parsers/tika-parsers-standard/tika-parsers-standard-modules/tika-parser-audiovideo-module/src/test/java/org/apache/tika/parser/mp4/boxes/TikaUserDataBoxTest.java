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
package org.apache.tika.parser.mp4.boxes;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaUserDataBoxTest {

    /**
     * A meta/mdir udta whose ilst-search hits a sub-box declaring a length below
     * the 8-byte header used to reach the next box: {@code reader.skip(len - 8)}
     * would be a negative skip, which threw {@link IllegalArgumentException} (not
     * IOException, so it escaped MP4Reader's catch). It must now raise a caught
     * {@link IOException} instead, so the udta walk aborts and the problem is
     * recorded rather than either crashing or silently mis-reading. See TIKA-4812.
     */
    @Test
    public void testIlstSearchMalformedSubBoxLength() {
        ByteBuffer buf = ByteBuffer.allocate(40); //big-endian by default
        buf.putInt(40);                                 //meta box size (>4)
        buf.put("meta".getBytes(StandardCharsets.ISO_8859_1));
        buf.putInt(0);                                  //version and flags
        buf.putInt(20);                                 //-> lengthToStartOfList = 16, so no skip
        buf.put("hdlr".getBytes(StandardCharsets.ISO_8859_1));
        buf.putInt(0);
        buf.putInt(0);
        buf.put("mdir".getBytes(StandardCharsets.ISO_8859_1)); //handler subtype -> MDIR path
        buf.putInt(4);                                  //malformed sub-box length (< 8)
        buf.put("free".getBytes(StandardCharsets.ISO_8859_1)); //!= ilst -> enters the search loop

        Metadata metadata = new Metadata();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new DefaultHandler(), metadata);
        assertThrows(IOException.class, () ->
                new TikaUserDataBox("udta", buf.array(), metadata, xhtml, new ParseContext()));
    }
}
