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
package org.apache.tika.extractor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xml.sax.ContentHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Test double for parsers that hand embedded documents over. Rewinds each
 * embedded stream the way a digester does, then records whether that left the
 * stream backed by a temp file and how many bytes it still yields.
 * <p>
 * Assert on what this extractor was handed, not on a watched temp directory:
 * a parser owns each child's {@code TemporaryResources}, so a directory watch
 * passes whatever the parser did.
 */
public class RewindRecordingExtractor implements EmbeddedDocumentExtractor {

    private final List<Boolean> spooled = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();

    @Override
    public boolean shouldParseEmbedded(Metadata metadata, ParseContext context) {
        return true;
    }

    @Override
    public void parseEmbedded(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                              ParseContext context, boolean outputHtml) throws IOException {
        stream.enableRewind();
        stream.readAllBytes();
        stream.rewind();
        spooled.add(stream.hasFile());
        lengths.add(stream.readAllBytes().length);
    }

    /** Byte counts of the embedded documents seen, in order. */
    public List<Integer> lengths() {
        return Collections.unmodifiableList(lengths);
    }

    public void assertSawLength(int length) {
        assertTrue(lengths.contains(length),
                "an embedded document of " + length + " bytes reached the extractor; saw "
                        + lengths);
    }

    public void assertSawLengthAtLeast(int length) {
        assertTrue(lengths.stream().anyMatch(l -> l >= length),
                "an embedded document of at least " + length + " bytes reached the extractor; saw "
                        + lengths);
    }

    public void assertNothingSpooled() {
        for (int i = 0; i < spooled.size(); i++) {
            assertFalse(spooled.get(i), "embedded stream " + i + " (" + lengths.get(i)
                    + " bytes) was spooled to disk to rewind instead of re-opened");
        }
    }
}
