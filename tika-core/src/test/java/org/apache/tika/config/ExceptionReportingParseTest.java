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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.ExceptionReporting.Level;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * The configured level must govern both the container and the embedded exception, in both
 * RMETA (RecursiveParserWrapper) and CONCATENATE (CompositeParser roll-up) modes.
 */
public class ExceptionReportingParseTest extends TikaTest {

    private static final String MESSAGE = "another null pointer exception";
    private static final String CLASS = "java.lang.NullPointerException";

    private static ParseContext context(Level level, int maxLength) {
        ParseContext context = new ParseContext();
        context.set(ExceptionReporting.class, new ExceptionReporting(level, maxLength));
        return context;
    }

    private static void assertLevel(Level level, String trace) {
        assertNotNull(trace);
        assertTrue(trace.contains(CLASS), trace);
        assertEquals(level == Level.FULL, trace.contains(MESSAGE), level + ":\n" + trace);
        assertEquals(level != Level.REDACTED, trace.contains("\tat "), level + ":\n" + trace);
    }

    @Test
    public void rmetaContainer() throws Exception {
        for (Level level : Level.values()) {
            List<Metadata> list =
                    getRecursiveMetadata("embedded_then_npe.xml", context(level, -1), true);
            String trace = list.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
            assertLevel(level, trace);
            // the wrapper is kept: it names the failing parser and matches pipes output
            assertTrue(trace.startsWith("org.apache.tika.exception.TikaException"), trace);
        }
    }

    @Test
    public void rmetaEmbedded() throws Exception {
        for (Level level : Level.values()) {
            List<Metadata> list =
                    getRecursiveMetadata("embedded_with_npe.xml", context(level, -1), true);
            assertEquals(2, list.size());
            assertLevel(level, list.get(1).get(TikaCoreProperties.EMBEDDED_EXCEPTION));
        }
    }

    @Test
    public void concatenateEmbedded() throws Exception {
        for (Level level : Level.values()) {
            Metadata metadata = getXML("embedded_with_npe.xml", context(level, -1)).metadata;
            String[] traces = metadata.getValues(TikaCoreProperties.EMBEDDED_EXCEPTION);
            assertEquals(1, traces.length);
            assertLevel(level, traces[0]);
        }
    }

    @Test
    public void maxLengthBounds() throws Exception {
        for (Level level : Level.values()) {
            List<Metadata> list =
                    getRecursiveMetadata("embedded_with_npe.xml", context(level, 40), true);
            String trace = list.get(1).get(TikaCoreProperties.EMBEDDED_EXCEPTION);
            assertTrue(trace.endsWith("...[truncated]"), trace);
            assertFalse(trace.contains(MESSAGE));
        }
    }
}
