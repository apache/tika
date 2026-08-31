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
import org.apache.tika.exception.TikaException;

public class TikaServerParseExceptionMapperTest {

    private static final String SECRET = "secret /path/to/file";

    private static String body(ExceptionReporting reporting) {
        Response r = new TikaServerParseExceptionMapper(reporting)
                .toResponse(new TikaServerParseException(new TikaException(SECRET)));
        assertEquals(422, r.getStatus());
        return (String) r.getEntity();
    }

    @Test
    public void fullByDefault() {
        String body = body(null);
        assertTrue(body.contains("org.apache.tika.exception.TikaException: " + SECRET), body);
    }

    @Test
    public void messageRedacted() {
        String body = body(new ExceptionReporting(ExceptionReporting.Level.MESSAGE_REDACTED, -1));
        assertTrue(body.contains("org.apache.tika.exception.TikaException"), body);
        assertTrue(body.contains("\tat "), body);
        assertFalse(body.contains(SECRET), body);
    }

    @Test
    public void maxLength() {
        String body = body(new ExceptionReporting(ExceptionReporting.Level.FULL, 30));
        assertTrue(body.endsWith("...[truncated]"), body);
        assertFalse(body.contains(SECRET), body);
    }
}
