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
package org.apache.tika.parser.chm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.tika.parser.chm.accessor.ChmPmgiHeader;
import org.junit.Before;
import org.junit.Test;

public class TestPmgiHeader {
    ChmPmgiHeader chmPmgiHeader = null;

    @Before
    public void setUp() throws Exception {
        byte[] data = TestParameters.chmData;
        chmPmgiHeader = new ChmPmgiHeader();
        chmPmgiHeader.parse(data, chmPmgiHeader);
    }

    @Test
    public void testToString() {
        assertTrue((chmPmgiHeader != null) && (chmPmgiHeader.toString().length() > 0));
    }

    @Test
    public void testGetFreeSpace() {
        assertEquals(TestParameters.VP_PMGI_FREE_SPACE, chmPmgiHeader.getFreeSpace());
    }
}
