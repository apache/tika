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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class OneNoteLegacyDumpStringsTest {

    private static final String TEXT = "a run that crosses several buffers";

    @Test
    public void testAsciiRunAcrossBuffersIsWrittenOnce(@TempDir Path tmp) throws Exception {
        byte[] ascii = TEXT.getBytes(StandardCharsets.US_ASCII);
        String out = dump(tmp, concat(new byte[]{1}, ascii, new byte[]{1}), 7);
        assertEquals(TEXT + "\n", out);
    }

    @Test
    public void testAsciiRunEndingTheFileKeepsItsLastByte(@TempDir Path tmp) throws Exception {
        String out = dump(tmp, TEXT.getBytes(StandardCharsets.US_ASCII), 1000);
        assertEquals(TEXT + "\n", out);
    }

    @Test
    public void testUtf16RunAcrossOddBufferBoundaryIsWrittenOnce(@TempDir Path tmp)
            throws Exception {
        // a leading pad byte puts every character pair across an odd buffer boundary
        byte[] utf16 = TEXT.getBytes(StandardCharsets.UTF_16LE);
        String out = dump(tmp, concat(new byte[]{1}, utf16, new byte[]{1, 1}), 7);
        assertEquals(TEXT + "\n", out);
    }

    @Test
    public void testSpacesCountTowardTheAlphaRatio(@TempDir Path tmp) throws Exception {
        // 4 letters, 2 spaces, 2 digits: passes only if spaces count (6/8 > 0.6)
        String spaced = "ab 12 cd";
        String out = dump(tmp, concat(new byte[]{1},
                spaced.getBytes(StandardCharsets.US_ASCII), new byte[]{1}), 1000);
        assertEquals(spaced + "\n", out);
    }

    private static String dump(Path tmp, byte[] bytes, int bufferSize) throws Exception {
        Path file = tmp.resolve("legacy");
        Files.write(file, bytes);
        StringWriter writer = new StringWriter();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new ToTextContentHandler(writer),
                new Metadata(), new ParseContext());
        xhtml.startDocument();
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            new OneNoteLegacyDumpStrings(dif, xhtml, bufferSize).dump();
        }
        xhtml.endDocument();
        return writer.toString().strip() + "\n";
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            bos.writeBytes(part);
        }
        return bos.toByteArray();
    }
}
