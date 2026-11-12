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
package org.apache.tika.fuzzing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.code_intelligence.jazzer.junit.FuzzTest;

import org.apache.tika.detect.CharsetSupersets;

/**
 * The WHATWG-patched decoders ({@code CharsetSupersets.decodeAs}) on arbitrary bytes: a decode
 * must finish within the budget (a malformed-heavy page once took half an hour), and decoding
 * the whole array must equal decoding it through a reader in small chunks.
 */
class PatchedCharsetFuzzTest {

    private static final long TIMEOUT_SECONDS = Long.getLong("tika.fuzz.timeout", 10);

    private static final List<Charset> PATCHED = List.of(
            CharsetSupersets.decodeAs(Charset.forName("Big5")),
            CharsetSupersets.decodeAs(Charset.forName("Big5-HKSCS")),
            CharsetSupersets.decodeAs(Charset.forName("EUC-JP")));

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tika-fuzz-decode");
        t.setDaemon(true);
        return t;
    });

    @FuzzTest
    void decode(byte[] data) throws Exception {
        for (Charset cs : PATCHED) {
            Future<String> whole = WORKER.submit(() -> new String(data, cs));
            String decoded;
            try {
                decoded = whole.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                whole.cancel(true);
                throw new IllegalStateException(cs.name() + ": decode of " + data.length
                        + " bytes exceeded " + TIMEOUT_SECONDS + "s", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(cs.name(), e.getCause());
            }
            for (int chunk : new int[] {5, 64}) {
                String streamed = streamed(data, cs, chunk);
                if (!decoded.equals(streamed)) {
                    throw new IllegalStateException(cs.name() + ": whole decode differs from a "
                            + chunk + "-char streamed decode of " + data.length + " bytes");
                }
            }
        }
    }

    private static String streamed(byte[] data, Charset cs, int chunk) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[chunk];
        try (Reader r = new InputStreamReader(new ByteArrayInputStream(data), cs)) {
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }
}
