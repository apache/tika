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
package org.apache.tika.pipes.grpc.compose;

import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.grpc.adapter.ContributionLimits;
import org.apache.tika.grpc.mapper.AdapterMerge;

/**
 * One external gRPC parser that tika-grpc calls from ParseBytes, from the {@code "grpc"}
 * section's {@code externalParsers} list (TIKA-4771). The call itself and the mapping of the
 * reply come from the adapter jar whose
 * {@link org.apache.tika.grpc.adapter.ExternalParserAdapter#producerId()} this entry names;
 * the entry says where the parser runs, how to connect, and how much to accept. Example, for
 * FastWARC (TIKA-4951) running next to tika-grpc, with its adapter jar (a separate
 * project) on the classpath:
 * <pre>
 * "externalParsers": [{
 *   "producerId": "fastwarc",
 *   "target": "localhost:50060",
 *   "plaintext": true,
 *   "options": {"verifyDigests": true}
 * }]
 * </pre>
 * <b>Credentials.</b> The channel uses TLS with the JVM's default trust store unless the entry
 * says otherwise: {@code "plaintext": true} to opt out, or a {@code tls} object with a
 * {@code caFile} (PEM) to trust a private CA, and {@code certFile} plus {@code keyFile} (PEM)
 * for mutual TLS. Setting both {@code plaintext} and {@code tls} is an error.
 */
public class ExternalParserConfig {

    /** The deadline for each call when {@code timeoutMs} is not set. */
    public static final long DEFAULT_TIMEOUT_MS = 60_000;

    /** The largest reply accepted from the parser when {@code maxReplyBytes} is not set. */
    public static final int DEFAULT_MAX_REPLY_BYTES = 128 * 1024 * 1024;

    private String producerId;
    private String target;
    private boolean plaintext;
    private Tls tls;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;
    private Long maxContentBytes;
    private int maxReplyBytes = DEFAULT_MAX_REPLY_BYTES;
    private int maxChildren = AdapterMerge.Limits.DEFAULTS.maxChildren();
    private int maxKeysPerDocument = AdapterMerge.Limits.DEFAULTS.maxKeysPerDocument();
    private int maxValueChars = AdapterMerge.Limits.DEFAULTS.maxValueChars();
    private long maxEmbeddedBytes = AdapterMerge.Limits.DEFAULTS.maxEmbeddedBytes();
    private boolean keepRawReply;
    private JsonNode options;

    /** TLS settings for the channel to the parser; all optional. */
    public static class Tls {
        private String caFile;
        private String certFile;
        private String keyFile;

        /** PEM file of the CAs to trust instead of the JVM's defaults. */
        public String getCaFile() {
            return caFile;
        }

        public void setCaFile(String caFile) {
            this.caFile = caFile;
        }

        /** PEM certificate chain to present, for mutual TLS; needs {@code keyFile}. */
        public String getCertFile() {
            return certFile;
        }

        public void setCertFile(String certFile) {
            this.certFile = certFile;
        }

        /** PEM private key for {@code certFile}. */
        public String getKeyFile() {
            return keyFile;
        }

        public void setKeyFile(String keyFile) {
            this.keyFile = keyFile;
        }
    }

    /**
     * The adapter to use, by its producer id, which also names the Document's
     * {@code ExtensionResult} slot. Must be unique across the list.
     */
    public String getProducerId() {
        return producerId;
    }

    public void setProducerId(String producerId) {
        this.producerId = producerId;
    }

    /** The parser's address, in any form gRPC's channel builder accepts ({@code host:port}). */
    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    /** Connect without TLS. {@code false} by default: the channel uses TLS. */
    public boolean isPlaintext() {
        return plaintext;
    }

    public void setPlaintext(boolean plaintext) {
        this.plaintext = plaintext;
    }

    /** A private CA, or a client certificate for mutual TLS; {@code null} for system TLS. */
    public Tls getTls() {
        return tls;
    }

    public void setTls(Tls tls) {
        this.tls = tls;
    }

