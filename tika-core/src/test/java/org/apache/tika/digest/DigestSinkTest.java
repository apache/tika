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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * The sink must produce exactly what the pull-style digest produces for the same bytes,
 * through every Digester shape, and must honour the DigestSink contract on every exit.
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

    private static Digester pullOnly(InputStreamDigester inner) {
        return (tis, m, ctx) -> inner.digest(tis, m, ctx);
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
        try (DigestSink sink = digester.digestSink(m, new ParseContext())) {
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
            sink.commit();
        }
        return m;
    }

    private static long tempFiles() throws IOException {
        try (Stream<Path> s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return s.filter(p -> p.getFileName().toString().startsWith("apache-tika-")).count();
        }
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

    @Test
    public void testMixedCompositeStreamingAndPullOnly() throws Exception {
        InputStreamDigester sha = new InputStreamDigester("SHA-256", SHA_KEY, HEX);
        Digester d = new CompositeDigester(new InputStreamDigester("MD5", MD5_KEY, HEX), pullOnly(sha));
        byte[] data = data(30_000);
        Metadata expected = viaStream(d, data);
        Metadata actual = viaSink(d, data);
        assertEquals(expected.get(MD5_KEY), actual.get(MD5_KEY));
        assertEquals(expected.get(SHA_KEY), actual.get(SHA_KEY));
    }

    @Test
    public void testDefaultBuffersLargeContentThroughATempFile() throws Exception {
        InputStreamDigester inner = new InputStreamDigester("SHA-256", SHA_KEY, HEX);
        long before = tempFiles();
        byte[] data = data(BufferingDigestSink.MEMORY_THRESHOLD + 12_345);
        Metadata m = new Metadata();
        try (DigestSink sink = pullOnly(inner).digestSink(m, new ParseContext())) {
            sink.write(data, 0, data.length);
            assertEquals(before + 1, tempFiles(), "past the threshold the content is on disk");
            sink.commit();
        }
        assertEquals(before, tempFiles(), "spill file must be deleted on close");
        assertEquals(viaStream(inner, data).get(SHA_KEY), m.get(SHA_KEY));
        assertEquals(Integer.toString(data.length), m.get(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void testDefaultBuffersSmallContentWithNoTempFile() throws Exception {
        InputStreamDigester inner = new InputStreamDigester("MD5", MD5_KEY, HEX);
        byte[] data = data(1234);
        Metadata m = new Metadata();
        long before = tempFiles();
        try (DigestSink sink = pullOnly(inner).digestSink(m, new ParseContext())) {
            sink.write(data, 0, data.length);
            assertEquals(before, tempFiles(), "under the threshold nothing may touch disk");
            sink.commit();
        }
        assertEquals(viaStream(inner, data).get(MD5_KEY), m.get(MD5_KEY));
    }

    @Test
    public void testValuesSetOnlyOnCloseAndCloseIsIdempotent() throws Exception {
        Digester d = new InputStreamDigester("MD5", MD5_KEY, HEX);
        Metadata m = new Metadata();
        DigestSink sink = d.digestSink(m, new ParseContext());
        sink.write(data(100), 0, 100);
        sink.commit();
        assertNull(m.get(MD5_KEY), "digest must not be visible before close");
        sink.close();
        String first = m.get(MD5_KEY);
        assertNotNull(first);
        sink.close();
        assertEquals(first, m.get(MD5_KEY));
    }

    @Test
    public void testCommitAfterCloseThrows() throws Exception {
        Digester d = new InputStreamDigester("MD5", MD5_KEY, HEX);
        Metadata m = new Metadata();
        DigestSink sink = d.digestSink(m, new ParseContext());
        sink.write(data(100), 0, 100);
        sink.close();
        assertThrows(IllegalStateException.class, sink::commit,
                "the chance to publish is gone; failing loudly beats a missing digest");
        assertNull(m.get(MD5_KEY));
    }

    @Test
    public void testUncommittedPublishesNothing() throws Exception {
        for (Digester d : new Digester[]{
                new InputStreamDigester("MD5", MD5_KEY, HEX),
                pullOnly(new InputStreamDigester("MD5", MD5_KEY, HEX)),
                new CompositeDigester(new InputStreamDigester("MD5", MD5_KEY, HEX),
                        pullOnly(new InputStreamDigester("SHA-256", SHA_KEY, HEX)))}) {
            Metadata m = new Metadata();
            DigestSink sink = d.digestSink(m, new ParseContext());
            sink.write(data(5000), 0, 5000);
            sink.close();
            assertNull(m.get(MD5_KEY), "uncommitted sink must not publish: " + d.getClass());
            assertNull(m.get(SHA_KEY));
            assertNull(m.get(HttpHeaders.CONTENT_LENGTH));
        }
    }

    @Test
    public void testWriteAfterCloseThrows() throws Exception {
        for (Digester d : new Digester[]{
                new InputStreamDigester("MD5", MD5_KEY, HEX),
                pullOnly(new InputStreamDigester("MD5", MD5_KEY, HEX)),
                new CompositeDigester(new InputStreamDigester("MD5", MD5_KEY, HEX))}) {
            DigestSink closed = d.digestSink(new Metadata(), new ParseContext());
            closed.close();
            assertThrows(IOException.class, () -> closed.write(1));
            assertThrows(IOException.class, () -> closed.write(new byte[3], 0, 3));
        }
    }

    /** A child whose close() throws unchecked must not leave the children after it open. */
    @Test
    public void testCompositeClosesEveryChildWhenOneThrows() throws Exception {
        AtomicInteger closedChildren = new AtomicInteger();
        Digester counting = new Digester() {
            @Override
            public void digest(TikaInputStream tis, Metadata m, ParseContext ctx) {
            }

            @Override
            public DigestSink digestSink(Metadata m, ParseContext ctx) {
                return new DigestSink() {
                    @Override
                    public void write(int b) {
                    }

                    @Override
                    protected void finish(boolean publish) {
                        closedChildren.incrementAndGet();
                    }
                };
            }
        };
        Digester throwing = (tis, m, ctx) -> {
            throw new IllegalStateException("boom");
        };
        // the thrower is a pull-only digester, so its BufferingDigestSink throws from close()
        Digester d = new CompositeDigester(counting, throwing, counting);
        DigestSink sink = d.digestSink(new Metadata(), new ParseContext());
        sink.write(1);
        sink.commit();   // publishing is what runs the pull digester that throws
        assertThrows(IllegalStateException.class, sink::close);
        assertEquals(2, closedChildren.get(), "children after the throwing one still closed");
    }

    /**
     * A child sink that cannot be created must not leave the already-created children
     * publishing a digest of the zero bytes they received.
     */
    @Test
    public void testCompositeCleansUpWhenAChildSinkCannotBeCreated() throws Exception {
        AtomicInteger closedChildren = new AtomicInteger();
        AtomicBoolean publishedAnything = new AtomicBoolean();
        Digester ok = new Digester() {
            @Override
            public void digest(TikaInputStream tis, Metadata m, ParseContext ctx) {
            }

            @Override
            public DigestSink digestSink(Metadata m, ParseContext ctx) {
                return new DigestSink() {
                    @Override
                    public void write(int b) {
                    }

                    @Override
                    protected void finish(boolean publish) {
                        closedChildren.incrementAndGet();
                        publishedAnything.compareAndSet(false, publish);
                    }
                };
            }
        };
        Digester real = new InputStreamDigester("MD5", MD5_KEY, HEX);
        Digester failing = new Digester() {
            @Override
            public void digest(TikaInputStream tis, Metadata m, ParseContext ctx) {
            }

            @Override
            public DigestSink digestSink(Metadata m, ParseContext ctx) throws IOException {
                throw new IOException("cannot open");
            }
        };
        Metadata m = new Metadata();
        IOException e = assertThrows(IOException.class,
                () -> new CompositeDigester(ok, real, failing).digestSink(m, new ParseContext()));
        assertEquals("cannot open", e.getMessage(), "the original failure propagates");
        assertEquals(1, closedChildren.get(), "already-created sinks are closed");
        assertEquals(0, e.getSuppressed().length, "clean closes add nothing");
        assertFalse(publishedAnything.get(), "no child may publish on the failure path");
        assertNull(m.get(MD5_KEY), "no digest of the zero bytes the children received");
        assertNull(m.get(HttpHeaders.CONTENT_LENGTH), "and no Content-Length: 0");
    }
}
