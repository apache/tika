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
package org.apache.tika.metadata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.writelimiter.StandardMetadataLimiterFactory;
import org.apache.tika.utils.StringUtils;

/**
 * A lone UTF-16 surrogate never gets stored: every write replaces it with U+FFFD, so no
 * reader (a Smile encoder, for one) meets one. tk:content is exempt, since it came through
 * SafeContentHandler, which already did this.
 */
public class MetadataWellFormedTest {

    private static final String LONE_HIGH = "abc\uDB2Cdef";
    private static final String LONE_LOW = "\uDC00abc";
    private static final String PAIR = "a😀b";

    @Test
    public void testWellFormed() {
        assertEquals("abc�def", StringUtils.wellFormed(LONE_HIGH));
        assertEquals("�abc", StringUtils.wellFormed(LONE_LOW));
        assertEquals("a��b", StringUtils.wellFormed("a\uDB2C\uDB2Cb"));
        assertEquals("�", StringUtils.wellFormed("\uDB2C"));
        assertEquals("", StringUtils.wellFormed(""));
        // a well-formed value comes back as is, not copied; so does an array of them
        assertSame(PAIR, StringUtils.wellFormed(PAIR));
        String[] clean = {"plain", PAIR};
        assertSame(clean, StringUtils.wellFormed(clean));
        String[] dirty = {"plain", LONE_HIGH};
        String[] fixed = StringUtils.wellFormed(dirty);
        assertArrayEquals(new String[]{"plain", "abc�def"}, fixed);
        assertEquals(LONE_HIGH, dirty[1], "the input array is not rewritten");
    }

    @Test
    public void testEveryWriteStoresWellFormed() {
        for (Metadata metadata : new Metadata[]{new Metadata(),
                new Metadata(new StandardMetadataLimiterFactory().newInstance())}) {
            metadata.set("single", LONE_HIGH);
            metadata.add("multi", LONE_LOW);
            metadata.add("multi", PAIR);
            metadata.set(TikaCoreProperties.TITLE, LONE_HIGH);
            metadata.setTrusted("tk:trusted", LONE_LOW);
            metadata.addTrusted("tk:trusted", LONE_HIGH);
            metadata.set(TikaCoreProperties.SUBJECT, new String[]{LONE_LOW, PAIR});

            assertEquals("abc�def", metadata.get("single"));
            assertArrayEquals(new String[]{"�abc", PAIR}, metadata.getValues("multi"));
            assertEquals("abc�def", metadata.get(TikaCoreProperties.TITLE));
            assertArrayEquals(new String[]{"�abc", "abc�def"},
                    metadata.getValues("tk:trusted"));
            assertArrayEquals(new String[]{"�abc", PAIR},
                    metadata.getValues(TikaCoreProperties.SUBJECT));
        }
    }

    /** tk:content is SafeContentHandler's job and the one value big enough for the scan to matter. */
    @Test
    public void testContentIsExempt() {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.TIKA_CONTENT, LONE_HIGH);
        assertEquals(LONE_HIGH, metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }
}
