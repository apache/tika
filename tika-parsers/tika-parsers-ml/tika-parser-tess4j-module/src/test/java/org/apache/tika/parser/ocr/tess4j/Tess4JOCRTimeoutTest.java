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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * TIKA-4813 follow-up: {@code Tess4JParser#doOCRWithTimeout} runs the blocking, native
 * {@code Tesseract.doOCR} call on a background thread and checkpoints {@link ParseTimeout}
 * while waiting, so a slow OCR call is bounded by the per-call timeout clipped to the
 * task's remaining budget instead of running unchecked until the server's stall watchdog
 * kills the whole forked JVM. Tested
 * via reflection against the private method directly, using a {@link Tesseract} subclass
 * that overrides the {@code doOCR(BufferedImage)} default method -- no native Tesseract
 * library is exercised, so this doesn't need {@code tess4jAvailable} gating.
 */
public class Tess4JOCRTimeoutTest {

    private static Method doOCRWithTimeoutMethod() throws NoSuchMethodException {
        Method m = Tess4JParser.class.getDeclaredMethod("doOCRWithTimeout",
                Tesseract.class, BufferedImage.class, long.class, ParseContext.class);
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

        // Large task budget, small per-call timeout: the per-call value must bound the
        // OCR call on its own, not just the task's remaining total.
        ParseContext context = new ParseContext();
        context.set(TimeoutLimits.class, new TimeoutLimits(60_000, 60_000));
        ParseTimeout parseTimeout = ParseTimeout.getOrCreate(context);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);

        long start = System.currentTimeMillis();
        try {
            doOCRWithTimeout.invoke(parser, slow, image, 500L, context);
            fail("expected the 500ms budget to be exceeded by the 3s doOCR call");
        } catch (InvocationTargetException e) {
            assertInstanceOf(TikaTimeoutException.class, e.getCause(),
                    "expected TikaTimeoutException, got: " + e.getCause());
        }
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 2500,
                "must time out near the 500ms budget, not wait for the 3s doOCR call to " +
                        "finish naturally; took " + elapsed + "ms");
        // without a mid-wait checkpoint, millisSinceLastProgress() would be >= elapsed
        assertTrue(parseTimeout.millisSinceLastProgress() < elapsed,
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

        Object result = doOCRWithTimeout.invoke(parser, fast, image, 60_000L, context);
        assertEquals("fast OCR result", result);
    }

    private static void setPool(Tess4JParser parser, BlockingQueue<Tesseract> pool) throws Exception {
        Field poolField = Tess4JParser.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        poolField.set(parser, pool);
    }

    private static void setInitialized(Tess4JParser parser, boolean initialized) throws Exception {
        Field initializedField = Tess4JParser.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        initializedField.set(parser, initialized);
    }

    /**
     * Pins the leak fix: {@code tesseractStillBusy} used to start {@code true}, so an
     * exception between borrow and doOCR (e.g. an ImageIO decode failure) leaked the
     * pooled instance forever. Seeds a fake instance directly into the pool with
     * {@code initialized=true}, so no native Tesseract library is needed.
     */
    @Test
    public void testImageIOFailureDoesNotShrinkThePool() throws Exception {
        Tess4JParser parser = newParser();
        setInitialized(parser, true);

        Tesseract fake = new Tesseract() {
            @Override
            public String doOCR(BufferedImage bi) {
                throw new AssertionError("doOCR must not be reached -- the image never decodes");
            }
        };
        BlockingQueue<Tesseract> pool = new ArrayBlockingQueue<>(1);
        pool.add(fake);
        setPool(parser, pool);

        // Valid PNG signature + truncated IHDR: enough for the PNG reader to commit and
        // then throw -- unrecognizable bytes would just make ImageIO return null instead.
        byte[] truncatedPng = {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'          // IHDR chunk header, then EOF
        };

        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        boolean threw = false;
        try (TikaInputStream tis = TikaInputStream.get(truncatedPng)) {
            parser.parse(tis, handler, metadata, context);
        } catch (IOException e) {
            threw = true;
        }
        assertTrue(threw, "expected ImageIO to throw decoding the truncated PNG");

        Tesseract returned = pool.poll(1, TimeUnit.SECONDS);
        assertNotNull(returned,
                "borrow must succeed immediately -- the instance was never handed to doOCR, " +
                        "so it must not have been withheld from the pool");
        assertSame(fake, returned);
    }

    /**
     * Pins the timeout-path handoff: once the waiter gives up, the worker thread itself
     * must return the instance to the pool when the slow {@code doOCR} finally completes
     * (it used to leak forever).
     */
    @Test
    public void testInstanceReturnedToPoolAfterSlowOCREventuallyCompletes() throws Exception {
        Tess4JParser parser = newParser();
        Method doOCRWithTimeout = doOCRWithTimeoutMethod();

        CountDownLatch releaseWorker = new CountDownLatch(1);
        Tesseract slow = new Tesseract() {
            @Override
            public String doOCR(BufferedImage bi) throws TesseractException {
                try {
                    assertTrue(releaseWorker.await(10, TimeUnit.SECONDS),
                            "test bug: releaseWorker was never counted down");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "finished after the waiter already gave up";
            }
        };

        BlockingQueue<Tesseract> pool = new ArrayBlockingQueue<>(1);
        setPool(parser, pool);

        ParseContext context = new ParseContext();
        context.set(TimeoutLimits.class, new TimeoutLimits(300, 300));
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);

        try {
            // requested exceeds the 300ms task budget -- budgetFor clips to remaining
            doOCRWithTimeout.invoke(parser, slow, image, 60_000L, context);
            fail("expected the 300ms budget to be exceeded by the still-blocked doOCR call");
        } catch (InvocationTargetException e) {
            assertInstanceOf(TikaTimeoutException.class, e.getCause(),
                    "expected TikaTimeoutException, got: " + e.getCause());
        }

        // The waiter gave up, but the worker is still genuinely running (blocked on the
        // latch) -- must not have been returned yet.
        assertEquals(0, pool.size(),
                "must not be returned while the OCR call is genuinely still in flight");

        releaseWorker.countDown();

        Tesseract returned = pool.poll(5, TimeUnit.SECONDS);
        assertNotNull(returned,
                "the worker thread must return the instance itself once doOCR completes, " +
                        "since the waiter already abandoned it");
        assertSame(slow, returned);
    }
}
