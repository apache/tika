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
package org.apache.tika.parser.apple;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.RewindRecordingExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * The data fork of an AppleSingle file is a region of the file itself. The
 * stream handed to the embedded-document extractor must re-read that region on
 * rewind, from memory when the parent is in memory and from the file when it
 * has one, rather than copy it to a temp file. The parser used to force that
 * copy on every parse, digester or not.
 */
public class AppleSingleFileParserNoTempFileTest {

    private static final int PAYLOAD_LENGTH = 2 * 1024 * 1024;
    private static final int HEADER_LENGTH = 26 + 12;

    @TempDir
    Path tempDir;

    @Test
    public void testDataForkFromFileIsNotSpooled() throws Exception {
        Path file = tempDir.resolve("fork.as");
        Files.write(file, appleSingle());
        try (TikaInputStream tis = TikaInputStream.get(file, new Metadata())) {
            RewindRecordingExtractor extractor = parse(tis);
            extractor.assertSawLength(PAYLOAD_LENGTH);
            extractor.assertNothingSpooled();
        }
    }

    @Test
    public void testDataForkFromMemoryIsNotSpooled() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(appleSingle())) {
            RewindRecordingExtractor extractor = parse(tis);
            extractor.assertSawLength(PAYLOAD_LENGTH);
            extractor.assertNothingSpooled();
        }
    }

    private static RewindRecordingExtractor parse(TikaInputStream tis) throws Exception {
        RewindRecordingExtractor extractor = new RewindRecordingExtractor();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, extractor);
        new AppleSingleFileParser().parse(tis, new DefaultHandler(), new Metadata(), context);
        return extractor;
    }

    /** Header, one entry (the data fork, id 1) and the fork itself. */
    private static byte[] appleSingle() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int32(out, 0x00051600);
        int32(out, 0x00020000);
        out.writeBytes(new byte[16]);
        out.write(0);
        out.write(1);
        int32(out, 1);
        int32(out, HEADER_LENGTH);
        int32(out, PAYLOAD_LENGTH);
        byte[] payload = new byte[PAYLOAD_LENGTH];
        new Random(4878).nextBytes(payload);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static void int32(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
