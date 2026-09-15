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
package org.apache.tika.parser.mbox;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.RewindRecordingExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * An mbox message is buffered in memory before it is handed over. Rewinding the
 * stream the embedded-document extractor gets -- which digesting does for every
 * embedded document -- must re-read that buffer, not copy it and spill the copy
 * to a temp file. The message is over the 1 MB a cache keeps in memory, so the
 * difference is observable.
 */
public class MboxParserNoTempFileTest {

    private static final int BODY_LENGTH = 2 * 1024 * 1024;

    @Test
    public void testMessageIsNotSpooled() throws Exception {
        StringBuilder mbox = new StringBuilder(BODY_LENGTH + 200);
        mbox.append("From sender@example.com Thu Sep  4 10:00:00 2026\n")
                .append("From: sender@example.com\n")
                .append("Subject: big\n\n");
        String line = "x".repeat(99) + "\n";
        while (mbox.length() < BODY_LENGTH) {
            mbox.append(line);
        }
        RewindRecordingExtractor extractor = new RewindRecordingExtractor();
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, extractor);
        try (TikaInputStream tis =
                TikaInputStream.get(mbox.toString().getBytes(StandardCharsets.US_ASCII))) {
            new MboxParser().parse(tis, new DefaultHandler(), new Metadata(), context);
        }
        //the parser keeps everything but the divider line, so a little under the input
        extractor.assertSawLengthAtLeast(BODY_LENGTH - 100);
        extractor.assertNothingSpooled();
    }
}
