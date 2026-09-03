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
package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

public class PlaceholderStreamTest {

    @Test
    public void testPlaceholderDeclaresNoLength() throws Exception {
        try (TikaInputStream tis = TikaInputStream.getPlaceholder()) {
            assertFalse(tis.hasLength(), "a placeholder's size describes nothing");
            assertEquals(-1, tis.read(), "placeholder is empty");
        }
    }

    /** A real empty document is not a placeholder: zero is honest there. */
    @Test
    public void testGenuinelyEmptyStreamStillDeclaresZero() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            assertTrue(tis.hasLength());
            assertEquals(0, tis.getLength());
        }
    }

    /** Spooling must not turn the placeholder's absent length into a zero. */
    @Test
    public void testSpoolingKeepsLengthUnknown() throws Exception {
        try (TikaInputStream tis = TikaInputStream.getPlaceholder()) {
            assertEquals(0, Files.size(tis.getPath()));
            assertFalse(tis.hasLength());
        }
    }

    /** Measuring a placeholder must not cost a temp file: there is nothing to measure. */
    @Test
    public void testMeasuringCostsNoTempFile() throws Exception {
        try (TikaInputStream tis = TikaInputStream.getPlaceholder()) {
            assertEquals(-1, tis.getLength());
            assertFalse(tis.hasFile());
        }
    }
}
