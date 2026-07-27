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
package org.apache.tika.metadata.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Gate for the curated {@code metadata-string-keys.json}: each entry's key must equal the live value
 * of the {@code source} constant it names (e.g. {@code HttpHeaders.CONTENT_TYPE}). If a constant is
 * renamed, retyped, or its value changes, this fails — the hand-maintained list can't silently rot.
 */
public class MetadataStringKeysTest {

    private static final String RESOURCE = "/org/apache/tika/metadata/metadata-string-keys.json";
    private static final String PKG = "org.apache.tika.metadata.";
    private static final Pattern ENTRY =
            Pattern.compile("\\{\"key\":\"(.*?)\",\"source\":\"(.*?)\"}");

    @Test
    public void everyStringKeyMatchesItsSourceConstant() throws Exception {
        String json;
        try (InputStream in = MetadataStringKeysTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "committed " + RESOURCE + " is missing");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = ENTRY.matcher(json);
        int count = 0;
        while (m.find()) {
            String key = m.group(1);
            String source = m.group(2);           // e.g. HttpHeaders.CONTENT_TYPE
            int dot = source.lastIndexOf('.');
            Class<?> cls = Class.forName(PKG + source.substring(0, dot));
            Field field = cls.getField(source.substring(dot + 1));
            assertEquals(String.class, field.getType(),
                    source + " must be a String constant (a Property belongs in metadata-keys.json)");
            assertEquals(key, field.get(null),
                    source + " value drifted from metadata-string-keys.json");
            count++;
        }
        assertEquals(15, count, "unexpected metadata-string-keys.json entry count");
    }
}
