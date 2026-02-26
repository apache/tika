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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Uniform framed message for the PipesClient/PipesServer IPC protocol.
 * <p>
 * Wire format: {@code [MAGIC 0x54 0x4B][TYPE 1B][LEN 4B][PAYLOAD]}
 * <ul>
 *   <li>MAGIC — two bytes {@code 0x54 0x4B} ("TK") for desync detection</li>
 *   <li>TYPE — one byte identifying the {@link PipesMessageType}</li>
 *   <li>LEN — four-byte big-endian payload length (0 for empty payloads)</li>
 *   <li>PAYLOAD — {@code LEN} bytes of payload data</li>
 * </ul>
 */
public record PipesMessage(PipesMessageType type, byte[] payload) {

    static final byte MAGIC_0 = 0x54; // 'T'
    static final byte MAGIC_1 = 0x4B; // 'K'

    /** Maximum payload size: 100 MB (same as old MAX_FETCH_EMIT_TUPLE_BYTES). */
    public static final int MAX_PAYLOAD_BYTES = 100 * 1024 * 1024;

    private static final byte[] EMPTY = new byte[0];

    /**
     * Reads one framed message from the stream.
     *
     * @throws ProtocolDesyncException if magic bytes don't match
     * @throws EOFException if the stream ends before a complete message
     * @throws IOException on payload size violations or I/O errors
     */
    public static PipesMessage read(DataInputStream in) throws IOException {
        int m0 = in.read();
        if (m0 == -1) {
            throw new EOFException("Stream closed before magic byte");
        }
        int m1 = in.read();
        if (m1 == -1) {
            throw new EOFException("Stream closed after first magic byte");
        }
        if ((byte) m0 != MAGIC_0 || (byte) m1 != MAGIC_1) {
            throw new ProtocolDesyncException(
                    String.format(Locale.ROOT, "Expected magic 0x%02x%02x but got 0x%02x%02x",
                            MAGIC_0 & 0xFF, MAGIC_1 & 0xFF, m0 & 0xFF, m1 & 0xFF));
        }

        int typeByte = in.read();
        if (typeByte == -1) {
            throw new EOFException("Stream closed before type byte");
        }
        PipesMessageType type = PipesMessageType.lookup(typeByte);

        int len = in.readInt();
        if (len < 0) {
            throw new IOException("Negative payload length: " + len);
        }
        if (len > MAX_PAYLOAD_BYTES) {
            throw new IOException("Payload length " + len +
                    " exceeds maximum of " + MAX_PAYLOAD_BYTES + " bytes");
        }

        byte[] payload;
        if (len == 0) {
            payload = EMPTY;
        } else {
            payload = new byte[len];
            in.readFully(payload);
        }
        return new PipesMessage(type, payload);
    }

    /**
     * Writes this message to the stream and flushes.
     */
    public void write(DataOutputStream out) throws IOException {
        out.write(MAGIC_0);
        out.write(MAGIC_1);
        out.write(type.getByte());
        out.writeInt(payload.length);
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    // ---- convenience factories ----

    public static PipesMessage ping() {
        return new PipesMessage(PipesMessageType.PING, EMPTY);
    }

    public static PipesMessage ack() {
        return new PipesMessage(PipesMessageType.ACK, EMPTY);
    }

    public static PipesMessage ready() {
        return new PipesMessage(PipesMessageType.READY, EMPTY);
    }

    public static PipesMessage shutDown() {
        return new PipesMessage(PipesMessageType.SHUT_DOWN, EMPTY);
    }

    /**
     * Creates a WORKING heartbeat with the last-progress timestamp in the payload.
     *
     * @param lastProgressMillis epoch millis of the last progress update
     */
    public static PipesMessage working(long lastProgressMillis) {
        byte[] payload = ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(lastProgressMillis)
                .array();
        return new PipesMessage(PipesMessageType.WORKING, payload);
    }

    public static PipesMessage newRequest(byte[] payload) {
        return new PipesMessage(PipesMessageType.NEW_REQUEST, payload);
    }

    public static PipesMessage finished(byte[] payload) {
        return new PipesMessage(PipesMessageType.FINISHED, payload);
    }

    public static PipesMessage intermediateResult(byte[] payload) {
        return new PipesMessage(PipesMessageType.INTERMEDIATE_RESULT, payload);
    }

    public static PipesMessage startupFailed(byte[] payload) {
        return new PipesMessage(PipesMessageType.STARTUP_FAILED, payload);
    }

    public static PipesMessage crash(PipesMessageType crashType, byte[] payload) {
        return new PipesMessage(crashType, payload);
    }

    /**
     * Extracts the last-progress timestamp from a WORKING message payload.
     *
     * @return epoch millis of the last progress update reported by the server
     */
    public long lastProgressMillis() {
        if (type != PipesMessageType.WORKING) {
            throw new IllegalStateException("lastProgressMillis() only valid for WORKING messages");
        }
        return ByteBuffer.wrap(payload)
                .order(ByteOrder.BIG_ENDIAN)
                .getLong();
    }
}
