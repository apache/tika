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
package org.apache.tika.parser.ogg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;

/**
 * TIKA-4921: vorbis-java folds comment field names in the default locale, so on a
 * Turkic JVM "TITLE" is stored as "ttle" and never found again.
 */
// sets the JVM-wide default locale
@Isolated
public class VorbisCommentsLocaleTest extends TikaTest {

    @Test
    public void testTurkishLocale(@TempDir Path tmp) throws Exception {
        // same-length upper-case keys keep every block header valid
        String flac = new String(
                Files.readAllBytes(getResourceAsFile("/test-documents/testFLAC_commentAndNativePicture.flac").toPath()),
                StandardCharsets.ISO_8859_1);
        assertEquals(1, count(flac, "title=Test Title"));
        flac = flac.replace("title=Test Title", "TITLE=Test Title")
                .replace("artist=Test Artist", "ARTIST=Test Artist");
        Path upperCased = tmp.resolve("upper.flac");
        Files.write(upperCased, flac.getBytes(StandardCharsets.ISO_8859_1));

        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            List<Metadata> metadataList = getRecursiveMetadata(upperCased);
            assertEquals("Test Title", metadataList.get(0).get(TikaCoreProperties.TITLE));
            assertEquals("Test Artist", metadataList.get(0).get(XMPDM.ARTIST));
            assertEquals(3, metadataList.size());
            assertEquals("Comment cover", metadataList.get(1).get(TikaCoreProperties.TITLE));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static int count(String s, String needle) {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
