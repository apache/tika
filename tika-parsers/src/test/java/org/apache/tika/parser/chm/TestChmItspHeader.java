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

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.tika.parser.chm.accessor.ChmItsfHeader;
import org.apache.tika.parser.chm.accessor.ChmItspHeader;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;

/**
 * Tests all public methods of the ChmItspHeader
 * 
 */
public class TestChmItspHeader extends TestCase {
    private ChmItspHeader chmItspHeader = null;

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

    public void testGetBlock_len() {
        Assert.assertEquals(TestParameters.VP_BLOCK_LENGTH,
                chmItspHeader.getBlock_len());
    }

    public void testGetBlockidx_intvl() {
        Assert.assertEquals(TestParameters.VP_BLOCK_INDEX_INTERVAL,
                chmItspHeader.getBlockidx_intvl());
    }

    public void testGetHeader_len() {
        Assert.assertEquals(TestParameters.VP_ITSP_HEADER_LENGTH,
                chmItspHeader.getHeader_len());
    }

    public void testGetIndex_depth() {
        Assert.assertEquals(TestParameters.VP_INDEX_DEPTH,
                chmItspHeader.getIndex_depth());
    }

    public void testGetIndex_head() {
        Assert.assertEquals(TestParameters.VP_INDEX_HEAD,
                chmItspHeader.getIndex_head());
    }

    public void testGetIndex_root() {
        Assert.assertEquals(TestParameters.VP_INDEX_ROOT,
                chmItspHeader.getIndex_root());
    }

    public void testGetLang_id() {
        Assert.assertEquals(TestParameters.VP_LANGUAGE_ID,
                chmItspHeader.getLang_id());
    }

    public void testGetNum_blocks() {
        Assert.assertEquals(TestParameters.VP_UNKNOWN_NUM_BLOCKS,
                chmItspHeader.getNum_blocks());
    }

    public void testGetUnknown_000c() {
        Assert.assertEquals(TestParameters.VP_ITSP_UNKNOWN_000C,
                chmItspHeader.getUnknown_000c());
    }

    public void testGetUnknown_0024() {
        Assert.assertEquals(TestParameters.VP_ITSP_UNKNOWN_0024,
                chmItspHeader.getUnknown_0024());
    }

    public void testGetUnknown_002() {
        Assert.assertEquals(TestParameters.VP_ITSP_UNKNOWN_002C,
                chmItspHeader.getUnknown_002c());
    }

    public void testGetUnknown_0044() {
        Assert.assertEquals(TestParameters.VP_ITSP_BYTEARR_LEN,
                chmItspHeader.getUnknown_0044().length);
    }

    public void testGetVersion() {
        Assert.assertEquals(TestParameters.VP_ITSP_VERSION,
                chmItspHeader.getVersion());
    }

    public void testGetSignature() {
        Assert.assertEquals(TestParameters.VP_ISTP_SIGNATURE, new String(
                chmItspHeader.getSignature()));
    }

    public void testGetSystem_uuid() {
        Assert.assertEquals(TestParameters.VP_ITSP_BYTEARR_LEN,
                chmItspHeader.getSystem_uuid().length);
    }

    public void testToString() {
        Assert.assertTrue(chmItspHeader.toString().contains(
                TestParameters.VP_ISTP_SIGNATURE));
    }

    public void tearDown() throws Exception {
        chmItspHeader = null;
    }

}
