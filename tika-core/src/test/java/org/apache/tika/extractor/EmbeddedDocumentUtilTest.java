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
package org.apache.tika.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.config.ExceptionReporting.Level;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * The configured policy must govern tk:exception:warn and tk:exception:embedded-stream-exception,
 * the two keys parsers write through EmbeddedDocumentUtil.
 */
public class EmbeddedDocumentUtilTest {

    private static final String MESSAGE = "inner secret";

    private static Throwable wrapped() {
        return new TikaException("Unexpected RuntimeException from a parser",
                new IOException(MESSAGE));
    }

    private static ParseContext context(Level level) {
        ParseContext context = new ParseContext();
        context.set(ExceptionReporting.class,
                new ExceptionReporting(level, ExceptionReporting.UNLIMITED));
        return context;
    }

    private static void assertLevel(Level level, Metadata metadata, Property key) {
        String recorded = metadata.get(key);
        assertTrue(recorded.startsWith("org.apache.tika.exception.TikaException"), recorded);
        assertTrue(recorded.contains("java.io.IOException"), recorded);
        assertEquals(level == Level.FULL, recorded.contains(MESSAGE), level + ":\n" + recorded);
        assertEquals(level != Level.REDACTED, recorded.contains("\tat "),
                level + ":\n" + recorded);
    }

    @Test
    public void recordExceptionHonorsPolicy() {
        for (Level level : Level.values()) {
            Metadata metadata = new Metadata();
            EmbeddedDocumentUtil.recordException(wrapped(), metadata, context(level));
            assertLevel(level, metadata, TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        }
    }

    @Test
    public void recordEmbeddedStreamExceptionHonorsPolicy() {
        for (Level level : Level.values()) {
            Metadata metadata = new Metadata();
            EmbeddedDocumentUtil.recordEmbeddedStreamException(wrapped(), metadata,
                    context(level));
            assertLevel(level, metadata,
                    TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM);
        }
    }

    @Test
    public void deprecatedFormsAreFullAndKeepTheWrapper() {
        Metadata metadata = new Metadata();
        EmbeddedDocumentUtil.recordException(wrapped(), metadata);
        EmbeddedDocumentUtil.recordEmbeddedStreamException(wrapped(), metadata);
        assertLevel(Level.FULL, metadata, TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertLevel(Level.FULL, metadata,
                TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM);
    }

    @Test
    public void maxLengthBoundsBothKeys() {
        ParseContext context = new ParseContext();
        context.set(ExceptionReporting.class, new ExceptionReporting(Level.FULL, 40));
        Metadata metadata = new Metadata();
        EmbeddedDocumentUtil.recordException(wrapped(), metadata, context);
        EmbeddedDocumentUtil.recordEmbeddedStreamException(wrapped(), metadata, context);
        for (Property key : new Property[]{TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM}) {
            String recorded = metadata.get(key);
            assertTrue(recorded.endsWith("...[truncated]"), recorded);
            assertFalse(recorded.contains(MESSAGE), recorded);
        }
    }
}
