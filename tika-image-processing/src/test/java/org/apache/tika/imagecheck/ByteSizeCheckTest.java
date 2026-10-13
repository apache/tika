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
package org.apache.tika.imagecheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.ImageCheck;
import org.apache.tika.parser.enricher.ImageProbe;

public class ByteSizeCheckTest {

    private static ImageCheck.Verdict check(ByteSizeCheck check, int length) throws Exception {
        return check.check(new ImageProbe(TikaInputStream.get(new byte[length]), new Metadata()),
                new Metadata(), new ParseContext());
    }

    @Test
    public void testDefaultsAdmitEverything() throws Exception {
        assertTrue(check(new ByteSizeCheck(), 0).admitted());
        assertTrue(check(new ByteSizeCheck(), 10_000_000).admitted());
    }

    @Test
    public void testMinBytes() throws Exception {
        ByteSizeCheck check = new ByteSizeCheck();
        check.setMinBytes(100);
        ImageCheck.Verdict small = check(check, 99);
        assertEquals(false, small.admitted());
        assertEquals("file-too-small", small.reason());
        assertTrue(check(check, 100).admitted());
    }

    @Test
    public void testMaxBytes() throws Exception {
        ByteSizeCheck check = new ByteSizeCheck();
        check.setMaxBytes(1000);
        ImageCheck.Verdict large = check(check, 1001);
        assertEquals(false, large.admitted());
        assertEquals("file-too-large", large.reason());
        assertTrue(check(check, 1000).admitted());
    }

    @Test
    public void testAnUnknownLengthIsAdmitted() throws Exception {
        ByteSizeCheck check = new ByteSizeCheck();
        check.setMinBytes(100);
        check.setMaxBytes(200);
        ImageProbe probe = new ImageProbe(
                TikaInputStream.get(new ByteArrayInputStream(new byte[5])), new Metadata());
        assertTrue(check.check(probe, new Metadata(), new ParseContext()).admitted());
    }

    @Test
    public void testBadSettingsAreRefused() {
        ByteSizeCheck check = new ByteSizeCheck();
        assertThrows(IllegalArgumentException.class, () -> check.setMinBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> check.setMaxBytes(-1));
    }
}
