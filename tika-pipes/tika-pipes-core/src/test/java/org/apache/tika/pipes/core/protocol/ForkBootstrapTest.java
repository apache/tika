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
package org.apache.tika.pipes.core.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaJsonConfig;

public class ForkBootstrapTest {

    private static String basePath(TikaJsonConfig config) {
        return config.getRootNode().get("fetchers").get("f").get("file-system-fetcher")
                .get("basePath").textValue();
    }

    private static TikaJsonConfig parentLoad(String json) throws Exception {
        return TikaJsonConfig.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testRoundTrip() throws Exception {
        byte[] token = ForkBootstrap.newToken();
        byte[] config = "{\"fetchers\":{}}".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ForkBootstrap.write(bos, token, config);

        ForkBootstrap read = ForkBootstrap.read(new ByteArrayInputStream(bos.toByteArray()));
        assertArrayEquals(token, read.getToken());
        assertNotNull(read.loadConfig().getRootNode().get("fetchers"));
    }

    @Test
    public void testParentResolvesEnv() throws Exception {
        String path = System.getenv("PATH");
        assertNotNull(path, "test needs PATH in the environment");
        TikaJsonConfig parent = parentLoad(
                "{\"fetchers\":{\"f\":{\"file-system-fetcher\":{\"basePath\":\"${env:PATH}\"}}}}");
        ForkBootstrap fork = sendThrough(ForkBootstrap.toBytes(parent));
        assertEquals(path, basePath(fork.loadConfig()));
    }

    @Test
    public void testForkDoesNotResolveEnvAgain() throws Exception {
        // What a parent-resolved secret whose value contains a reference looks like on the wire.
        // Resolving it in the fork would fail on the unset variable or expand it a second time.
        String literal = "s3cret-${env:TIKA_FORK_BOOTSTRAP_TEST_UNSET}";
        byte[] resolved = ("{\"fetchers\":{\"f\":{\"file-system-fetcher\":{\"basePath\":\""
                + literal + "\"}}}}").getBytes(StandardCharsets.UTF_8);
        assertEquals(literal, basePath(sendThrough(resolved).loadConfig()));
    }

    @Test
    public void testConfigLengthIsBounded() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.write(new byte[ForkBootstrap.TOKEN_LENGTH_BYTES]);
        dos.writeInt(ForkBootstrap.MAX_CONFIG_BYTES + 1);
        assertThrows(IOException.class,
                () -> ForkBootstrap.read(new ByteArrayInputStream(bos.toByteArray())));
    }

    @Test
    public void testTruncatedBootstrapFails() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ForkBootstrap.write(bos, ForkBootstrap.newToken(), new byte[100]);
        byte[] truncated = Arrays.copyOf(bos.toByteArray(), bos.size() - 1);
        assertThrows(EOFException.class,
                () -> ForkBootstrap.read(new ByteArrayInputStream(truncated)));
    }

    @Test
    public void testCheckToken() throws Exception {
        byte[] token = ForkBootstrap.newToken();
        assertTrue(ForkBootstrap.checkToken(new ByteArrayInputStream(token), token));

        byte[] wrong = token.clone();
        wrong[0] ^= 1;
        assertFalse(ForkBootstrap.checkToken(new ByteArrayInputStream(wrong), token));

        byte[] shortRead = Arrays.copyOf(token, token.length - 1);
        assertFalse(ForkBootstrap.checkToken(new ByteArrayInputStream(shortRead), token));
    }

    private static ForkBootstrap sendThrough(byte[] config) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ForkBootstrap.write(bos, ForkBootstrap.newToken(), config);
        return ForkBootstrap.read(new ByteArrayInputStream(bos.toByteArray()));
    }
}
