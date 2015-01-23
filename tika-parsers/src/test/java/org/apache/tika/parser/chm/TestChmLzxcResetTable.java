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

import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.chm.accessor.ChmDirectoryListingSet;
import org.apache.tika.parser.chm.accessor.ChmItsfHeader;
import org.apache.tika.parser.chm.accessor.ChmItspHeader;
import org.apache.tika.parser.chm.accessor.ChmLzxcControlData;
import org.apache.tika.parser.chm.accessor.ChmLzxcResetTable;
import org.apache.tika.parser.chm.assertion.ChmAssert;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.junit.Before;
import org.junit.Test;

public class TestChmLzxcResetTable {
    private ChmLzxcResetTable chmLzxcResetTable = null;

    @Before
    public void setUp() throws Exception {
        byte[] data = TestParameters.chmData;
        /* Creates and parses itsf header */
        ChmItsfHeader chmItsfHeader = new ChmItsfHeader();
        // chmItsfHeader.parse(Arrays.copyOfRange(data, 0,
        // ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsfHeader);
        chmItsfHeader.parse(ChmCommons.copyOfRange(data, 0,
                ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsfHeader);
        /* Creates and parses itsp block */
        ChmItspHeader chmItspHeader = new ChmItspHeader();
        // chmItspHeader.parse(Arrays.copyOfRange( data, (int)
        // chmItsfHeader.getDirOffset(),
        // (int) chmItsfHeader.getDirOffset()
        // + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
        chmItspHeader.parse(ChmCommons.copyOfRange(data,
                (int) chmItsfHeader.getDirOffset(),
                (int) chmItsfHeader.getDirOffset()
                        + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
        /* Creating instance of ChmDirListingContainer */
        ChmDirectoryListingSet chmDirListCont = new ChmDirectoryListingSet(
                data, chmItsfHeader, chmItspHeader);
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
        ChmLzxcControlData chmLzxcControlData = new ChmLzxcControlData();
        chmLzxcControlData.parse(dir_chunk, chmLzxcControlData);

        indexOfResetTable = chmDirListCont.getResetTableIndex();
        chmLzxcResetTable = new ChmLzxcResetTable();

        int startIndex = (int) chmDirListCont.getDataOffset()
                + chmDirListCont.getDirectoryListingEntryList()
                        .get(indexOfResetTable).getOffset();

        ChmAssert.assertCopyingDataIndex(startIndex, data.length);

        // dir_chunk = Arrays.copyOfRange(data, startIndex, startIndex
        // +
        // chmDirListCont.getDirectoryListingEntryList().get(indexOfResetTable).getLength());
        dir_chunk = ChmCommons.copyOfRange(
                data,
                startIndex,
                startIndex
                        + chmDirListCont.getDirectoryListingEntryList()
                                .get(indexOfResetTable).getLength());

        chmLzxcResetTable.parse(dir_chunk, chmLzxcResetTable);
    }

    @Test
    public void testGetBlockAddress() {
        assertEquals(TestParameters.VP_RESET_TABLE_BA,
                chmLzxcResetTable.getBlockAddress().length);
    }

    @Test
    public void testGetBlockCount() {
        assertEquals(TestParameters.VP_RESET_TABLE_BA,
                chmLzxcResetTable.getBlockCount());
    }

    @Test
    public void testGetBlockLen() {
        assertEquals(TestParameters.VP_RES_TBL_BLOCK_LENGTH,
                chmLzxcResetTable.getBlockLen());
    }

    @Test
    public void testGetCompressedLen() {
        assertEquals(TestParameters.VP_RES_TBL_COMPR_LENGTH,
                chmLzxcResetTable.getCompressedLen());
    }

    @Test
    public void testGetTableOffset() {
        assertEquals(TestParameters.VP_TBL_OFFSET,
                chmLzxcResetTable.getTableOffset());
    }

    @Test
    public void testGetUncompressedLen() {
        assertEquals(TestParameters.VP_RES_TBL_UNCOMP_LENGTH,
                chmLzxcResetTable.getUncompressedLen());
    }

    @Test
    public void testGetUnknown() {
        assertEquals(TestParameters.VP_RES_TBL_UNKNOWN,
                chmLzxcResetTable.getUnknown());
    }

    @Test
    public void testGetVersion() {
        assertEquals(TestParameters.VP_RES_TBL_VERSION,
                chmLzxcResetTable.getVersion());
    }

    @Test
    public void testToString() {
        assertTrue(chmLzxcResetTable.toString().length() > 0);
    }

    // TODO: add setters to be tested
}
