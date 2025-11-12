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

package org.apache.tika.parser.digest;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.StringUtils;

public class InputStreamDigester implements DigestingParser.Digester {

    private final String algorithm;
    private final String algorithmKeyName;
    private final DigestingParser.Encoder encoder;
    private final int markLimit;

    public InputStreamDigester(int markLimit, String algorithm, DigestingParser.Encoder encoder) {
        this(markLimit, algorithm, algorithm, encoder);
    }

    /**
     * @param markLimit        limit in bytes to allow for mark/reset.  If the inputstream is longer
     *                         than this limit, the stream will be reset and then spooled to a
     *                         temporary file.
     *                         Throws IllegalArgumentException if < 0.
     * @param algorithm        name of the digest algorithm to retrieve from the Provider
     * @param algorithmKeyName name of the algorithm to store
     *                         as part of the key in the metadata
     *                         when {@link #digest(TikaInputStream, Metadata, ParseContext)} is called
     * @param encoder          encoder to convert the byte array returned from the digester to a
     *                         string
     */
    public InputStreamDigester(int markLimit, String algorithm, String algorithmKeyName,
                               DigestingParser.Encoder encoder) {
        this.algorithm = algorithm;
        this.algorithmKeyName = algorithmKeyName;
        this.encoder = encoder;
        this.markLimit = markLimit;

        if (markLimit < 0) {
            throw new IllegalArgumentException("markLimit must be >= 0");
        }
    }

    /**
     * Copied from commons-codec
     */
    private static MessageDigest updateDigest(MessageDigest digest, TikaInputStream data, Metadata metadata)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        for (int read = data.read(buffer, 0, 1024); read > -1; read = data.read(buffer, 0, 1024)) {
            digest.update(buffer, 0, read);
            total += read;
        }
        setContentLength(total, metadata);
        return digest;
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
     * @param tis           InputStream to digest. Best to use a TikaInputStream because
     *                     of potential need to spool to disk.  InputStream must
     *                     support mark/reset.
     * @param metadata     metadata in which to store the digest information
     * @param parseContext ParseContext -- not actually used yet, but there for future expansion
     * @throws IOException on IO problem or IllegalArgumentException if algorithm couldn't be found
     */
    @Override
    public void digest(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        byte[] digestBytes;
        MessageDigest messageDigest = newMessageDigest();

        updateDigest(messageDigest, tis, metadata);
        digestBytes = messageDigest.digest();

        metadata.set(getMetadataKey(), encoder.encode(digestBytes));
    }

    private String getMetadataKey() {
        return TikaCoreProperties.TIKA_META_PREFIX + "digest" +
                TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + algorithmKeyName;
    }
}
