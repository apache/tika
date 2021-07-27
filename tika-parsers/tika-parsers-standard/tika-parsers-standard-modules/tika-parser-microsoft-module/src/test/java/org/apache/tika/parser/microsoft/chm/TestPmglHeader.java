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
package org.apache.tika.parser.microsoft.chm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestPmglHeader {
    ChmPmglHeader chmPmglHeader = null;

    @BeforeEach
    public void setUp() throws Exception {
        byte[] data = TestParameters.chmData;
        chmPmglHeader = new ChmPmglHeader();
        chmPmglHeader.parse(ChmCommons.copyOfRange(data, ChmConstants.START_PMGL,
                ChmConstants.START_PMGL + ChmConstants.CHM_PMGL_LEN + 10), chmPmglHeader);
    }

    @Test
    public void testToString() {
        assertTrue((chmPmglHeader != null) && chmPmglHeader.toString().length() > 0);
    }

    @Test
    public void testChmPmglHeaderGet() {
        assertEquals(TestParameters.VP_PMGL_SIGNATURE,
                new String(chmPmglHeader.getSignature(), UTF_8));
    }

    @Test
    public void testGetBlockNext() {
        assertEquals(TestParameters.VP_PMGL_BLOCK_NEXT, chmPmglHeader.getBlockNext());
    }

    @Test
    public void testGetBlockPrev() {
        assertEquals(TestParameters.VP_PMGL_BLOCK_PREV, chmPmglHeader.getBlockPrev());
    }

    @Test
    public void testGetFreeSpace() {
        assertEquals(TestParameters.VP_PMGL_FREE_SPACE, chmPmglHeader.getFreeSpace());
    }

    @Test
    public void testGetUnknown0008() {
        assertEquals(TestParameters.VP_PMGL_UNKNOWN_008, chmPmglHeader.getUnknown0008());
    }
}
