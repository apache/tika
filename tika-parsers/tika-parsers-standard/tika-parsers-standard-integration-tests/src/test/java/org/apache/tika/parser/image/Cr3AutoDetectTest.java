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
package org.apache.tika.parser.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/** With every standard parser, a CR3 goes to Cr3Parser, not to the MP4 parser of its family. */
public class Cr3AutoDetectTest extends TikaTest {

    @Test
    public void testAutoDetectParserPicksCr3Parser() throws Exception {
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(cr3())) {
            list = getRecursiveMetadata(tis, true);
        }
        Metadata cr3 = list.get(0);
        assertEquals("image/x-canon-cr3", cr3.get(HttpHeaders.CONTENT_TYPE));
        assertContains(Cr3Parser.class.getName(),
                Arrays.asList(cr3.getValues(TikaCoreProperties.TIKA_PARSED_BY)));
        assertEquals(2, list.size());
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /** ftyp crx, an empty moov and the Canon preview box with a PRVW record. */
    private static byte[] cr3() throws IOException {
        byte[] jpeg = jpeg();
        ByteArrayOutputStream prvw = new ByteArrayOutputStream();
        prvw.writeBytes(new byte[4]);
        prvw.writeBytes(ByteBuffer.allocate(8).putShort((short) 1).putShort((short) 48)
                .putShort((short) 32).putShort((short) 1).array());
        prvw.writeBytes(ByteBuffer.allocate(4).putInt(jpeg.length).array());
        prvw.writeBytes(jpeg);
        ByteArrayOutputStream preview = new ByteArrayOutputStream();
        preview.writeBytes(HexFormat.of().parseHex("eaf42b5e1c984b88b9fbb7dc406e4d16"));
        preview.writeBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 1});
        preview.writeBytes(box("PRVW", prvw.toByteArray()));
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(box("ftyp", "crx \0\0\0\1crx isom".getBytes(StandardCharsets.US_ASCII)));
        file.writeBytes(box("moov", box("mvhd", new byte[100])));
        file.writeBytes(box("uuid", preview.toByteArray()));
        file.writeBytes(box("mdat", new byte[16]));
        return file.toByteArray();
    }

    private static byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length).putInt(8 + payload.length)
                .put(type.getBytes(StandardCharsets.US_ASCII)).put(payload).array();
    }

    private static byte[] jpeg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }
}
