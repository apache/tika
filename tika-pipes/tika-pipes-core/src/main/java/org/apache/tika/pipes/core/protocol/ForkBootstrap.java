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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;

/**
 * What the parent hands a forked PipesServer on its stdin before anything else: an auth
 * token and the parent's config. The fork presents the token as the first bytes of every
 * socket connection so the other end can reject a stranger that connected first.
 * <p>
 * Frame: {@value #TOKEN_LENGTH_BYTES}-byte token, int config length, config JSON.
 * The config's {@code ${env:...}} references are resolved by the parent, never by the fork.
 */
public final class ForkBootstrap {

    public static final int TOKEN_LENGTH_BYTES = 32;

    // Fixed: the configurable IPC limits live inside the config this frame carries.
    static final int MAX_CONFIG_BYTES = 64 * 1024 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] token;
    private final byte[] config;

    private ForkBootstrap(byte[] token, byte[] config) {
        this.token = token;
        this.config = config;
    }

    public static byte[] newToken() {
        byte[] token = new byte[TOKEN_LENGTH_BYTES];
        RANDOM.nextBytes(token);
        return token;
    }

    /**
     * Serializes the parent's loaded config. Its env references are already resolved, so the
     * bytes may carry secrets; they travel only over the fork's stdin.
     */
    public static byte[] toBytes(TikaJsonConfig tikaJsonConfig) throws IOException {
        return TikaObjectMapperFactory.getMapper().writeValueAsBytes(tikaJsonConfig.getRootNode());
    }

    public static void write(OutputStream out, byte[] token, byte[] config) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(token);
        dos.writeInt(config.length);
        dos.write(config);
        dos.flush();
    }

    public static ForkBootstrap read(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte[] token = new byte[TOKEN_LENGTH_BYTES];
        dis.readFully(token);
        int length = dis.readInt();
        if (length < 0 || length > MAX_CONFIG_BYTES) {
            throw new IOException("bootstrap config length " + length + " is outside [0, "
                    + MAX_CONFIG_BYTES + "]");
        }
        byte[] config = new byte[length];
        dis.readFully(config);
        return new ForkBootstrap(token, config);
    }

    /**
     * Reads {@value #TOKEN_LENGTH_BYTES} bytes and compares them to {@code expected} in
     * constant time. False on a short read or a mismatch.
     */
    public static boolean checkToken(InputStream in, byte[] expected) throws IOException {
        byte[] presented = new byte[TOKEN_LENGTH_BYTES];
        try {
            new DataInputStream(in).readFully(presented);
        } catch (EOFException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, presented);
    }

    public byte[] getToken() {
        return token;
    }

    /** Loads the config without resolving env references: the parent already did. */
    public TikaJsonConfig loadConfig() throws TikaConfigException {
        return TikaJsonConfig.load(new ByteArrayInputStream(config), false);
    }
}
