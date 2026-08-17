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
package org.apache.tika.pipes.core.server;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.protocol.ShutDownReceivedException;
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

/**
 * Centralizes protocol I/O operations shared by {@link PipesServer} and
 * {@link ConnectionHandler}.
 * <p>
 * This class handles the pure protocol mechanics — serialization, framing,
 * and ACK exchange. It does <b>not</b> make lifecycle decisions (exit vs.
 * return, close connection vs. shut down JVM). Callers are responsible for
 * catching exceptions and responding according to their own lifecycle policy.
 */
public class ServerProtocolIO {

    private static final Logger LOG = LoggerFactory.getLogger(ServerProtocolIO.class);

    /**
     * Pre-serialized fallback payload (Smile-encoded {@code PAYLOAD_LIMIT_EXCEEDED} result).
     * Private to prevent external mutation of the array contents — {@code static final}
     * prevents reference reassignment but not element writes.
     */
    private static final byte[] FALLBACK_PAYLOAD_BYTES;

    /**
     * The minimum value accepted for {@code maxPayloadBytes} in the constructor and in
     * {@link org.apache.tika.pipes.core.PipesConfig#setMaxIpcPayloadBytes(int)}: the
     * serialized byte length of {@link #FALLBACK_PAYLOAD_BYTES}.
     * Any configured limit smaller than this cannot carry even the fallback frame.
     */
    public static final int MIN_FALLBACK_PAYLOAD_BYTES;

    static {
        try {
            FALLBACK_PAYLOAD_BYTES = JsonPipesIpc.toBytes(
                    new PipesResult(PipesResult.RESULT_STATUS.PAYLOAD_LIMIT_EXCEEDED,
                            "payload_limit_exceeded"));
            MIN_FALLBACK_PAYLOAD_BYTES = FALLBACK_PAYLOAD_BYTES.length;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final DataInputStream input;
    private final DataOutputStream output;
    private final int maxIpcPayloadBytes;

    public ServerProtocolIO(DataInputStream input, DataOutputStream output, int maxIpcPayloadBytes) {
        if (maxIpcPayloadBytes < MIN_FALLBACK_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "maxIpcPayloadBytes %d is below the minimum %d required to carry a PAYLOAD_LIMIT_EXCEEDED response",
                    maxIpcPayloadBytes, MIN_FALLBACK_PAYLOAD_BYTES));
        }
        this.input = input;
        this.output = output;
        this.maxIpcPayloadBytes = maxIpcPayloadBytes;
    }

    /**
     * Writes a FINISHED message with the serialized result and waits for ACK.
     * <p>
     * Serialization is streamed into a {@link BoundedOutputStream} capped at
     * {@code maxIpcPayloadBytes}. If the payload overflows the cap, the stream aborts
     * before any bytes are sent to the client and a pre-computed
     * {@code PAYLOAD_LIMIT_EXCEEDED} frame is sent instead. This keeps the original
     * result status intact when the payload fits, avoids unbounded heap allocation,
     * and prevents wire desynchronization on the client side.
     *
     * @throws ShutDownReceivedException if SHUT_DOWN is received instead of ACK
     * @throws IOException on serialization or I/O errors
     */
    public void writeFinished(PipesResult pipesResult) throws IOException {
        BoundedOutputStream bos = new BoundedOutputStream(maxIpcPayloadBytes);
        try {
            JsonPipesIpc.toStream(pipesResult, bos);
        } catch (IOException e) {
            if (!bos.overflowed()) {
                throw e;
            }
            LOG.warn("Payload exceeded maxIpcPayloadBytes {}; returning PAYLOAD_LIMIT_EXCEEDED",
                    maxIpcPayloadBytes);
            // If content was already emitted server-side, preserve that status so the
            // client does not duplicate the emission on the passback path. The fixed
            // message replaces the original, which may itself be the overflow source
            // (an accumulated parse-exception stack).
            if (alreadyEmitted(pipesResult.status())) {
                BoundedOutputStream fallbackBos = new BoundedOutputStream(maxIpcPayloadBytes);
                try {
                    JsonPipesIpc.toStream(
                            new PipesResult(pipesResult.status(), "payload_limit_exceeded"),
                            fallbackBos);
                    PipesMessage.finished(fallbackBos.toByteArray()).write(output);
                    awaitAck();
                    return;
                } catch (IOException fallbackE) {
                    if (!fallbackBos.overflowed()) {
                        throw fallbackE;
                    }
                    // Even the status-only result overflows — fall through to the
                    // guaranteed-fit static fallback.
                }
            }
            doWritePayloadLimitExceeded();
            return;
        }
        PipesMessage.finished(bos.toByteArray()).write(output);
        awaitAck();
    }

