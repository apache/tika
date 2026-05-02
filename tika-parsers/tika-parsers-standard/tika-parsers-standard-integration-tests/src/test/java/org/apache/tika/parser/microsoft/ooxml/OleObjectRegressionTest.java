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
package org.apache.tika.parser.microsoft.ooxml;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.digestutils.CommonsDigesterFactory;

/**
 * Regression guard for the alpha4-era attachment loss in OOXML containers with embedded
 * oleObjectN.bin parts.
 *
 * When a {@link DigesterFactory} is on the {@link ParseContext} (a common production
 * configuration) the embedded OLE part used to be routed through
 * {@code MSEmbeddedStreamTranslator} which consumed the underlying
 * {@code TikaInputStream} without rewinding it. {@code AutoDetectParser}'s zero-byte
 * probe then read {@code -1} from the now-exhausted stream and threw
 * {@link org.apache.tika.exception.ZeroByteFileException}, which silently dropped the
 * grandchildren (images, ms-equation parts, ms-chart workbook) of every oleObject
 * wrapper.
 *
 * Fixed in {@code DigestHelper#maybeDigest} by enableRewind/rewind around the translate
 * call so the original stream is intact when control returns to AutoDetectParser.
 */
public class OleObjectRegressionTest extends TikaTest {

    private static final String TEST_FILE = "testWPSAttachment.docx";

    /**
     * Sanity baseline: without a digester configured, the embedded ms-graph oleObject is
     * detected and its inner POIFS is recursed.
     */
    @Test
    public void testOleObjectExtractedWithoutDigester() throws Exception {
        List<Metadata> list = getRecursiveMetadata(TEST_FILE);
        assertOleGraphPresent(list);
    }

    /**
     * Reproducer for the alpha4 attachment-loss regression. With a digester wired in, the
     * embedded oleObject must still resolve to {@code application/vnd.ms-graph} (or
     * similar inner-OLE2 detected type), not stay stuck as
     * {@code application/vnd.openxmlformats-officedocument.oleobject} with a
     * ZeroByteFileException.
     */
    @Test
    public void testOleObjectExtractedWithDigester() throws Exception {
        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, new CommonsDigesterFactory());

        List<Metadata> list = getRecursiveMetadata(TEST_FILE, context, true);
        assertOleGraphPresent(list);

        // Sanity check that the digester actually ran (so we know the test is exercising
        // the digest code path).
        long digested = list.stream()
                .filter(m -> m.get("X-TIKA:digest:MD5") != null)
                .count();
        assertTrue(digested > 0,
                "expected the digester to have computed at least one MD5; "
                        + "did the digester wire up correctly?");
    }

    private static void assertOleGraphPresent(List<Metadata> list) {
        List<String> contentTypes = list.stream()
                .map(m -> m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH) + " -> "
                        + m.get(Metadata.CONTENT_TYPE))
                .collect(Collectors.toList());

        // testWPSAttachment.docx has three "raw OLE2" embeds at /oleObject{4,6,8}.bin
        // wrapping respectively a Word doc, an Excel sheet, and a PowerPoint deck. They
        // must be detected as their inner-POIFS types, not left as the OOXML oleobject
        // dead-end MIME.
        for (String n : new String[]{"/oleObject4.bin", "/oleObject6.bin", "/oleObject8.bin"}) {
            Metadata m = list.stream()
                    .filter(x -> n.equals(x.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH)))
                    .findFirst()
                    .orElse(null);
            assertTrue(m != null, "expected " + n + " to be present; items=" + contentTypes);
            String ct = m.get(Metadata.CONTENT_TYPE);
            assertTrue(ct != null
                            && !ct.toLowerCase().contains("openxmlformats-officedocument.oleobject"),
                    "expected " + n + " to be detected as its inner POIFS type, not "
                            + "as the dead-end oleobject mime; ct=" + ct);
            assertNull(m.get("X-TIKA:EXCEPTION:embedded_exception"),
                    "expected no embedded_exception on " + n
                            + "; got: " + m.get("X-TIKA:EXCEPTION:embedded_exception"));
        }
    }
}