    /** The deadline for each call, in milliseconds. */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive, got: " + timeoutMs);
        }
        this.timeoutMs = timeoutMs;
    }

    /**
     * The largest reply tika-grpc reads from this parser, in bytes: 128 MiB by default,
     * independent of the server's {@code maxInboundMessageBytes}. A larger reply fails the
     * slot. Each ParseBytes request in flight can hold one reply per parser.
     */
    public int getMaxReplyBytes() {
        return maxReplyBytes;
    }

    public void setMaxReplyBytes(int maxReplyBytes) {
        if (maxReplyBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxReplyBytes must be positive, got: " + maxReplyBytes);
        }
        this.maxReplyBytes = maxReplyBytes;
    }

    /**
     * Documents larger than this are not sent; their slot is {@code STATUS_SKIPPED}. When not
     * set, any document ParseBytes accepted is sent.
     */
    public Long getMaxContentBytes() {
        return maxContentBytes;
    }

    public void setMaxContentBytes(Long maxContentBytes) {
        if (maxContentBytes != null && maxContentBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxContentBytes must be positive, got: " + maxContentBytes);
        }
        this.maxContentBytes = maxContentBytes;
    }

    /**
     * The most embedded documents one reply adds to the Document (a WARC's records, say);
     * 10,000 by default. Past it the slot is marked truncated.
     */
    public int getMaxChildren() {
        return maxChildren;
    }

    public void setMaxChildren(int maxChildren) {
        this.maxChildren = nonNegative("maxChildren", maxChildren);
    }

    /**
     * The most metadata keys the adapter may add to one embedded document, and to the parent;
     * 256 by default. Keys past it are dropped with a warning.
     */
    public int getMaxKeysPerDocument() {
        return maxKeysPerDocument;
    }

    public void setMaxKeysPerDocument(int maxKeysPerDocument) {
        this.maxKeysPerDocument = nonNegative("maxKeysPerDocument", maxKeysPerDocument);
    }

    /** Values the adapter writes are cut to this many characters; 8,192 by default. */
    public int getMaxValueChars() {
        return maxValueChars;
    }

    public void setMaxValueChars(int maxValueChars) {
        this.maxValueChars = nonNegative("maxValueChars", maxValueChars);
    }

    /**
     * The most bytes the embedded documents of one reply may add to the Document; 64 MiB by
     * default, room for the record headers of a 256 MiB WARC. The adapter's builder stops
     * keeping children near it (counting UTF-8 bytes),
     * and the merge enforces it on the serialized documents. Past it the slot is marked
     * truncated.
     */
    public long getMaxEmbeddedBytes() {
        return maxEmbeddedBytes;
    }

    public void setMaxEmbeddedBytes(long maxEmbeddedBytes) {
        if (maxEmbeddedBytes < 0) {
            throw new IllegalArgumentException(
                    "maxEmbeddedBytes must not be negative, got: " + maxEmbeddedBytes);
        }
        this.maxEmbeddedBytes = maxEmbeddedBytes;
    }

    /**
     * Whether the slot also carries the parser's whole reply as an {@code Any}, for consumers
     * that want more than the adapter maps. Off by default: replies can be large, and it is
     * the first thing dropped when the ParseBytes reply is over its size limit.
     */
    public boolean isKeepRawReply() {
        return keepRawReply;
    }

    public void setKeepRawReply(boolean keepRawReply) {
        this.keepRawReply = keepRawReply;
    }

    /**
     * The adapter's own settings, checked by the adapter when the server starts; see the
     * adapter's docs.
     */
    public JsonNode getOptions() {
        return options;
    }

    public void setOptions(JsonNode options) {
        this.options = options;
    }

    ContributionLimits contributionLimits() {
        return new ContributionLimits(maxChildren, maxKeysPerDocument, maxValueChars,
                maxEmbeddedBytes, ContributionLimits.DEFAULTS.maxWarnings(),
                ContributionLimits.DEFAULTS.maxWarningChars());
    }

    AdapterMerge.Limits mergeLimits() {
        return new AdapterMerge.Limits(maxChildren, maxKeysPerDocument, maxValueChars,
                maxEmbeddedBytes);
    }

    private static int nonNegative(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, got: " + value);
        }
        return value;
    }

    /**
     * Checks the entry is complete and consistent.
     *
     * @throws IllegalArgumentException if the producer id or the address is missing, or the
     *                                  credentials settings contradict each other
     */
    void validate() {
        require(producerId, "producerId");
        if (!PRODUCER_ID.matcher(producerId).matches()) {
            throw new IllegalArgumentException("external parser producerId '" + producerId
                    + "' must be 1 to 64 of a-z, 0-9, '.', '_' and '-', starting with a"
                    + " letter or digit");
        }
        require(target, "target");
        if (plaintext && tls != null) {
            throw new IllegalArgumentException("external parser '" + producerId
                    + "' sets both plaintext and tls");
        }
        if (tls != null && (tls.certFile == null) != (tls.keyFile == null)) {
            throw new IllegalArgumentException("external parser '" + producerId
                    + "': tls.certFile and tls.keyFile go together");
        }
    }

    /** Lower case, so slot names compare the same everywhere they are used. */
    private static final Pattern PRODUCER_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("an external parser"
                    + (producerId == null ? "" : " ('" + producerId + "')")
                    + " has no " + name);
        }
    }
}
