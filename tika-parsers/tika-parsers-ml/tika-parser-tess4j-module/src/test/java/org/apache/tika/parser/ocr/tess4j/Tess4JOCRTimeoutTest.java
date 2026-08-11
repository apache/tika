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
package org.apache.tika.parser.ocr.tess4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.parser.ParseContext;

/**
 * TIKA-4813 follow-up: {@code Tess4JParser#doOCRWithTimeout} runs the blocking, native
 * {@code Tesseract.doOCR} call on a background thread and checkpoints {@link ParseTimeout}
 * while waiting, so a slow OCR call is bounded by the task's remaining budget instead of
 * running unchecked until the server's stall watchdog kills the whole forked JVM. Tested
 * via reflection against the private method directly, using a {@link Tesseract} subclass
 * that overrides the {@code doOCR(BufferedImage)} default method -- no native Tesseract
 * library is exercised, so this doesn't need {@code tess4jAvailable} gating.
 */
public class Tess4JOCRTimeoutTest {

    private static Method doOCRWithTimeoutMethod() throws NoSuchMethodException {
        Method m = Tess4JParser.class.getDeclaredMethod("doOCRWithTimeout",
                Tesseract.class, BufferedImage.class, ParseContext.class);
        m.setAccessible(true);
        return m;
    }

    private static Tess4JParser newParser() throws Exception {
        // Constructor calls initialize(), which gracefully catches UnsatisfiedLinkError
        // and disables the parser rather than throwing if the native library isn't on
        // this machine -- doOCRWithTimeout doesn't touch the pool, so this is safe
        // regardless of whether Tess4J is actually available here.
        return new Tess4JParser();
    }

    @Test
    public void testSlowOCRCallTimesOutAndCheckpoints() throws Exception {
        Tess4JParser parser = newParser();
        Method doOCRWithTimeout = doOCRWithTimeoutMethod();

        Tesseract slow = new Tesseract() {
            @Override
            public String doOCR(BufferedImage bi) throws TesseractException {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "should not be reached before the timeout fires";
            }
        };

        ParseContext context = new ParseContext();
        context.set(TimeoutLimits.class, new TimeoutLimits(500, 500));
        ParseTimeout parseTimeout = ParseTimeout.getOrCreate(context);
        long initialProgress = parseTimeout.getLastProgressMillis();

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);

        long start = System.currentTimeMillis();
        try {
            doOCRWithTimeout.invoke(parser, slow, image, context);
            fail("expected the 500ms budget to be exceeded by the 3s doOCR call");
        } catch (InvocationTargetException e) {
            assertInstanceOf(TikaTimeoutException.class, e.getCause(),
                    "expected TikaTimeoutException, got: " + e.getCause());
        }
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 2500,
                "must time out near the 500ms budget, not wait for the 3s doOCR call to " +
                        "finish naturally; took " + elapsed + "ms");
        assertTrue(parseTimeout.getLastProgressMillis() > initialProgress,
                "expected at least one checkpoint to have fired while waiting");
    }

    @Test
    public void testFastOCRCallReturnsNormally() throws Exception {
        Tess4JParser parser = newParser();
        Method doOCRWithTimeout = doOCRWithTimeoutMethod();

        Tesseract fast = new Tesseract() {
            @Override
            public String doOCR(BufferedImage bi) {
                return "fast OCR result";
            }
        };

        ParseContext context = new ParseContext();
        context.set(TimeoutLimits.class, new TimeoutLimits(60_000, 60_000));
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);

        Object result = doOCRWithTimeout.invoke(parser, fast, image, context);
        assertEquals("fast OCR result", result);
    }
}
