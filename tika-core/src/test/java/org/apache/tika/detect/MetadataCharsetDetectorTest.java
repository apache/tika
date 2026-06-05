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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class MetadataCharsetDetectorTest {

    private final MetadataCharsetDetector detector = new MetadataCharsetDetector();

    private Charset detect(Metadata metadata) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            List<EncodingResult> results = detector.detect(tis, metadata, new ParseContext());
            if (results.isEmpty()) {
                return null;
            }
            assertEquals(EncodingResult.ResultType.DECLARATIVE, results.get(0).getResultType());
            return results.get(0).getCharset();
        }
    }

    @Test
    public void testContentTypeHint() throws Exception {
        // TIKA-4752: the charset claimed via CONTENT_TYPE_HINT (e.g. a zip entry's
        // UTF-8/EFS flag, recorded as text/plain; charset=UTF-8) is consumed.
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.CONTENT_TYPE_HINT, "text/plain; charset=UTF-8");
        assertEquals(StandardCharsets.UTF_8, detect(m));
    }

    @Test
    public void testContentType() throws Exception {
        Metadata m = new Metadata();
        // ISO-8859-1 normalizes to its windows-1252 superset (WHATWG), existing behavior.
        m.set(Metadata.CONTENT_TYPE, "text/html; charset=ISO-8859-1");
        assertEquals(Charset.forName("windows-1252"), detect(m));
    }

    @Test
    public void testContentEncoding() throws Exception {
        Metadata m = new Metadata();
        m.set(Metadata.CONTENT_ENCODING, "Shift_JIS");
        assertEquals(Charset.forName("Shift_JIS"), detect(m));
    }

    @Test
    public void testContentTypeWinsOverHint() throws Exception {
        Metadata m = new Metadata();
        m.set(Metadata.CONTENT_TYPE, "text/plain; charset=UTF-16");
        m.set(TikaCoreProperties.CONTENT_TYPE_HINT, "text/plain; charset=UTF-8");
        assertEquals(StandardCharsets.UTF_16, detect(m));
    }

    @Test
    public void testHintWinsOverContentEncoding() throws Exception {
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.CONTENT_TYPE_HINT, "text/plain; charset=UTF-8");
        m.set(Metadata.CONTENT_ENCODING, "Shift_JIS");
        assertEquals(StandardCharsets.UTF_8, detect(m));
    }

    @Test
    public void testNoDeclarationIsEmpty() throws Exception {
        assertEquals(null, detect(new Metadata()));
        // A content-type with no charset parameter is not a declaration.
        Metadata m = new Metadata();
        m.set(Metadata.CONTENT_TYPE, "text/plain");
        assertEquals(null, detect(m));
        // An unparseable charset label is ignored, not thrown.
        Metadata bad = new Metadata();
        bad.set(Metadata.CONTENT_ENCODING, "not-a-charset");
        assertTrue(detect(bad) == null);
    }
}