    /**
     * True for statuses whose content the server already emitted. Replacing one of these
     * with a failure status makes the client treat an emitted document as failed, so a
     * retry emits it a second time.
     */
    private static boolean alreadyEmitted(PipesResult.RESULT_STATUS status) {
        return status == PipesResult.RESULT_STATUS.EMIT_SUCCESS ||
                status == PipesResult.RESULT_STATUS.EMIT_SUCCESS_PASSBACK ||
                status == PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION;
    }

    private void doWritePayloadLimitExceeded() throws IOException {
        // FALLBACK_PAYLOAD_BYTES is pre-computed at class load and guaranteed to be smaller
        // than maxIpcPayloadBytes (enforced by the constructor), so the client always accepts it.
        PipesMessage.finished(FALLBACK_PAYLOAD_BYTES).write(output);
        awaitAck();
    }

    /**
     * Writes an INTERMEDIATE_RESULT message with the serialized metadata and waits for ACK.
     * If the metadata exceeds {@code maxIpcPayloadBytes}, the intermediate is silently skipped
     * (the FINISHED message will still follow).
     *
     * @throws ShutDownReceivedException if SHUT_DOWN is received instead of ACK
     * @throws IOException on serialization or I/O errors
     */
    public void writeIntermediate(Metadata metadata) throws IOException {
        BoundedOutputStream bos = new BoundedOutputStream(maxIpcPayloadBytes);
        try {
            JsonPipesIpc.toStream(metadata, bos);
        } catch (IOException e) {
            if (bos.overflowed()) {
                LOG.warn("Intermediate result payload exceeded maxIpcPayloadBytes {}; skipping intermediate",
                        maxIpcPayloadBytes);
                return;
            }
            throw e;
        }
        PipesMessage.intermediateResult(bos.toByteArray()).write(output);
        awaitAck();
    }

    /**
     * Writes a crash message (OOM, TIMEOUT, or UNSPECIFIED_CRASH) with the
     * serialized stack trace and waits for ACK. Serialization is streamed into
     * a {@link BoundedOutputStream} capped at {@code maxIpcPayloadBytes}. If
     * the stack trace overflows the cap, an empty payload is sent instead.
     *
     * @throws IOException on serialization, I/O, or unexpected ACK response
     */
    public void writeCrash(PipesMessageType crashType, Throwable t) throws IOException {
        String msg = (t != null) ? ExceptionUtils.getStackTrace(t) : "";
        BoundedOutputStream bos = new BoundedOutputStream(maxIpcPayloadBytes);
        try {
            JsonPipesIpc.toStream(msg, bos);
        } catch (IOException e) {
            if (!bos.overflowed()) {
                throw e;
            }
            // Stack trace overflows limit (e.g., CJK chars encode at 3 bytes/char in Smile).
            // Fall back to an empty payload, guaranteed to fit within any valid limit.
            bos = new BoundedOutputStream(maxIpcPayloadBytes);
            JsonPipesIpc.toStream("", bos);
        }
        PipesMessage.crash(crashType, bos.toByteArray()).write(output);
        awaitAck();
    }

