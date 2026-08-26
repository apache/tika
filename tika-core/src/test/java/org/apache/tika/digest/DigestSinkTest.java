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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * The sink must produce exactly what the pull-style digest produces for the same bytes,
 * through every Digester shape: streaming, composite, and the buffering default.
 */
public class DigestSinkTest {

    private static final String MD5_KEY = "tk:digest:MD5";
    private static final String SHA_KEY = "tk:digest:SHA-256";
    private static final Encoder HEX = bytes -> HexFormat.of().formatHex(bytes);

    private static byte[] data(int len) {
        byte[] d = new byte[len];
        for (int i = 0; i < len; i++) {
            d[i] = (byte) (i * 131 + 17);
        }
        return d;
    }

    private static Metadata viaStream(Digester digester, byte[] data) throws IOException {
        Metadata m = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            digester.digest(tis, m, new ParseContext());
        }
        return m;
    }

    /** Writes in awkward chunk sizes, including single bytes, to exercise both overloads. */
    private static Metadata viaSink(Digester digester, byte[] data) throws IOException {
        Metadata m = new Metadata();
        try (OutputStream sink = digester.digestSink(m, new ParseContext())) {
            int pos = 0;
            int[] chunks = {1, 7, 1000, 1, 65536};
            int c = 0;
            while (pos < data.length) {
                int len = Math.min(chunks[c++ % chunks.length], data.length - pos);
                if (len == 1) {
                    sink.write(data[pos]);
                } else {
                    sink.write(data, pos, len);
                }
                pos += len;
            }
        }
        return m;
    }

    @Test
    public void testStreamingSinkMatchesPullDigest() throws Exception {
        Digester d = new InputStreamDigester("MD5", MD5_KEY, HEX);
        byte[] data = data(200_000);
        Metadata expected = viaStream(d, data);
        Metadata actual = viaSink(d, data);
        assertNotNull(expected.get(MD5_KEY));
        assertEquals(expected.get(MD5_KEY), actual.get(MD5_KEY));
        assertEquals(Integer.toString(data.length), actual.get(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void testCompositeFansOut() throws Exception {
        Digester d = new CompositeDigester(
                new InputStreamDigester("MD5", MD5_KEY, HEX),
                new InputStreamDigester("SHA-256", SHA_KEY, HEX));
        byte[] data = data(50_000);
        Metadata expected = viaStream(d, data);
        Metadata actual = viaSink(d, data);
        assertEquals(expected.get(MD5_KEY), actual.get(MD5_KEY));
        assertEquals(expected.get(SHA_KEY), actual.get(SHA_KEY));
    }

    /** A third-party Digester that only implements digest() gets the buffering default. */
    @Test
    public void testDefaultBuffersForPullOnlyDigester() throws Exception {
        InputStreamDigester inner = new InputStreamDigester("SHA-256", SHA_KEY, HEX);
        Digester pullOnly = (tis, m, ctx) -> inner.digest(tis, m, ctx);
        // past the buffering threshold so the temp-file branch runs too
        byte[] data = data(BufferingDigestSink.MEMORY_THRESHOLD + 12_345);
        Metadata expected = viaStream(inner, data);
        Metadata actual = viaSink(pullOnly, data);
        assertEquals(expected.get(SHA_KEY), actual.get(SHA_KEY));
        assertEquals(Integer.toString(data.length), actual.get(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void testDefaultBuffersSmallInMemory() throws Exception {
        InputStreamDigester inner = new InputStreamDigester("MD5", MD5_KEY, HEX);
        Digester pullOnly = (tis, m, ctx) -> inner.digest(tis, m, ctx);
        byte[] data = data(1234);
        assertEquals(viaStream(inner, data).get(MD5_KEY), viaSink(pullOnly, data).get(MD5_KEY));
    }

    @Test
    public void testValuesSetOnlyOnClose() throws Exception {
        Digester d = new InputStreamDigester("MD5", MD5_KEY, HEX);
        Metadata m = new Metadata();
        OutputStream sink = d.digestSink(m, new ParseContext());
        sink.write(data(100), 0, 100);
        assertNull(m.get(MD5_KEY), "digest must not be visible before close");
        sink.close();
        assertNotNull(m.get(MD5_KEY));
        sink.close();   // idempotent
    }
}
