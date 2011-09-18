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
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;

/**
 * Tests all public functions of ChmItsfHeader
 * 
 */
public class TestChmItsfHeader extends TestCase {
    private ChmItsfHeader chmItsfHeader = null;

    public void setUp() throws Exception {
        chmItsfHeader = new ChmItsfHeader();
        byte[] data = TestParameters.chmData;
        // chmItsfHeader.parse(Arrays.copyOfRange(data, 0,
        // ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsfHeader);
        chmItsfHeader.parse(ChmCommons.copyOfRange(data, 0,
                ChmConstants.CHM_ITSF_V3_LEN - 1), chmItsfHeader);
    }

    public void getDataOffset() {
        Assert.assertEquals(TestParameters.VP_DATA_OFFSET_LENGTH,
                chmItsfHeader.getDataOffset());
    }

    public void getDir_uuid() {
        Assert.assertNotNull(chmItsfHeader.getDir_uuid());
    }

    public void getDirLen() {
        Assert.assertEquals(TestParameters.VP_DIRECTORY_LENGTH,
                chmItsfHeader.getDirLen());
    }

    public void getDirOffset() {
        Assert.assertEquals(TestParameters.VP_DIRECTORY_OFFSET,
                chmItsfHeader.getDirOffset());
    }

    public void getHeaderLen() {
        Assert.assertEquals(TestParameters.VP_ITSF_HEADER_LENGTH,
                chmItsfHeader.getHeaderLen());
    }

    public void getLangId() {
        Assert.assertEquals(TestParameters.VP_LANGUAGE_ID,
                chmItsfHeader.getLangId());
    }

    public void getLastModified() {
        Assert.assertEquals(TestParameters.VP_LAST_MODIFIED,
                chmItsfHeader.getLastModified());
    }

    public void getUnknown_000c() {
        Assert.assertEquals(TestParameters.VP_UNKNOWN_000C,
                chmItsfHeader.getUnknown_000c());
    }

    public void getUnknownLen() {
        Assert.assertEquals(TestParameters.VP_UNKNOWN_LEN,
                chmItsfHeader.getUnknownLen());
    }

    public void getUnknownOffset() {
        Assert.assertEquals(TestParameters.VP_UNKNOWN_OFFSET,
                chmItsfHeader.getUnknownOffset());
    }

    public void getVersion() {
        Assert.assertEquals(TestParameters.VP_VERSION,
                chmItsfHeader.getVersion());
    }

    public void testToString() {
        Assert.assertTrue(chmItsfHeader.toString().contains(
                TestParameters.VP_ISTF_SIGNATURE));
    }

    public void tearDown() throws Exception {
        chmItsfHeader = null;
    }
}