    /**
     * Reads a framed message and verifies it is an ACK.
     *
     * @throws ShutDownReceivedException if the message is SHUT_DOWN
     * @throws IOException if the message is any other non-ACK type, or on I/O error
     */
    public void awaitAck() throws IOException {
        PipesMessage msg = PipesMessage.read(input, maxIpcPayloadBytes);
        if (msg.type() == PipesMessageType.ACK) {
            return;
        }
        if (msg.type() == PipesMessageType.SHUT_DOWN) {
            throw new ShutDownReceivedException();
        }
        throw new IOException("Expected ACK but got " + msg.type());
    }

    /**
     * Validates a (resolved) ParseContext's configuration. Must be called <em>after</em>
     * {@link org.apache.tika.serialization.ParseContextUtils#resolveAll}, since configs are lazy
     * and only populated once resolved.
     */
    public static void validateParseContext(ParseContext context)
            throws TikaConfigException {
        if (context == null) {
            return;
        }
        UnpackConfig unpackConfig = context.get(UnpackConfig.class);
        ParseMode parseMode = context.get(ParseMode.class);

        // Warn (don't throw) when UnpackConfig has an emitter but ParseMode is not UNPACK.
        // The global parse-context may include UnpackConfig as a default for UNPACK pipe runs,
        // but the /rmeta and /tika endpoints explicitly set RMETA mode and PipesWorker correctly
        // ignores UnpackConfig for non-UNPACK modes. Throwing here would crash the child process.
        if (unpackConfig != null && !StringUtils.isBlank(unpackConfig.getEmitter())
                && parseMode != null && parseMode != ParseMode.UNPACK) {
            LOG.warn("FetchEmitTuple has UnpackConfig with emitter '{}' but ParseMode is {}. "
                    + "UnpackConfig will be ignored. "
                    + "To extract embedded bytes, set ParseMode.UNPACK in the ParseContext.",
                    unpackConfig.getEmitter(), parseMode);
        }
    }

    /**
     * Trust boundary: caps request-supplied {@link TimeoutLimits} (typed or unresolved
     * {@code timeout-limits} JSON) at {@code pipes.maxTotalTaskTimeoutMillis}; the
     * server's own tika-config limits are never clamped. Must run after
     * {@code ParseContextUtils.resolveAll} and before {@code ParseTimeout} is armed.
     */
    public static void clampRequestTimeoutLimits(ParseContext requestContext,
            ParseContext mergedContext, long maxMillis) {
        if (requestContext == null) {
            return;
        }
        boolean requestSupplied = requestContext.get(TimeoutLimits.class) != null
                || requestContext.hasJsonConfig("timeout-limits");
        if (!requestSupplied) {
            return;
        }
        TimeoutLimits merged = mergedContext.get(TimeoutLimits.class);
        if (merged == null) {
            return;
        }
        TimeoutLimits clamped = merged.clampedTo(maxMillis);
        if (clamped != merged) {
            LOG.warn("request-supplied {} exceeds pipes.maxTotalTaskTimeoutMillis ({}); clamping",
                    merged, maxMillis);
            mergedContext.set(TimeoutLimits.class, clamped);
        }
    }

    /**
     * An {@link OutputStream} backed by a {@link ByteArrayOutputStream} that aborts
     * with an {@link IOException} the moment accumulated bytes would exceed {@code limit}.
     * The caller distinguishes an overflow abort from genuine I/O errors via
     * {@link #overflowed()}.
     */
    private static final class BoundedOutputStream extends OutputStream {

        private final int limit;
        private final ByteArrayOutputStream buf;
        private boolean overflowed = false;

        BoundedOutputStream(int limit) {
            this.limit = limit;
            this.buf = new ByteArrayOutputStream(Math.min(limit, 8192));
        }

        @Override
        public void write(int b) throws IOException {
            if (buf.size() >= limit) {
                overflowed = true;
                throw new IOException("payload_overflow");
            }
            buf.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if ((long) buf.size() + len > limit) {
                overflowed = true;
                throw new IOException("payload_overflow");
            }
            buf.write(b, off, len);
        }

        boolean overflowed() {
            return overflowed;
        }

        byte[] toByteArray() {
            return buf.toByteArray();
        }
    }
}
