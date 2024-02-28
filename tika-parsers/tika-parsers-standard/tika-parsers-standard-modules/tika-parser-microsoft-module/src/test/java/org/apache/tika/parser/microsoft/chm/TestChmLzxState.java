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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;

public class TestChmLzxState {
    private ChmLzxState chmLzxState;
    private int windowSize;

    @BeforeEach
    public void setUp() throws Exception {
        byte[] data = TestParameters.chmData;

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
        ChmDirectoryListingSet chmDirListCont =
                new ChmDirectoryListingSet(data, chmItsHeader, chmItspHeader);
        int indexOfControlData = ChmCommons
                .indexOfDataSpaceStorageElement(chmDirListCont.getDirectoryListingEntryList(),
                        ChmConstants.CONTROL_DATA);

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

        ChmLzxcControlData clcd = new ChmLzxcControlData();
        clcd.parse(dir_chunk, clcd);
        windowSize = (int) clcd.getWindowSize();
    }

    @Test
    public void testChmLzxStateConstructor() throws TikaException {
        chmLzxState = new ChmLzxState(windowSize);
        assertNotNull(chmLzxState);
    }

    @Test
    public void testToString() throws TikaException {
        if (chmLzxState == null) {
            testChmLzxStateConstructor();
        }
        assertTrue(chmLzxState.toString().length() > 20);
    }

    // TODO add more tests

}
