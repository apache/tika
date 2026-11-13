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
import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.quality.TextQualityDetector;
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
    public void testHeuristicKeepsShortCaption(@TempDir Path tmp) throws Exception {
        // 5 of 8 non-space chars are letters; spaces don't count toward the ratio
        assertKept(tmp, "Table 3-5", null);
    }

    @Test
    public void testHeuristicDropsLetterHeavyBinary(@TempDir Path tmp) throws Exception {
        // from image data in a real section: letter-heavy, but no lower-case word
        assertDropped(tmp, " 2JLXVZZL@2(", null);
    }

    @Test
    public void testQualityDetectorKeepsTextTheHeuristicLoses(@TempDir Path tmp)
            throws Exception {
        TextQualityDetector detector = JunkDetector.loadFromClasspath();
        assertKept(tmp, "FIX THIS MATH", detector);
        assertKept(tmp, "Bothell, WA 98052", detector);
        assertDropped(tmp, "FIX THIS MATH", null);
    }

    @Test
    public void testQualityDetectorDropsBinary(@TempDir Path tmp) throws Exception {
        TextQualityDetector detector = JunkDetector.loadFromClasspath();
        assertDropped(tmp, " 2JLXVZZL@2(", detector);
        assertDropped(tmp, " ,6BDHFB@BDDD4(", detector);
    }

    @Test
    public void testQualityDetectorIsFoundOnTheClassPath(@TempDir Path tmp) throws Exception {
        // the public constructor uses it; all-caps text survives only with a detector
        Path file = tmp.resolve("caps");
        Files.write(file, concat(new byte[]{1},
                "FIX THIS MATH".getBytes(StandardCharsets.US_ASCII), new byte[]{1}));
        StringWriter writer = new StringWriter();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new ToTextContentHandler(writer),
                new Metadata(), new ParseContext());
        xhtml.startDocument();
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            new OneNoteLegacyDumpStrings(dif, xhtml).dump();
        }
        xhtml.endDocument();
        assertEquals("FIX THIS MATH", writer.toString().strip());
    }

    private static void assertKept(Path tmp, String text, TextQualityDetector detector)
            throws Exception {
        assertEquals(text + "\n", dump(tmp, padded(text), 1000, detector));
    }

    private static void assertDropped(Path tmp, String text, TextQualityDetector detector)
            throws Exception {
        assertEquals("\n", dump(tmp, padded(text), 1000, detector));
    }

    private static byte[] padded(String text) {
        return concat(new byte[]{1}, text.getBytes(StandardCharsets.US_ASCII), new byte[]{1});
    }

    private static String dump(Path tmp, byte[] bytes, int bufferSize) throws Exception {
        return dump(tmp, bytes, bufferSize, null);
    }

    private static String dump(Path tmp, byte[] bytes, int bufferSize,
                               TextQualityDetector detector) throws Exception {
        Path file = tmp.resolve("legacy");
        Files.write(file, bytes);
        StringWriter writer = new StringWriter();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new ToTextContentHandler(writer),
                new Metadata(), new ParseContext());
        xhtml.startDocument();
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(file.toFile())) {
            new OneNoteLegacyDumpStrings(dif, xhtml, bufferSize, detector).dump();
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
