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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests all public functions of ChmItsfHeader
 */
public class TestChmItsfHeader {
    private ChmItsfHeader chmItsfHeader = null;

    @Before
    public void setUp() throws Exception {
        chmItsfHeader = new ChmItsfHeader();
        byte[] data = TestParameters.chmData;
        // chmItsfHeader.parse(Arrays.copyOfRange(data, 0,
        // ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsfHeader);
        chmItsfHeader.parse(ChmCommons.copyOfRange(data, 0, ChmConstants.CHM_ITSF_V3_LEN - 1),
                chmItsfHeader);
    }

    @Test
    public void getDataOffset() {
        assertEquals(TestParameters.VP_DATA_OFFSET_LENGTH, chmItsfHeader.getDataOffset());
    }

    @Test
    public void getDir_uuid() {
        assertNotNull(chmItsfHeader.getDir_uuid());
    }

    @Test
    public void getDirLen() {
        assertEquals(TestParameters.VP_DIRECTORY_LENGTH, chmItsfHeader.getDirLen());
    }

    @Test
    public void getDirOffset() {
        assertEquals(TestParameters.VP_DIRECTORY_OFFSET, chmItsfHeader.getDirOffset());
    }

    @Test
    public void getHeaderLen() {
        assertEquals(TestParameters.VP_ITSF_HEADER_LENGTH, chmItsfHeader.getHeaderLen());
    }

    @Test
    public void getLangId() {
        assertEquals(TestParameters.VP_LANGUAGE_ID, chmItsfHeader.getLangId());
    }

    @Test
    public void getLastModified() {
        assertEquals(TestParameters.VP_LAST_MODIFIED, chmItsfHeader.getLastModified());
    }

    @Test
    public void getUnknown_000c() {
        assertEquals(TestParameters.VP_UNKNOWN_000C, chmItsfHeader.getUnknown_000c());
    }

    @Test
    public void getUnknownLen() {
        assertEquals(TestParameters.VP_UNKNOWN_LEN, chmItsfHeader.getUnknownLen());
    }

    @Test
    public void getUnknownOffset() {
        assertEquals(TestParameters.VP_UNKNOWN_OFFSET, chmItsfHeader.getUnknownOffset());
    }

    @Test
    public void getVersion() {
        assertEquals(TestParameters.VP_VERSION, chmItsfHeader.getVersion());
    }

    @Test
    public void testToString() {
        assertTrue(chmItsfHeader.toString().contains(TestParameters.VP_ISTF_SIGNATURE));
    }

    @After
    public void tearDown() throws Exception {
        chmItsfHeader = null;
    }
}
