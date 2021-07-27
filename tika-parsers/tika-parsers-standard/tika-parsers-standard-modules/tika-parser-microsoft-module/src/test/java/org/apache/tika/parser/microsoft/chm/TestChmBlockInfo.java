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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests major functionality of ChmBlockInfo
 */
public class TestChmBlockInfo {
    private byte[] data;
    private ChmBlockInfo chmBlockInfo;
    private ChmDirectoryListingSet chmDirListCont = null;
    private ChmLzxcResetTable clrt = null;
    private ChmLzxcControlData chmLzxcControlData = null;

    @BeforeEach
    public void setUp() throws Exception {
        data = TestParameters.chmData;
        /* Creates and parses itsf header */
        ChmItsfHeader chmItsHeader = new ChmItsfHeader();
        // chmItsHeader.parse(Arrays.copyOfRange(data, 0,
        // ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsHeader);
        chmItsHeader.parse(ChmCommons.copyOfRange(data, 0, ChmConstants.CHM_ITSF_V3_LEN - 1),
                chmItsHeader);
        /* Creates and parses itsp block */
        ChmItspHeader chmItspHeader = new ChmItspHeader();
        // chmItspHeader.parse(Arrays.copyOfRange( data, (int)
        // chmItsHeader.getDirOffset(),
        // (int) chmItsHeader.getDirOffset()
        // + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
        chmItspHeader.parse(ChmCommons.copyOfRange(data, (int) chmItsHeader.getDirOffset(),
                (int) chmItsHeader.getDirOffset() + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
        /* Creating instance of ChmDirListingContainer */
        chmDirListCont = new ChmDirectoryListingSet(data, chmItsHeader, chmItspHeader);
        int indexOfControlData = chmDirListCont.getControlDataIndex();

        int indexOfResetTable =
                ChmCommons.indexOfResetTableBlock(data, ChmConstants.LZXC.getBytes(UTF_8));
        byte[] dir_chunk = null;
        if (indexOfResetTable > 0) {
            // dir_chunk = Arrays.copyOfRange( data, indexOfResetTable,
            // indexOfResetTable
            // +
            // chmDirListCont.getDirectoryListingEntryList().get(indexOfControlData).getLength());
            dir_chunk = ChmCommons.copyOfRange(data, indexOfResetTable, indexOfResetTable +
                    chmDirListCont.getDirectoryListingEntryList().get(indexOfControlData)
                            .getLength());
        }

        /* Creates and parses control block */
        chmLzxcControlData = new ChmLzxcControlData();
        chmLzxcControlData.parse(dir_chunk, chmLzxcControlData);

        int indexOfFeList = chmDirListCont.getResetTableIndex();
        int startIndex = (int) chmDirListCont.getDataOffset() +
                chmDirListCont.getDirectoryListingEntryList().get(indexOfFeList).getOffset();
        // dir_chunk = Arrays.copyOfRange(data, startIndex , startIndex +
        // chmDirListCont.getDirectoryListingEntryList().get(indexOfFeList).getLength());
        dir_chunk = ChmCommons.copyOfRange(data, startIndex, startIndex +
                chmDirListCont.getDirectoryListingEntryList().get(indexOfFeList).getLength());
        clrt = new ChmLzxcResetTable();
        clrt.parse(dir_chunk, clrt);
    }

    @Test
    public void testToString() throws ChmParsingException {
        if (chmBlockInfo == null) {
            testGetChmBlockInfo();
        }
        assertTrue(chmBlockInfo.toString().length() > 0);
    }

    @Test
    public void testGetChmBlockInfo() throws ChmParsingException {
        for (DirectoryListingEntry directoryListingEntry : chmDirListCont
                .getDirectoryListingEntryList()) {
            chmBlockInfo = ChmBlockInfo
                    .getChmBlockInfoInstance(directoryListingEntry, (int) clrt.getBlockLen(),
                            chmLzxcControlData);
            // Assert.assertTrue(!directoryListingEntry.getName().isEmpty() &&
            // chmBlockInfo.toString() != null);
            assertTrue(!ChmCommons.isEmpty(directoryListingEntry.getName()) &&
                    chmBlockInfo.toString() != null);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        data = null;
        chmBlockInfo = null;
    }
}
