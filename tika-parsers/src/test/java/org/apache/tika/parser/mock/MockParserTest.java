package org.apache.tika.parser.mock;

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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Date;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.junit.Test;

public class MockParserTest extends TikaTest {
    private final static String M = "/test-documents/mock/";
    private final static Parser PARSER = new AutoDetectParser();

    @Override
    public XMLResult getXML(String path, Metadata m) throws Exception {
        //note that this is specific to MockParserTest with addition of M to the path!
        InputStream is = getResourceAsStream(M+path);
        try {
            return super.getXML(is, PARSER, m);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    @Test
    public void testExample() throws Exception {
        Metadata m = new Metadata();
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream outBos = new ByteArrayOutputStream();
        ByteArrayOutputStream errBos = new ByteArrayOutputStream();
        PrintStream tmpOut = new PrintStream(outBos, true, IOUtils.UTF_8.toString());
        PrintStream tmpErr = new PrintStream(errBos, true, IOUtils.UTF_8.toString());
        System.setOut(tmpOut);
        System.setErr(tmpErr);
        try {
            assertThrowable("example.xml", m, IOException.class, "not another IOException");
            assertMockParser(m);
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        String outString = new String(outBos.toByteArray(), IOUtils.UTF_8);
        assertContains("writing to System.out", outString);

        String errString = new String(errBos.toByteArray(), IOUtils.UTF_8);
        assertContains("writing to System.err", errString);

    }

    @Test
    public void testNothingBad() throws Exception {
        Metadata m = new Metadata();
        String content = getXML("nothing_bad.xml", m).xml;
        assertEquals("Geoffrey Chaucer", m.get("author"));
        assertContains("<p>And bathed every veyne in swich licour,</p>", content);
        assertMockParser(m);
    }

    @Test
    public void testNullPointer() throws Exception {
        Metadata m = new Metadata();
        assertThrowable("null_pointer.xml", m, NullPointerException.class, "another null pointer exception");
        assertMockParser(m);
    }

    @Test
    public void testNullPointerNoMsg() throws Exception {
        Metadata m = new Metadata();
        assertThrowable("null_pointer_no_msg.xml", m, NullPointerException.class, null);
        assertMockParser(m);
    }


    @Test
    public void testSleep() throws Exception {
        long start = new Date().getTime();
        Metadata m = new Metadata();
        String content = getXML("sleep.xml", m).xml;
        assertMockParser(m);
        long elapsed = new Date().getTime()-start;
        //should sleep for at least 3000
        boolean enoughTimeHasElapsed = elapsed > 2000;
        assertTrue("not enough time has not elapsed: "+elapsed, enoughTimeHasElapsed);
        assertMockParser(m);
    }

    @Test
    public void testHeavyHang() throws Exception {
        long start = new Date().getTime();
        Metadata m = new Metadata();

        String content = getXML("heavy_hang.xml", m).xml;
        assertMockParser(m);
        long elapsed = new Date().getTime()-start;
        //should sleep for at least 3000
        boolean enoughTimeHasElapsed = elapsed > 2000;
        assertTrue("not enough time has elapsed: "+elapsed, enoughTimeHasElapsed);
        assertMockParser(m);
    }

    @Test
    public void testFakeOOM() throws Exception {
        Metadata m = new Metadata();
        assertThrowable("fake_oom.xml", m, OutOfMemoryError.class, "not another oom");
        assertMockParser(m);
    }

    @Test
    public void testRealOOM() throws Exception {
        //Note: we're not actually testing the diff between fake and real oom
        //i.e. by creating child process and setting different -Xmx or
        //memory profiling.
        Metadata m = new Metadata();
        assertThrowable("real_oom.xml", m, OutOfMemoryError.class, "Java heap space");
        assertMockParser(m);
    }

    @Test
    public void testInterruptibleSleep() {
        //Without static initialization of the parser, it can take ~1 second after t.start()
        //before the parser actually calls parse.  This is
        //just the time it takes to instantiate and call AutoDetectParser, do the detection, etc.
        //This is not thread creation overhead.
        ParserRunnable r = new ParserRunnable("sleep_interruptible.xml");
        Thread t = new Thread(r);
        t.start();
        long start = new Date().getTime();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //swallow
        }

        t.interrupt();

        try {
            t.join(10000);
        } catch (InterruptedException e) {
            //swallow
        }
        long elapsed = new Date().getTime()-start;
        boolean shortEnough = elapsed < 2000;//the xml file specifies 3000
        assertTrue("elapsed (" + elapsed + " millis) was not short enough", shortEnough);
    }

    @Test
    public void testNonInterruptibleSleep() {
        ParserRunnable r = new ParserRunnable("sleep_not_interruptible.xml");
        Thread t = new Thread(r);
        t.start();
        long start = new Date().getTime();
        try {
            //make sure that the thread has actually started
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //swallow
        }
        t.interrupt();
        try {
            t.join(20000);
        } catch (InterruptedException e) {
            //swallow
        }
        long elapsed = new Date().getTime()-start;
        boolean longEnough = elapsed > 3000;//the xml file specifies 3000, this sleeps 1000
        assertTrue("elapsed ("+elapsed+" millis) was not long enough", longEnough);
    }

    private class ParserRunnable implements Runnable {
        private final String path;
        ParserRunnable(String path) {
            this.path = path;
        }
        @Override
        public void run() {
            Metadata m = new Metadata();
            try {
                getXML(path, m);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                assertMockParser(m);
            }
        }
    }

    private void assertThrowable(String path, Metadata m, Class<? extends Throwable> expected, String message) {

        try {
            getXML(path, m);
        } catch (Throwable t) {
            //if this is a throwable wrapped in a TikaException, use the cause
            if (t instanceof TikaException && t.getCause() != null) {
                t = t.getCause();
            }
            if (! (t.getClass().isAssignableFrom(expected))){
                fail(t.getClass() +" is not assignable from "+expected);
            }
            if (message != null) {
                assertEquals(message, t.getMessage());
            }
        }
    }

    private void assertMockParser(Metadata m) {
        String[] parsers = m.getValues("X-Parsed-By");
        //make sure that it was actually parsed by mock.
        boolean parsedByMock = false;
        for (String parser : parsers) {
            if (parser.equals("org.apache.tika.parser.mock.MockParser")) {
                parsedByMock = true;
                break;
            }
        }
        assertTrue("mock parser should have been called", parsedByMock);
    }
}
