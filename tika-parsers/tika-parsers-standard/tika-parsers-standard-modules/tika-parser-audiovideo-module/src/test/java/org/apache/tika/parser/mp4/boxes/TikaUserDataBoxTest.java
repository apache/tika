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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaUserDataBoxTest {

    @Test
    public void testIlstSearchMalformedSubBoxLength() {
        //a sub-box length below 8 made skip(len - 8) negative: an IllegalArgumentException
        //that escaped the MP4 reader. TIKA-4812
        ByteBuffer buf = ByteBuffer.allocate(40);
        buf.putInt(40);
        buf.put("meta".getBytes(StandardCharsets.ISO_8859_1));
        buf.putInt(0);
        buf.putInt(20);                                 //lengthToStartOfList = 16: no skip
        buf.put("hdlr".getBytes(StandardCharsets.ISO_8859_1));
        buf.putInt(0);
        buf.putInt(0);
        buf.put("mdir".getBytes(StandardCharsets.ISO_8859_1));
        buf.putInt(4);                                  //malformed sub-box length
        buf.put("free".getBytes(StandardCharsets.ISO_8859_1)); //not ilst: enters the search

        Metadata metadata = new Metadata();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new DefaultHandler(), metadata);
        assertDoesNotThrow(() -> new TikaUserDataBox("udta", buf.array(), metadata, xhtml));
    }
}
