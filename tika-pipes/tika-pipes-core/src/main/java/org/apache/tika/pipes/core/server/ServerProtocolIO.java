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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.config.OutputLimits;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.writelimiter.MetadataWriteLimiterFactory;
import org.apache.tika.metadata.writelimiter.StandardMetadataLimiterFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pages.PagesConfig;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.protocol.ShutDownReceivedException;
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.WriteLimiter;
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
    private final ExceptionReporting exceptionReporting;

    /**
     * @deprecated since 4.1, use the overload taking an {@link ExceptionReporting}
     */
    @Deprecated
    public ServerProtocolIO(DataInputStream input, DataOutputStream output, int maxIpcPayloadBytes) {
        this(input, output, maxIpcPayloadBytes, new ExceptionReporting());
    }

    public ServerProtocolIO(DataInputStream input, DataOutputStream output, int maxIpcPayloadBytes,
                            ExceptionReporting exceptionReporting) {
        if (maxIpcPayloadBytes < MIN_FALLBACK_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "maxIpcPayloadBytes %d is below the minimum %d required to carry a PAYLOAD_LIMIT_EXCEEDED response",
                    maxIpcPayloadBytes, MIN_FALLBACK_PAYLOAD_BYTES));
        }
        this.input = input;
        this.output = output;
        this.maxIpcPayloadBytes = maxIpcPayloadBytes;
        this.exceptionReporting = exceptionReporting;
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
        long serStart = System.nanoTime();
        try {
            JsonPipesIpc.toStream(pipesResult, bos);
            lastRespSerNanos = System.nanoTime() - serStart;
        } catch (IOException e) {
            lastRespSerNanos = System.nanoTime() - serStart;
            if (!bos.overflowed()) {
                if (!(e instanceof JsonProcessingException)) {
                    throw e;
                }
                // the result itself cannot be encoded (the buffer is in memory, so this is
                // not the pipe): report it for this document rather than let the parent
                // count a worker crash and lose the document
                LOG.warn("result could not be serialized; returning a status-only result", e);
                writeStatusOnly(unserializableStatus(pipesResult.status()),
                        "result could not be serialized: " + e.getMessage());
                return;
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
        byte[] payload = bos.toByteArray();
        lastRespBytes = payload.length;
        long writeStart = System.nanoTime();
        PipesMessage.finished(payload).write(output);
        long ackStart = System.nanoTime();
        lastRespWriteNanos = ackStart - writeStart;
        awaitAck();
        lastRespAckNanos = System.nanoTime() - ackStart;
    }

    // Last FINISHED-frame costs, read by ConnectionHandler for its per-parse timing line.
    // One request at a time per connection, so plain fields suffice.
    private long lastRespSerNanos = -1;
    private long lastRespWriteNanos = -1;
    private long lastRespAckNanos = -1;
    private int lastRespBytes = -1;

    long getLastRespSerNanos() {
        return lastRespSerNanos;
    }

    long getLastRespWriteNanos() {
        return lastRespWriteNanos;
    }

    long getLastRespAckNanos() {
        return lastRespAckNanos;
    }

    int getLastRespBytes() {
        return lastRespBytes;
    }

    /** Write+ACK cost of the INTERMEDIATE_RESULT frame; -1 when none was sent. */
    private long lastIntermediateNanos = -1;

    long getLastIntermediateNanos() {
        return lastIntermediateNanos;
    }

    void resetLastTimings() {
        lastRespSerNanos = -1;
        lastRespWriteNanos = -1;
        lastRespAckNanos = -1;
        lastRespBytes = -1;
        lastIntermediateNanos = -1;
    }

    /**
     * A success whose payload is lost is a parse failure for the parent; an emitted status
     * stays so the parent does not emit again, and a failure keeps its own status and category.
     */
    static PipesResult.RESULT_STATUS unserializableStatus(PipesResult.RESULT_STATUS status) {
        if (alreadyEmitted(status) || status.getCategory() != PipesResult.CATEGORY.SUCCESS) {
            return status;
        }
        return PipesResult.RESULT_STATUS.PARSE_EXCEPTION_NO_EMIT;
    }

    /**
     * A status and message; the same status with a short fixed message when that overflows
     * the limit (the status is what the parent's retry and reporting need); the guaranteed-fit
     * frame when even that does not fit.
     */
    private void writeStatusOnly(PipesResult.RESULT_STATUS status, String message)
            throws IOException {
        for (String m : new String[]{message, "result could not be serialized"}) {
            BoundedOutputStream fallbackBos = new BoundedOutputStream(maxIpcPayloadBytes);
            try {
                JsonPipesIpc.toStream(new PipesResult(status, m), fallbackBos);
            } catch (IOException e) {
                if (!fallbackBos.overflowed()) {
                    throw e;
                }
                continue;
            }
            PipesMessage.finished(fallbackBos.toByteArray()).write(output);
            awaitAck();
            return;
        }
        doWritePayloadLimitExceeded();
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
        writeIntermediate(metadata, null);
    }

    /**
     * Like {@link #writeIntermediate(Metadata)}, but runs {@code afterFrameWritten} once the
     * frame is flushed to the socket, before waiting for the client's ACK. Lets the caller
     * unblock the parse worker while the ACK is still in flight -- the ACK round trip would
     * otherwise sit between pre-parse and parse on every request. Not invoked when the
     * oversized intermediate is skipped or the write fails; callers must handle those paths
     * themselves.
     */
    public void writeIntermediate(Metadata metadata, Runnable afterFrameWritten) throws IOException {
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
        long interStart = System.nanoTime();
        PipesMessage.intermediateResult(bos.toByteArray()).write(output);
        if (afterFrameWritten != null) {
            afterFrameWritten.run();
        }
        lastIntermediateNanos = System.nanoTime() - interStart;
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
        String msg = (t != null) ? ExceptionUtils.format(t, exceptionReporting) : "";
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
     * Caps request-supplied {@link TimeoutLimits} (typed or unresolved {@code timeout-limits}
     * JSON) field by field at the operator's own limits ({@code operatorContext}: the server's
     * config plus any preset, or the class defaults) and at
     * {@code pipes.maxTotalTaskTimeoutMillis}: a request may shorten a timeout but not extend
     * it. The operator's limits are never clamped. Must run after
     * {@code ParseContextUtils.resolveAll} and before {@code ParseTimeout} is armed.
     */
    public static void clampRequestTimeoutLimits(ParseContext requestContext,
            ParseContext operatorContext, ParseContext mergedContext, long maxMillis) {
        if (requestContext == null) {
            return;
        }
        boolean requestSupplied = requestContext.get(TimeoutLimits.class) != null
                || requestContext.hasJsonConfig("timeout-limits");
        if (!requestSupplied) {
            return;
        }
        TimeoutLimits merged = mergedContext.get(TimeoutLimits.class);
        if (merged == null || merged == operatorContext.get(TimeoutLimits.class)) {
            return;
        }
        TimeoutLimits operator = TimeoutLimits.get(operatorContext);
        long total = Math.min(merged.getTotalTaskTimeoutMillis(),
                Math.min(operator.getTotalTaskTimeoutMillis(), maxMillis));
        long progress = Math.min(merged.getProgressTimeoutMillis(),
                Math.min(operator.getProgressTimeoutMillis(), maxMillis));
        if (total != merged.getTotalTaskTimeoutMillis()
                || progress != merged.getProgressTimeoutMillis()) {
            LOG.warn("request-supplied {} exceeds the server's {} (pipes.maxTotalTaskTimeoutMillis"
                    + " {}); clamping", merged, operator, maxMillis);
            TimeoutLimits clamped = new TimeoutLimits(total, progress);
            clamped.setThrowOnDeadline(merged.isThrowOnDeadline());
            mergedContext.set(TimeoutLimits.class, clamped);
        }
    }

    /**
     * A request may tighten the operator's {@link OutputLimits},
     * {@link EmbeddedLimits}, content-handler write limit, metadata limiter and
     * {@code maxUnpackBytes} but not loosen them. Render limits in {@code "pages"} are bounded
     * where the parser resolves them, by the {@link PagesConfig.OperatorPages} recorded here.
     * {@code operatorContext} is the server's config plus any preset, before the request was
     * overlaid; entries it already holds are the operator's and are left alone. Must run after
     * {@code ParseContextUtils.resolveAll}.
     */
    public static void clampRequestLimits(ParseContext operatorContext, ParseContext merged)
            throws TikaConfigException {
        OutputLimits operatorOutput = OutputLimits.get(operatorContext);
        OutputLimits output = merged.get(OutputLimits.class);
        if (output != null && output != operatorContext.get(OutputLimits.class)) {
            OutputLimits tightened = output.tightenedTo(operatorOutput);
            if (tightened != output) {
                LOG.warn("request-supplied {} is looser than the server's {}; clamping",
                        output, operatorOutput);
                merged.set(OutputLimits.class, tightened);
            }
        }
        EmbeddedLimits operatorEmbedded = EmbeddedLimits.get(operatorContext);
        EmbeddedLimits embedded = merged.get(EmbeddedLimits.class);
        if (embedded != null && embedded != operatorContext.get(EmbeddedLimits.class)) {
            EmbeddedLimits tightened = embedded.tightenedTo(operatorEmbedded);
            if (tightened != embedded) {
                LOG.warn("request-supplied {} is looser than the server's {}; clamping",
                        embedded, operatorEmbedded);
                merged.set(EmbeddedLimits.class, tightened);
            }
        }
        MetadataWriteLimiterFactory operatorLimiter =
                operatorContext.get(MetadataWriteLimiterFactory.class);
        MetadataWriteLimiterFactory metadataLimiter = merged.get(MetadataWriteLimiterFactory.class);
        if (operatorLimiter != null && metadataLimiter != operatorLimiter) {
            if (!(operatorLimiter instanceof StandardMetadataLimiterFactory operatorStandard)
                    || !(metadataLimiter instanceof StandardMetadataLimiterFactory standard)) {
                throw new TikaConfigException("a request may not replace the server's " +
                        "metadata limiter with " + metadataLimiter);
            }
            StandardMetadataLimiterFactory tightened = standard.tightenedTo(operatorStandard);
            if (tightened != standard) {
                LOG.warn("request-supplied {} is looser than the server's {}; clamping",
                        standard, operatorStandard);
                merged.set(MetadataWriteLimiterFactory.class, tightened);
            }
        }
        try {
            merged.set(PagesConfig.OperatorPages.class, new PagesConfig.OperatorPages(
                    ParseContextConfig.getConfig(operatorContext, PagesConfig.KEY,
                            PagesConfig.class, null)));
        } catch (IOException e) {
            throw new TikaConfigException("failed to read the server's pages config", e);
        }
        UnpackConfig operatorUnpack = operatorContext.get(UnpackConfig.class);
        UnpackConfig unpack = merged.get(UnpackConfig.class);
        if (unpack != null && unpack != operatorUnpack) {
            long ceiling = operatorUnpack != null ? operatorUnpack.getMaxUnpackBytes()
                    : UnpackConfig.DEFAULT_MAX_UNPACK_BYTES;
            long maxUnpackBytes = ceiling < 0 ? unpack.getMaxUnpackBytes()
                    : unpack.getMaxUnpackBytes() < 0 ? ceiling
                    : Math.min(unpack.getMaxUnpackBytes(), ceiling);
            if (maxUnpackBytes != unpack.getMaxUnpackBytes()) {
                LOG.warn("request-supplied maxUnpackBytes {} exceeds the server's {}; clamping",
                        unpack.getMaxUnpackBytes(), ceiling);
                // the request's own instance, deserialized for this task
                unpack.setMaxUnpackBytes(maxUnpackBytes);
            }
        }
        ContentHandlerFactory operatorFactory = operatorContext.get(ContentHandlerFactory.class);
        ContentHandlerFactory factory = merged.get(ContentHandlerFactory.class);
        if (factory instanceof WriteLimiter limiter && factory != operatorFactory) {
            int ceiling = operatorOutput.getWriteLimit();
            if (operatorFactory instanceof WriteLimiter operatorWriteLimiter) {
                ceiling = tighter(operatorWriteLimiter.getWriteLimit(), ceiling);
            }
            int writeLimit = tighter(limiter.getWriteLimit(), ceiling);
            if (writeLimit != limiter.getWriteLimit()) {
                if (!(factory instanceof BasicContentHandlerFactory basic)) {
                    throw new TikaConfigException("request-supplied write limit " +
                            limiter.getWriteLimit() + " exceeds the server's " + ceiling);
                }
                LOG.warn("request-supplied write limit {} exceeds the server's {}; clamping",
                        limiter.getWriteLimit(), ceiling);
                // the request's own instance, deserialized for this task
                basic.setWriteLimit(writeLimit);
            }
        }
    }

    /** The tighter of two limits where a negative value means unlimited. */
    private static int tighter(int requested, int ceiling) {
        if (ceiling < 0) {
            return requested;
        }
        return requested < 0 ? ceiling : Math.min(requested, ceiling);
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
