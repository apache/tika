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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.parser.microsoft.AbstractListManager.LevelTuple;
import org.apache.tika.parser.microsoft.AbstractListManager.ParagraphLevelCounter;

public class ParagraphLevelCounterTest {

    // A paragraph ilvl beyond the defined levels must yield "" instead of growing `counts`
    // to that many entries (a large value would OOM). The timeout guards the unbounded loop.
    @Test
    @Timeout(10)
    public void testOutOfRangeLevelReturnsEmpty() {
        LevelTuple[] levels = {new LevelTuple("%1.")};
        ParagraphLevelCounter counter = new ParagraphLevelCounter(levels);
        assertEquals("", counter.incrementLevel(100_000_000, null));
        assertEquals("", counter.incrementLevel(-1, null));
    }

    // A normal in-range level still formats.
    @Test
    public void testInRangeLevelStillFormats() {
        LevelTuple[] levels = {new LevelTuple(1, -1, "%1.", "decimal", false)};
        ParagraphLevelCounter counter = new ParagraphLevelCounter(levels);
        assertEquals("1. ", counter.incrementLevel(0, null));
    }
}
