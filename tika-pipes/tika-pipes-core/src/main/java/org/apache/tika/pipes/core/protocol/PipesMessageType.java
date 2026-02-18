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

import java.util.Locale;
import java.util.OptionalInt;

/**
 * Unified message types for the PipesClient/PipesServer IPC protocol.
 * <p>
 * Replaces the separate {@code PipesClient.COMMANDS} and
 * {@code PipesServer.PROCESSING_STATUS} enums with a single enum
 * that carries explicit wire bytes, ACK requirements, and exit codes.
 */
public enum PipesMessageType {

    PING(0x01, false, -1),
    ACK(0x02, false, -1),
    NEW_REQUEST(0x03, false, -1),
    SHUT_DOWN(0x04, false, -1),
    READY(0x05, false, -1),
    STARTUP_FAILED(0x06, true, -1),
    INTERMEDIATE_RESULT(0x07, true, -1),
    WORKING(0x08, false, -1),
    FINISHED(0x09, true, -1),
    OOM(0x0A, true, 18),
    TIMEOUT(0x0B, true, 17),
    UNSPECIFIED_CRASH(0x0C, true, 19);

    private final int wireByte;
    private final boolean requiresAck;
    private final int exitCode;

    PipesMessageType(int wireByte, boolean requiresAck, int exitCode) {
        this.wireByte = wireByte;
        this.requiresAck = requiresAck;
        this.exitCode = exitCode;
    }

    /**
     * Returns the single byte used on the wire for this message type.
     */
    public byte getByte() {
        return (byte) wireByte;
    }

    /**
     * Returns {@code true} if the receiver must send an ACK after reading
     * a message of this type.
     */
    public boolean requiresAck() {
        return requiresAck;
    }

    /**
     * Returns the exit code the server should use when exiting due to this
     * condition, or empty if this message type does not trigger an exit.
     */
    public OptionalInt getExitCode() {
        if (exitCode < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(exitCode);
    }

    /**
     * Looks up a message type by its wire byte.
     *
     * @param b the wire byte
     * @return the matching message type
     * @throws IllegalArgumentException if no type matches
     */
    public static PipesMessageType lookup(int b) {
        for (PipesMessageType type : values()) {
            if (type.wireByte == b) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Unknown PipesMessageType wire byte: 0x%02x", b & 0xFF));
    }
}
