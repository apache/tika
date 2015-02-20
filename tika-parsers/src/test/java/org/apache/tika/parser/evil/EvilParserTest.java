package org.apache.tika.parser.evil;

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
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import java.util.Date;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

public class EvilParserTest extends TikaTest {

    @Test
    public void testNothingBad() throws Exception {
        //For use cases that need to modify the mime types and potentially
        //pass a non-evil document through the EvilParser.

        Metadata m = new Metadata();
        //need to set resource name so that detector can work
        m.set(Metadata.RESOURCE_NAME_KEY, "nothing_bad.evil");
        String content = getXML("evil/nothing_bad.evil", m).xml;

        assertContains("Whan that Aprille", content);
        assertEvilParser(m);
    }

    @Test
    public void testNullPointer() throws Exception {
        Metadata m = new Metadata();
        //need to set resource name so that detector can work
        m.set(Metadata.RESOURCE_NAME_KEY, "null_pointer.evil");
        boolean ex = false;
        try {
            String content = getXML("evil/null_pointer.evil", m).xml;
            //runtime exceptions are wrapped in a TikaException by CompositeParser
        } catch (TikaException e) {
            if (e.getCause() != null && e.getCause() instanceof java.lang.NullPointerException) {
                String msg = e.getCause().getMessage();
                assertEquals("null pointer message", msg);
                ex = true;
            }
        }
        assertTrue("NullPointerException", ex);
    }

    @Test
    public void testNullPointerNoMsg() throws Exception {
        Metadata m = new Metadata();
        //need to set resource name so that detector can work
        m.set(Metadata.RESOURCE_NAME_KEY, "null_pointer_no_msg.evil");
        boolean ex = false;
        try {
            String content = getXML("evil/null_pointer_no_msg.evil", m).xml;
            //runtime exceptions are wrapped in a TikaException by CompositeParser
        } catch (TikaException e) {
            if (e.getCause() != null && e.getCause() instanceof java.lang.NullPointerException) {
                String msg = e.getCause().getMessage();
                assertNull(msg);
                ex = true;
            }
        }
        assertTrue("NullPointerException with no msg", ex);
    }


    @Test
    public void testSleep() throws Exception {
        long start = new Date().getTime();
        Metadata m = new Metadata();
        //need to set resource name so that detector can work
        m.set(Metadata.RESOURCE_NAME_KEY, "sleep.evil");
        String content = getXML("evil/sleep.evil", m).xml;
        assertEvilParser(m);
        long elapsed = new Date().getTime()-start;
        //should sleep for at least 3000
        boolean enoughTimeHasElapsed = elapsed > 2000;
        assertTrue("enough time has not elapsed: "+elapsed, enoughTimeHasElapsed);
    }

    @Test
    public void testHeavyHang() throws Exception {
        long start = new Date().getTime();
        Metadata m = new Metadata();
        //need to set resource name so that detector can work
        m.set(Metadata.RESOURCE_NAME_KEY, "heavy_hang.evil");
        String content = getXML("evil/heavy_hang.evil", m).xml;
        assertEvilParser(m);
        long elapsed = new Date().getTime()-start;
        //should sleep for at least 3000
        boolean enoughTimeHasElapsed = elapsed > 2000;
        assertTrue("enough time has elapsed: "+elapsed, enoughTimeHasElapsed);
    }

    @Test
    public void testFakeOOM() throws Exception {
        Metadata m = new Metadata();
        //need to set resource name so that detector can work
        m.set(Metadata.RESOURCE_NAME_KEY, "fake_oom.evil");
        boolean ex = false;
        try {
            String content = getXML("evil/fake_oom.evil", m).xml;
        } catch (OutOfMemoryError e) {
            assertEquals("fake oom", e.getMessage());
            ex = true;
        }
        assertTrue("Fake oom", ex);
    }

    @Test
    public void testRealOOM() throws Exception {
        //this doesn't actually test real oom, but
        //only relies on the message

        Metadata m = new Metadata();
        //need to set resource name so that detector can work
        m.set(Metadata.RESOURCE_NAME_KEY, "real_oom.evil");
        boolean ex = false;
        try {
            String content = getXML("evil/real_oom.evil", m).xml;
        } catch (OutOfMemoryError e) {
            assertContains("Java heap space", e.getMessage());
            ex = true;
        }
        assertTrue("Real oom", ex);
    }

    private void assertEvilParser(Metadata m) {
        String[] parsers = m.getValues("X-Parsed-By");
        //make sure that it was actually parsed by evil.
        boolean parsedByEvil = false;
        for (String parser : parsers) {
            if (parser.equals("org.apache.tika.parser.evil.EvilParser")) {
                parsedByEvil = true;
                break;
            }
        }
        assertTrue("evil parser should have been called", parsedByEvil);
    }
}
