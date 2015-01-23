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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.chm.accessor.ChmDirectoryListingSet;
import org.apache.tika.parser.chm.accessor.ChmItsfHeader;
import org.apache.tika.parser.chm.accessor.ChmItspHeader;
import org.apache.tika.parser.chm.accessor.ChmLzxcControlData;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests all public methods of ChmLzxcControlData block
 */
public class TestChmLzxcControlData {
    private ChmLzxcControlData chmLzxcControlData = null;

    @Before
    public void setUp() throws Exception {
        byte[] data = TestParameters.chmData;
        /* Creates and parses itsf header */
        ChmItsfHeader chmItsHeader = new ChmItsfHeader();
        // chmItsHeader.parse(Arrays.copyOfRange(data, 0,
        // ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsHeader);
        chmItsHeader.parse(ChmCommons.copyOfRange(data, 0,
                ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsHeader);
        /* Creates and parses itsp block */
        ChmItspHeader chmItspHeader = new ChmItspHeader();
        // chmItspHeader.parse(Arrays.copyOfRange( data, (int)
        // chmItsHeader.getDirOffset(),
        // (int) chmItsHeader.getDirOffset()
        // + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
        chmItspHeader.parse(ChmCommons.copyOfRange(data,
                (int) chmItsHeader.getDirOffset(),
                (int) chmItsHeader.getDirOffset()
                        + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
        /* Creating instance of ChmDirListingContainer */
        ChmDirectoryListingSet chmDirListCont = new ChmDirectoryListingSet(
                data, chmItsHeader, chmItspHeader);
        int indexOfControlData = chmDirListCont.getControlDataIndex();

        int indexOfResetTable = ChmCommons.indexOfResetTableBlock(data,
                ChmConstants.LZXC.getBytes(IOUtils.UTF_8));
        byte[] dir_chunk = null;
        if (indexOfResetTable > 0) {
            // dir_chunk = Arrays.copyOfRange( data, indexOfResetTable,
            // indexOfResetTable
            // +
            // chmDirListCont.getDirectoryListingEntryList().get(indexOfControlData).getLength());
            dir_chunk = ChmCommons.copyOfRange(data, indexOfResetTable,
                    indexOfResetTable
                            + chmDirListCont.getDirectoryListingEntryList()
                                    .get(indexOfControlData).getLength());
        }

        /* Creates and parses control block */
        chmLzxcControlData = new ChmLzxcControlData();
        chmLzxcControlData.parse(dir_chunk, chmLzxcControlData);

    }

    @Test
    public void testConstructorNotNull() {
        assertNotNull(chmLzxcControlData);
    }

    @Test
    public void testGetResetInterval() {
        assertEquals(TestParameters.VP_RESET_INTERVAL,
                chmLzxcControlData.getResetInterval());
    }

    @Test
    public void testGetSize() {
        assertEquals(TestParameters.VP_CONTROL_DATA_SIZE,
                chmLzxcControlData.getSize());
    }

    @Test
    public void testGetUnknown_18() {
        assertEquals(TestParameters.VP_UNKNOWN_18,
                chmLzxcControlData.getUnknown_18());
    }

    @Test
    public void testGetVersion() {
        assertEquals(TestParameters.VP_CONTROL_DATA_VERSION,
                chmLzxcControlData.getVersion());
    }

    @Test
    public void testGetWindowSize() {
        assertEquals(TestParameters.VP_WINDOW_SIZE,
                chmLzxcControlData.getWindowSize());
    }

    @Test
    public void testGetWindowsPerReset() {
        assertEquals(TestParameters.VP_WINDOWS_PER_RESET,
                chmLzxcControlData.getWindowsPerReset());
    }

    @Test
    public void testGetToString() {
        assertTrue(chmLzxcControlData.toString().contains(
                TestParameters.VP_CONTROL_DATA_SIGNATURE));
    }

    @Test
    public void testGetSignature() {
        assertEquals(
                TestParameters.VP_CONTROL_DATA_SIGNATURE.getBytes(IOUtils.UTF_8).length,
                chmLzxcControlData.getSignature().length);
    }

    @Test
    public void testGetSignaure() {
        assertEquals(
                TestParameters.VP_CONTROL_DATA_SIGNATURE.getBytes(IOUtils.UTF_8).length,
                chmLzxcControlData.getSignature().length);
    }

}
