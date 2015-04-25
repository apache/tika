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
import org.apache.tika.parser.chm.accessor.ChmItsfHeader;
import org.apache.tika.parser.chm.accessor.ChmItspHeader;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests all public methods of the ChmItspHeader
 * 
 */
public class TestChmItspHeader {
    private ChmItspHeader chmItspHeader = null;

    @Before
    public void setUp() throws Exception {
        byte[] data = TestParameters.chmData;

        ChmItsfHeader chmItsfHeader = new ChmItsfHeader();
        // chmItsfHeader.parse(Arrays.copyOfRange(data, 0,
        // ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsfHeader);
        chmItsfHeader.parse(ChmCommons.copyOfRange(data, 0,
                ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsfHeader);

        chmItspHeader = new ChmItspHeader();
        // chmItspHeader.parse(Arrays.copyOfRange( data, (int)
        // chmItsfHeader.getDirOffset(),
        // (int) chmItsfHeader.getDirOffset()
        // + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
        chmItspHeader.parse(ChmCommons.copyOfRange(data,
                (int) chmItsfHeader.getDirOffset(),
                (int) chmItsfHeader.getDirOffset()
                        + ChmConstants.CHM_ITSP_V1_LEN), chmItspHeader);
    }

    @Test
    public void testGetBlock_len() {
        assertEquals(TestParameters.VP_BLOCK_LENGTH,
                chmItspHeader.getBlock_len());
    }

    @Test
    public void testGetBlockidx_intvl() {
        assertEquals(TestParameters.VP_BLOCK_INDEX_INTERVAL,
                chmItspHeader.getBlockidx_intvl());
    }

    @Test
    public void testGetHeader_len() {
        assertEquals(TestParameters.VP_ITSP_HEADER_LENGTH,
                chmItspHeader.getHeader_len());
    }

    @Test
    public void testGetIndex_depth() {
        assertEquals(TestParameters.VP_INDEX_DEPTH,
                chmItspHeader.getIndex_depth());
    }

    @Test
    public void testGetIndex_head() {
        assertEquals(TestParameters.VP_INDEX_HEAD,
                chmItspHeader.getIndex_head());
    }

    @Test
    public void testGetIndex_root() {
        assertEquals(TestParameters.VP_INDEX_ROOT,
                chmItspHeader.getIndex_root());
    }

    @Test
    public void testGetLang_id() {
        assertEquals(TestParameters.VP_LANGUAGE_ID,
                chmItspHeader.getLang_id());
    }

    @Test
    public void testGetNum_blocks() {
        assertEquals(TestParameters.VP_UNKNOWN_NUM_BLOCKS,
                chmItspHeader.getNum_blocks());
    }

    @Test
    public void testGetUnknown_000c() {
        assertEquals(TestParameters.VP_ITSP_UNKNOWN_000C,
                chmItspHeader.getUnknown_000c());
    }

    @Test
    public void testGetUnknown_0024() {
        assertEquals(TestParameters.VP_ITSP_UNKNOWN_0024,
                chmItspHeader.getUnknown_0024());
    }

    @Test
    public void testGetUnknown_002() {
        assertEquals(TestParameters.VP_ITSP_UNKNOWN_002C,
                chmItspHeader.getUnknown_002c());
    }

    @Test
    public void testGetUnknown_0044() {
        assertEquals(TestParameters.VP_ITSP_BYTEARR_LEN,
                chmItspHeader.getUnknown_0044().length);
    }

    @Test
    public void testGetVersion() {
        assertEquals(TestParameters.VP_ITSP_VERSION,
                chmItspHeader.getVersion());
    }

    @Test
    public void testGetSignature() {
        assertEquals(TestParameters.VP_ISTP_SIGNATURE, new String(
                chmItspHeader.getSignature(), IOUtils.UTF_8));
    }

    @Test
    public void testGetSystem_uuid() {
        assertEquals(TestParameters.VP_ITSP_BYTEARR_LEN,
                chmItspHeader.getSystem_uuid().length);
    }

    @Test
    public void testToString() {
        assertTrue(chmItspHeader.toString().contains(
                TestParameters.VP_ISTP_SIGNATURE));
    }

    @After
    public void tearDown() throws Exception {
        chmItspHeader = null;
    }

}
