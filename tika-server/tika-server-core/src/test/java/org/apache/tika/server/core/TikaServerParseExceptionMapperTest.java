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
package org.apache.tika.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.pipes.core.PipesException;

/**
 * The live shapes: a {@code PipesException} cause (PipesParsingHelper) and a message-only
 * wrapper (interrupted parse). Both are 500, and the body is the cause, not this server's
 * wrapper.
 */
public class TikaServerParseExceptionMapperTest {

    private static final String SECRET = "secret /path/to/file";

    private static Response respond(ExceptionReporting reporting) {
        return new TikaServerParseExceptionMapper(reporting)
                .toResponse(new TikaServerParseException(new PipesException(SECRET)));
    }

    @Test
    public void fullByDefault() {
        Response r = respond(new ExceptionReporting());
        assertEquals(500, r.getStatus());
        String body = (String) r.getEntity();
        assertTrue(body.startsWith("org.apache.tika.pipes.core.PipesException: " + SECRET), body);
        // the wrapper is not the first line and contributes no frames of its own
        assertFalse(body.contains("TikaServerParseException:"), body);
    }

    @Test
    public void messageRedacted() {
        Response r = respond(new ExceptionReporting(ExceptionReporting.Level.MESSAGE_REDACTED, -1));
        assertEquals(500, r.getStatus());
        String body = (String) r.getEntity();
        assertTrue(body.startsWith("org.apache.tika.pipes.core.PipesException"), body);
        assertTrue(body.contains("\tat "), body);
        assertFalse(body.contains(SECRET), body);
    }

    @Test
    public void maxLength() {
        // 50 cuts mid-message: the start of SECRET survives but not all of it
        Response r = respond(new ExceptionReporting(ExceptionReporting.Level.FULL, 50));
        String body = (String) r.getEntity();
        assertTrue(body.endsWith("...[truncated]"), body);
        assertTrue(body.contains("secret"), body);
        assertFalse(body.contains(SECRET), body);
    }

    @Test
    public void noCauseFallsBackToTheWrapper() {
        Response r = new TikaServerParseExceptionMapper(new ExceptionReporting())
                .toResponse(new TikaServerParseException("Parsing interrupted"));
        assertEquals(500, r.getStatus());
        String body = (String) r.getEntity();
        assertTrue(body.contains("Parsing interrupted"), body);
    }
}
