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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.ExceptionReporting;

public class CatchAllExceptionMapperTest {

    private static final String SECRET = "secret /path/to/file";

    @Test
    public void fullByDefault() {
        Response r = new CatchAllExceptionMapper(new ExceptionReporting())
                .toResponse(new RuntimeException(SECRET));
        assertEquals(500, r.getStatus());
        String body = (String) r.getEntity();
        assertTrue(body.contains("java.lang.RuntimeException: " + SECRET), body);
    }

    @Test
    public void messageRedacted() {
        Response r = new CatchAllExceptionMapper(
                new ExceptionReporting(ExceptionReporting.Level.MESSAGE_REDACTED, -1))
                .toResponse(new RuntimeException(SECRET));
        assertEquals(500, r.getStatus());
        String body = (String) r.getEntity();
        assertTrue(body.contains("java.lang.RuntimeException"), body);
        assertTrue(body.contains("\tat "), body);
        assertFalse(body.contains(SECRET), body);
    }

    @Test
    public void webApplicationExceptionPassesThrough() {
        Response original = Response.status(404).entity("not here").build();
        Response r = new CatchAllExceptionMapper(
                new ExceptionReporting(ExceptionReporting.Level.MESSAGE_REDACTED, -1))
                .toResponse(new WebApplicationException(original));
        assertEquals(404, r.getStatus());
        assertEquals("not here", r.getEntity());
    }
}
