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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class OneNoteTextTest {

    @Test
    public void testClean() {
        assertEquals("Untitled picture.png", OneNoteText.clean("Untitled picture.png\u0000"));
        assertEquals("Answer:\n\n", OneNoteText.clean("Answer:\u000B\u000B"));
        assertEquals("a￼b", OneNoteText.clean("a￼b"));
        assertNull(OneNoteText.clean("￼￼"));
        assertNull(OneNoteText.clean("\u0000"));
        assertNull(OneNoteText.clean("  \u000B"));
        assertNull(OneNoteText.clean(null));
    }

    @Test
    public void testExtendedAscii() {
        assertEquals("Übung Thévenin ",
                new String(new byte[]{(byte) 0xDC, 'b', 'u', 'n', 'g', ' ', 'T', 'h', (byte) 0xE9,
                        'v', 'e', 'n', 'i', 'n', (byte) 0xA0}, OneNoteText.EXTENDED_ASCII));
    }
}
