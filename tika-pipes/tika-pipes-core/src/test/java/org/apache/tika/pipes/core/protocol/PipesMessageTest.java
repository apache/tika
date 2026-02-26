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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PipesMessageTest {

    @Test
    void testRoundTripEmptyPayload() throws IOException {
        for (PipesMessageType type : new PipesMessageType[]{
                PipesMessageType.PING, PipesMessageType.ACK,
                PipesMessageType.READY, PipesMessageType.SHUT_DOWN}) {
            PipesMessage original = new PipesMessage(type, new byte[0]);
            PipesMessage roundTripped = roundTrip(original);
            assertEquals(type, roundTripped.type());
            assertEquals(0, roundTripped.payload().length);
        }
    }

    @Test
    void testRoundTripWithPayload() throws IOException {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        PipesMessage original = PipesMessage.finished(payload);
        PipesMessage roundTripped = roundTrip(original);
        assertEquals(PipesMessageType.FINISHED, roundTripped.type());
        assertArrayEquals(payload, roundTripped.payload());
    }

    @Test
    void testRoundTripAllTypes() throws IOException {
        byte[] payload = "test".getBytes(StandardCharsets.UTF_8);
        for (PipesMessageType type : PipesMessageType.values()) {
            PipesMessage original = new PipesMessage(type, payload);
            PipesMessage roundTripped = roundTrip(original);
            assertEquals(type, roundTripped.type());
            assertArrayEquals(payload, roundTripped.payload());
        }
    }

    @Test
    void testWorkingMessageRoundTrip() throws IOException {
        PipesMessage original = PipesMessage.working(42L);
        PipesMessage roundTripped = roundTrip(original);
        assertEquals(PipesMessageType.WORKING, roundTripped.type());
        assertEquals(42L, roundTripped.lastProgressMillis());
    }

    @Test
    void testDesyncDetectionBadMagic() {
        byte[] bad = new byte[]{0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00};
        assertThrows(ProtocolDesyncException.class, () ->
                PipesMessage.read(new DataInputStream(new ByteArrayInputStream(bad))));
    }

    @Test
    void testDesyncDetectionPartialMagic() {
        // First byte correct, second wrong
        byte[] bad = new byte[]{0x54, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00};
        assertThrows(ProtocolDesyncException.class, () ->
                PipesMessage.read(new DataInputStream(new ByteArrayInputStream(bad))));
    }

    @Test
    void testEofBeforeMagic() {
        byte[] empty = new byte[0];
        assertThrows(EOFException.class, () ->
                PipesMessage.read(new DataInputStream(new ByteArrayInputStream(empty))));
    }

    @Test
    void testEofAfterFirstMagicByte() {
        byte[] partial = new byte[]{0x54};
        assertThrows(EOFException.class, () ->
                PipesMessage.read(new DataInputStream(new ByteArrayInputStream(partial))));
    }

    @Test
    void testNegativePayloadLength() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.write(PipesMessage.MAGIC_0);
        dos.write(PipesMessage.MAGIC_1);
        dos.write(PipesMessageType.FINISHED.getByte());
        dos.writeInt(-1); // negative length
        dos.flush();

        assertThrows(IOException.class, () ->
                PipesMessage.read(new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))));
    }

    @Test
    void testOversizedPayloadRejection() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.write(PipesMessage.MAGIC_0);
        dos.write(PipesMessage.MAGIC_1);
        dos.write(PipesMessageType.FINISHED.getByte());
        dos.writeInt(PipesMessage.MAX_PAYLOAD_BYTES + 1);
        dos.flush();

        assertThrows(IOException.class, () ->
                PipesMessage.read(new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))));
    }

    @Test
    void testRequiresAckAssertions() {
        assertFalse(PipesMessageType.PING.requiresAck());
        assertFalse(PipesMessageType.ACK.requiresAck());
        assertFalse(PipesMessageType.NEW_REQUEST.requiresAck());
        assertFalse(PipesMessageType.SHUT_DOWN.requiresAck());
        assertFalse(PipesMessageType.READY.requiresAck());
        assertFalse(PipesMessageType.WORKING.requiresAck());

        assertTrue(PipesMessageType.STARTUP_FAILED.requiresAck());
        assertTrue(PipesMessageType.INTERMEDIATE_RESULT.requiresAck());
        assertTrue(PipesMessageType.FINISHED.requiresAck());
        assertTrue(PipesMessageType.OOM.requiresAck());
        assertTrue(PipesMessageType.TIMEOUT.requiresAck());
        assertTrue(PipesMessageType.UNSPECIFIED_CRASH.requiresAck());
    }

    @Test
    void testGetByteAndLookupInverse() {
        for (PipesMessageType type : PipesMessageType.values()) {
            byte b = type.getByte();
            PipesMessageType looked = PipesMessageType.lookup(b);
            assertEquals(type, looked, "lookup(getByte()) failed for " + type);
        }
    }

    @Test
    void testLookupUnknownByte() {
        assertThrows(IllegalArgumentException.class, () -> PipesMessageType.lookup(0xFF));
        assertThrows(IllegalArgumentException.class, () -> PipesMessageType.lookup(0x00));
    }

    @Test
    void testExitCodes() {
        assertTrue(PipesMessageType.OOM.getExitCode().isPresent());
        assertEquals(18, PipesMessageType.OOM.getExitCode().getAsInt());

        assertTrue(PipesMessageType.TIMEOUT.getExitCode().isPresent());
        assertEquals(17, PipesMessageType.TIMEOUT.getExitCode().getAsInt());

        assertTrue(PipesMessageType.UNSPECIFIED_CRASH.getExitCode().isPresent());
        assertEquals(19, PipesMessageType.UNSPECIFIED_CRASH.getExitCode().getAsInt());

        assertFalse(PipesMessageType.PING.getExitCode().isPresent());
        assertFalse(PipesMessageType.FINISHED.getExitCode().isPresent());
        assertFalse(PipesMessageType.READY.getExitCode().isPresent());
    }

    @Test
    void testConvenienceFactories() throws IOException {
        assertEquals(PipesMessageType.PING, roundTrip(PipesMessage.ping()).type());
        assertEquals(PipesMessageType.ACK, roundTrip(PipesMessage.ack()).type());
        assertEquals(PipesMessageType.READY, roundTrip(PipesMessage.ready()).type());
        assertEquals(PipesMessageType.SHUT_DOWN, roundTrip(PipesMessage.shutDown()).type());

        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        assertEquals(PipesMessageType.NEW_REQUEST, roundTrip(PipesMessage.newRequest(data)).type());
        assertEquals(PipesMessageType.FINISHED, roundTrip(PipesMessage.finished(data)).type());
        assertEquals(PipesMessageType.INTERMEDIATE_RESULT, roundTrip(PipesMessage.intermediateResult(data)).type());
        assertEquals(PipesMessageType.STARTUP_FAILED, roundTrip(PipesMessage.startupFailed(data)).type());
        assertEquals(PipesMessageType.OOM, roundTrip(PipesMessage.crash(PipesMessageType.OOM, data)).type());
    }

    private PipesMessage roundTrip(PipesMessage msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        msg.write(new DataOutputStream(baos));
        return PipesMessage.read(new DataInputStream(new ByteArrayInputStream(baos.toByteArray())));
    }
}
