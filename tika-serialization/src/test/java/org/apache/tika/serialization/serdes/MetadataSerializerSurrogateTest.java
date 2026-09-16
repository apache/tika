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
package org.apache.tika.serialization.serdes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.metadata.Metadata;

/**
 * A parser can hand back a lone surrogate (an HTML numeric character reference for one, for
 * instance). The UTF-8 and Smile generators reject it, and before this a single such value
 * failed the whole document's serialization, which in the pipes worker was fatal.
 */
public class MetadataSerializerSurrogateTest {

    private static final String LONE_HIGH = "abc\uDB2Cdef";
    private static final String LONE_LOW = "\uDC00abc";
    private static final String PAIR = "a😀b";

    @Test
    public void testWellFormed() {
        assertEquals("abc�def", MetadataSerializer.wellFormed(LONE_HIGH));
        assertEquals("�abc", MetadataSerializer.wellFormed(LONE_LOW));
        assertEquals("a��b", MetadataSerializer.wellFormed("a\uDB2C\uDB2Cb"));
        assertEquals("�", MetadataSerializer.wellFormed("\uDB2C"));
        // a well-formed value is returned as is, not copied
        assertSame(PAIR, MetadataSerializer.wellFormed(PAIR));
        assertSame("plain", MetadataSerializer.wellFormed("plain"));
        assertEquals("", MetadataSerializer.wellFormed(""));
    }

    @Test
    public void testLoneSurrogatesSurviveUtf8AndSmile() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("single", LONE_HIGH);
        metadata.add("multi", LONE_LOW);
        metadata.add("multi", PAIR);
        metadata.set("pair", PAIR);

        for (ObjectMapper mapper : new ObjectMapper[] {
                TikaObjectMapperFactory.createMapper(),
                TikaObjectMapperFactory.createMapper(new SmileFactory())}) {
            byte[] bytes = mapper.writeValueAsBytes(metadata);
            Metadata back = mapper.readValue(bytes, Metadata.class);
            assertEquals("abc�def", back.get("single"));
            assertArrayEquals(new String[] {"�abc", PAIR}, back.getValues("multi"));
            assertEquals(PAIR, back.get("pair"));
        }
    }
}
