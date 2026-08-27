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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.config.ExceptionReporting.Level;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

public class ExceptionUtilsTest {

    private static final String[] MESSAGES = {"outer secret", "middle secret", "inner secret",
            "suppressed secret"};

    private static Throwable chain() {
        IllegalStateException inner = new IllegalStateException(MESSAGES[2]);
        IOException middle = new IOException(MESSAGES[1], inner);
        TikaException outer = new TikaException(MESSAGES[0], middle);
        outer.addSuppressed(new RuntimeException(MESSAGES[3]));
        return outer;
    }

    private static String format(Throwable t, Level level) {
        return ExceptionUtils.format(t, new ExceptionReporting(level, ExceptionReporting.UNLIMITED));
    }

    private static void assertNoMessages(String s) {
        for (String m : MESSAGES) {
            assertFalse(s.contains(m), "must not contain '" + m + "':\n" + s);
        }
    }

    @Test
    public void fullMatchesPrintStackTrace() {
        Throwable t = chain();
        String full = format(t, Level.FULL);
        for (String m : MESSAGES) {
            assertTrue(full.contains(m));
        }
        assertTrue(full.contains("Caused by: java.io.IOException: " + MESSAGES[1]));
    }

    @Test
    public void messageRedactedEqualsFullMinusMessages() {
        Throwable t = chain();
        String full = format(t, Level.FULL);
        String redacted = format(t, Level.MESSAGE_REDACTED);
        assertNoMessages(redacted);
        assertTrue(redacted.contains("\tat "));
        assertTrue(redacted.contains("Caused by: java.io.IOException\n"));
        assertTrue(redacted.contains("\tSuppressed: java.lang.RuntimeException\n"));
        assertTrue(redacted.contains(" more\n"), "common-frame elision kept");
        // Strip ": message" from every header line of FULL; the rest must be identical.
        String expected = full.replaceAll("(?m)^((?:\\t*Suppressed: |Caused by: )?[\\w.$]+Exception): .*$", "$1");
        assertEquals(expected, redacted);
    }

    @Test
    public void redactedIsClassChainOnly() {
        String s = format(chain(), Level.REDACTED);
        assertNoMessages(s);
        assertFalse(s.contains("\tat "));
        assertEquals("org.apache.tika.exception.TikaException\n"
                + "\tSuppressed: java.lang.RuntimeException\n"
                + "Caused by: java.io.IOException\n"
                + "Caused by: java.lang.IllegalStateException\n", s);
    }

    @Test
    public void cyclicCauseTerminates() throws Exception {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        for (Level level : Level.values()) {
            String s = format(a, level);
            assertTrue(s.contains("CIRCULAR REFERENCE"), level + ":\n" + s);
        }
    }

    @Test
    public void deepChainCapped() {
        Throwable t = new RuntimeException("leaf");
        for (int i = 0; i < 200; i++) {
            t = new RuntimeException("level " + i, t);
        }
        String s = format(t, Level.REDACTED);
        assertTrue(s.contains("cause chain truncated"));
        assertTrue(s.split("\n").length < 100);
    }

    @Test
    public void maxLengthTruncates() {
        Throwable t = chain();
        String s = ExceptionUtils.format(t, new ExceptionReporting(Level.FULL, 50));
        assertTrue(s.startsWith(format(t, Level.FULL).substring(0, 50)));
        assertTrue(s.endsWith("...[truncated]"));
        assertEquals(50 + "...[truncated]".length(), s.length());
    }

    @Test
    public void maxLengthDoesNotSplitSurrogatePair() {
        // Cut lands between the two UTF-16 units of the astral char; must back off by one.
        Throwable t = new RuntimeException("x😀yyyyyyyy");
        int cut = "java.lang.RuntimeException: x".length() + 1;
        String s = ExceptionUtils.format(t, new ExceptionReporting(Level.FULL, cut));
        assertTrue(s.endsWith("x...[truncated]"), s);
    }

    @Test
    public void nullContextAndPolicyAreFull() {
        Throwable t = chain();
        String full = format(t, Level.FULL);
        assertEquals(full, ExceptionUtils.format(t, (ParseContext) null));
        assertEquals(full, ExceptionUtils.format(t, (ExceptionReporting) null));
        assertEquals(full, ExceptionUtils.format(t, new ParseContext()));
    }

    @Test
    public void contextPolicyApplies() {
        ParseContext context = new ParseContext();
        context.set(ExceptionReporting.class, new ExceptionReporting(Level.REDACTED, -1));
        assertNoMessages(ExceptionUtils.format(chain(), context));
    }

    @Test
    public void unwrapTikaException() {
        Throwable t = chain();
        assertEquals(t.getCause(), ExceptionUtils.unwrapTikaException(t));
        Throwable sub = new org.apache.tika.exception.EncryptedDocumentException(t);
        assertEquals(sub, ExceptionUtils.unwrapTikaException(sub));
        Throwable bare = new TikaException("no cause");
        assertEquals(bare, ExceptionUtils.unwrapTikaException(bare));
    }

    @Test
    public void invalidMaxLength() {
        assertThrows(IllegalArgumentException.class, () -> new ExceptionReporting(Level.FULL, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExceptionReporting(Level.FULL, -2));
        assertThrows(NullPointerException.class, () -> new ExceptionReporting(null, -1));
    }
}
