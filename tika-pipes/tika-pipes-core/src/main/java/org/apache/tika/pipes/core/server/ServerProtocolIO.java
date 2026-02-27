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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
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

    private final DataInputStream input;
    private final DataOutputStream output;

    public ServerProtocolIO(DataInputStream input, DataOutputStream output) {
        this.input = input;
        this.output = output;
    }

    /**
     * Writes a FINISHED message with the serialized result and waits for ACK.
     *
     * @throws ShutDownReceivedException if SHUT_DOWN is received instead of ACK
     * @throws IOException on serialization or I/O errors
     */
    public void writeFinished(PipesResult pipesResult) throws IOException {
        byte[] bytes = JsonPipesIpc.toBytes(pipesResult);
        PipesMessage.finished(bytes).write(output);
        awaitAck();
    }

    /**
     * Writes an INTERMEDIATE_RESULT message with the serialized metadata and waits for ACK.
     *
     * @throws ShutDownReceivedException if SHUT_DOWN is received instead of ACK
     * @throws IOException on serialization or I/O errors
     */
    public void writeIntermediate(Metadata metadata) throws IOException {
        byte[] bytes = JsonPipesIpc.toBytes(metadata);
        PipesMessage.intermediateResult(bytes).write(output);
        awaitAck();
    }

    /**
     * Writes a crash message (OOM, TIMEOUT, or UNSPECIFIED_CRASH) with the
     * serialized stack trace and waits for ACK.
     *
     * @throws IOException on serialization, I/O, or unexpected ACK response
     */
    public void writeCrash(PipesMessageType crashType, Throwable t) throws IOException {
        String msg = (t != null) ? ExceptionUtils.getStackTrace(t) : "";
        byte[] bytes = JsonPipesIpc.toBytes(msg);
        PipesMessage.crash(crashType, bytes).write(output);
        awaitAck();
    }

    /**
     * Reads a framed message and verifies it is an ACK.
     *
     * @throws ShutDownReceivedException if the message is SHUT_DOWN
     * @throws IOException if the message is any other non-ACK type, or on I/O error
     */
    public void awaitAck() throws IOException {
        PipesMessage msg = PipesMessage.read(input);
        if (msg.type() == PipesMessageType.ACK) {
            return;
        }
        if (msg.type() == PipesMessageType.SHUT_DOWN) {
            throw new ShutDownReceivedException();
        }
        throw new IOException("Expected ACK but got " + msg.type());
    }

    /**
     * Validates that a FetchEmitTuple's configuration is consistent.
     * <p>
     * If the tuple has an UnpackConfig with an emitter but ParseMode is not UNPACK,
     * that's a configuration error.
     */
    public static void validateFetchEmitTuple(FetchEmitTuple fetchEmitTuple)
            throws TikaConfigException {
        ParseContext requestContext = fetchEmitTuple.getParseContext();
        if (requestContext == null) {
            return;
        }
        UnpackConfig unpackConfig = requestContext.get(UnpackConfig.class);
        ParseMode parseMode = requestContext.get(ParseMode.class);

        if (unpackConfig != null && !StringUtils.isBlank(unpackConfig.getEmitter())
                && parseMode != ParseMode.UNPACK) {
            throw new TikaConfigException(
                    "FetchEmitTuple has UnpackConfig with emitter '" + unpackConfig.getEmitter() +
                            "' but ParseMode is " + parseMode + ". " +
                            "To extract embedded bytes, set ParseMode.UNPACK in the ParseContext.");
        }
    }
}
