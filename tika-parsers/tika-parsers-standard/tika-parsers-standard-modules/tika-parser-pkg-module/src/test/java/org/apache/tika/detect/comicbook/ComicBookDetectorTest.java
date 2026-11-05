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
package org.apache.tika.detect.comicbook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class ComicBookDetectorTest {

    private static final byte[] RAR4 = {'R', 'a', 'r', '!', 0x1a, 0x07, 0x00};
    private static final byte[] RAR5 = {'R', 'a', 'r', '!', 0x1a, 0x07, 0x01, 0x00};
    private static final byte[] SEVEN_Z = {'7', 'z', (byte) 0xbc, (byte) 0xaf, 0x27, 0x1c};

    @Test
    public void testNameOrDeclaredType() throws Exception {
        assertEquals(ComicBookDetector.CBR, detect(RAR4, "comic.cbr", null));
        assertEquals(ComicBookDetector.CBR, detect(RAR5, "COMIC.CBR", null));
        assertEquals(ComicBookDetector.CBR, detect(RAR5, null, "application/vnd.comicbook-rar"));
        assertEquals(ComicBookDetector.CBR, detect(RAR5, null, "application/x-cbr"));
        assertEquals(ComicBookDetector.CB7, detect(SEVEN_Z, "comic.cb7", null));
        assertEquals(ComicBookDetector.CBT, detect(tar("ustar  \0"), "comic.cbt", null));
        assertEquals(ComicBookDetector.CBT, detect(tar("ustar\u000000"), null, "application/x-cbt"));
    }

    /** Without a comic name or type, or with content of another container: nothing. */
    @Test
    public void testNothingElse() throws Exception {
        assertEquals(MediaType.OCTET_STREAM, detect(RAR5, "archive.rar", null));
        assertEquals(MediaType.OCTET_STREAM, detect(RAR5, null, "application/x-rar-compressed"));
        assertEquals(MediaType.OCTET_STREAM, detect(SEVEN_Z, "comic.cbr", null));
        assertEquals(MediaType.OCTET_STREAM, detect(new byte[]{'P', 'K', 3, 4}, "comic.cbt", null));
        assertEquals(MediaType.OCTET_STREAM, detect(new byte[0], "comic.cb7", null));
    }

    private static byte[] tar(String magic) {
        byte[] header = new byte[512];
        byte[] bytes = magic.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(bytes, 0, header, 257, bytes.length);
        return header;
    }

    private static MediaType detect(byte[] content, String name, String type) throws IOException {
        Metadata metadata = new Metadata();
        if (name != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        }
        if (type != null) {
            metadata.set(HttpHeaders.CONTENT_TYPE, type);
        }
        try (TikaInputStream tis = TikaInputStream.get(content)) {
            return new ComicBookDetector().detect(tis, metadata, new ParseContext());
        }
    }
}
