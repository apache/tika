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
package org.apache.tika.digest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.parser.ParseContext;

public class InputStreamDigesterTest {

    @Test
    public void testReservedDigestKeySurvivesGuard() throws Exception {
        String key = "tk:digest:SHA256";
        InputStreamDigester digester = new InputStreamDigester("SHA256", key,
                bytes -> Base64.getEncoder().encodeToString(bytes));
        Metadata metadata = new Metadata();
        try (TikaInputStream tis =
                     TikaInputStream.get("hello world".getBytes(StandardCharsets.UTF_8))) {
            digester.digest(tis, metadata, new ParseContext());
        }
        assertNotNull(metadata.get(key), "trusted digest write must survive the reserved-key guard");
        assertNotNull(Property.get(key), "digest key must be registered as a Property");
    }

    /**
     * Digesting a re-openable source used to read it once for the hash and leave the parse to
     * read it again. For an embedded entry that second read means inflating it again, so the
     * digest now asks the source to hold the content.
     */
    @Test
    public void testDigestOfAReopenableSourceDoesNotForceASecondOpen() throws Exception {
        byte[] content = new byte[64 * 1024];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31 + 7);
        }
        AtomicInteger opens = new AtomicInteger();
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(content.length));
        InputStreamDigester digester = new InputStreamDigester("SHA256", "tk:digest:SHA256",
                bytes -> Base64.getEncoder().encodeToString(bytes));
        try (TemporaryResources tmp = new TemporaryResources()) {
            java.util.function.Supplier<InputStream> raw = () -> {
                opens.incrementAndGet();
                return new ByteArrayInputStream(content);
            };
            try (TikaInputStream tis = TikaInputStream.get(raw::get, tmp, metadata)) {
                digester.digest(tis, metadata, new ParseContext());
                assertNotNull(metadata.get("tk:digest:SHA256"));
                // the parse that follows the digest
                assertArrayEquals(content, IOUtils.toByteArray(tis));
            }
        }
        assertEquals(1, opens.get(), "digest plus parse must open the source once");
    }
}
