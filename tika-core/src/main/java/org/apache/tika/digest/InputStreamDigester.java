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

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.StringUtils;

/**
 * Digester that uses {@link TikaInputStream#enableRewind()} and {@link TikaInputStream#rewind()}
 * to read the entire stream for digesting, then rewind for subsequent processing.
 */
public class InputStreamDigester implements Digester {

    private final String algorithm;
    private final String metadataKey;
    private final Encoder encoder;

    /**
     * @param algorithm   name of the digest algorithm to retrieve from the Provider
     * @param metadataKey the full metadata key to use when storing the digest
     *                    (e.g., "X-TIKA:digest:MD5" or "X-TIKA:digest:SHA256:BASE32")
     * @param encoder     encoder to convert the byte array returned from the digester to a
     *                    string
     */
    public InputStreamDigester(String algorithm, String metadataKey, Encoder encoder) {
        this.algorithm = algorithm;
        this.metadataKey = metadataKey;
        this.encoder = encoder;
    }

    private static void setContentLength(long length, Metadata metadata) {
        if (StringUtils.isBlank(metadata.get(Metadata.CONTENT_LENGTH))) {
            //only add it if it hasn't been populated already
            metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
        }
    }

    private MessageDigest newMessageDigest() {
        try {
            Provider provider = getProvider();
            if (provider == null) {
                return MessageDigest.getInstance(algorithm);
            } else {
                return MessageDigest.getInstance(algorithm, provider);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * When subclassing this, becare to ensure that your provider is
     * thread-safe (not likely) or return a new provider with each call.
     *
     * @return provider to use to get the MessageDigest from the algorithm name.
     * Default is to return null.
     */
    protected Provider getProvider() {
        return null;
    }

    /**
     * Digests the TikaInputStream and stores the result in metadata.
     * <p>
     * Uses {@link TikaInputStream#enableRewind()} to ensure the stream can be
     * rewound after digesting, then calls {@link TikaInputStream#rewind()} to
     * reset the stream for subsequent processing.
     *
     * @param tis          TikaInputStream to digest
     * @param metadata     metadata in which to store the digest information
     * @param parseContext ParseContext -- not actually used yet, but there for future expansion
     * @throws IOException on IO problem or IllegalArgumentException if algorithm couldn't be found
     */
    @Override
    public void digest(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        tis.enableRewind();

        MessageDigest messageDigest = newMessageDigest();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = tis.read(buffer)) != -1) {
            messageDigest.update(buffer, 0, read);
            total += read;
        }

        setContentLength(total, metadata);
        metadata.set(metadataKey, encoder.encode(messageDigest.digest()));

        tis.rewind();
    }

}
