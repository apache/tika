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
package org.apache.tika.parser.mock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class MockParserTest extends TikaTest {

    @Test
    public void testFakeload() throws Exception {
        //just make sure there aren't any exceptions
        getRecursiveMetadata("mock_fakeload.xml");
    }

    @Test
    public void testTimes() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("mock_times.xml");
        assertContainsCount("hello",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT), 30);
    }

    @Test
    public void testHangInterruptible() throws Exception {
        Thread t = startHang("true", 10_000);
        t.interrupt();
        t.join(2_000);
        assertFalse(t.isAlive(), "interruptible hang should end on interrupt");
    }

    /** Timeout tests rely on this: a hang that honoured interrupts would end without the fork being killed. */
    @Test
    public void testHangNotInterruptible() throws Exception {
        Thread t = startHang("false", 600);
        t.interrupt();
        t.join(300);
        assertTrue(t.isAlive(), "non-interruptible hang must outlive the interrupt");
        t.join(5_000);
        assertFalse(t.isAlive());
    }

    /** Pins pulse_millis being read from its own attribute: if it fell back to millis, no check would run for 10 s. */
    @Test
    public void testHeavyHangInterruptible() throws Exception {
        Thread t = startParser("<hang millis=\"10000\" pulse_millis=\"10\" heavy=\"true\" interruptible=\"true\"/>");
        Thread.sleep(50);
        t.interrupt();
        t.join(2_000);
        assertFalse(t.isAlive(), "heavy hang should notice the interrupt within a pulse");
    }

    /** Returns once the parser thread is inside the hang's sleep. */
    private Thread startHang(String interruptible, long millis) throws Exception {
        Thread t = startParser("<hang millis=\"" + millis + "\" interruptible=\"" + interruptible + "\"/>");
        long deadline = System.currentTimeMillis() + 10_000;
        while (t.getState() != Thread.State.TIMED_WAITING) {
            assertTrue(System.currentTimeMillis() < deadline, "hang never started sleeping");
            Thread.sleep(10);
        }
        return t;
    }

    private Thread startParser(String action) {
        byte[] xml = ("<mock>" + action + "</mock>").getBytes(UTF_8);
        Thread t = new Thread(() -> {
            try (TikaInputStream tis = TikaInputStream.get(xml)) {
                new MockParser().parse(tis, new DefaultHandler(), new Metadata(), new ParseContext());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        return t;
    }
}
