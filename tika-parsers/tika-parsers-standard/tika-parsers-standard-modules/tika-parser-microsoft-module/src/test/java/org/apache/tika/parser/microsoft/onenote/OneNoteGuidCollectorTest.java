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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class OneNoteGuidCollectorTest {

    @Test
    public void testCapsTotalGuidValuesAndWarnsOnce() {
        List<String> warnings = new ArrayList<>();
        OneNoteGuidCollector collector = new OneNoteGuidCollector(warnings::add);
        assertFalse(collector.isFull());
        collector.add(OneNoteGuidCollector.Category.PAGE, null);
        for (int i = 0; i < OneNoteGuidCollector.MAX_GUID_COUNT; i++) {
            collector.add(OneNoteGuidCollector.Category.SECTION, "guid-" + i);
        }
        collector.add(OneNoteGuidCollector.Category.SECTION, "guid-0");
        collector.add(OneNoteGuidCollector.Category.PAGE, "overflow-1");
        collector.add(OneNoteGuidCollector.Category.PAGE, "overflow-2");

        assertEquals(OneNoteGuidCollector.MAX_GUID_COUNT,
                collector.values(OneNoteGuidCollector.Category.SECTION).size());
        assertTrue(collector.values(OneNoteGuidCollector.Category.PAGE).isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(collector.isFull());
    }
}
