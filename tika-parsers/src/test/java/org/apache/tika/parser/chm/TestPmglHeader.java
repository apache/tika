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

import org.apache.tika.parser.chm.accessor.ChmPmglHeader;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;

public class TestPmglHeader extends TestCase {
    ChmPmglHeader chmPmglHeader = null;

    public void setUp() throws Exception {
        byte[] data = TestParameters.chmData;
        chmPmglHeader = new ChmPmglHeader();
        chmPmglHeader.parse(ChmCommons.copyOfRange(data,
                ChmConstants.START_PMGL, ChmConstants.START_PMGL
                        + ChmConstants.CHM_PMGL_LEN + 10), chmPmglHeader);
    }

    public void testToString() {
        Assert.assertTrue((chmPmglHeader != null)
                && chmPmglHeader.toString().length() > 0);
    }

    public void testChmPmglHeaderGet() {
        Assert.assertEquals(TestParameters.VP_PMGL_SIGNATURE, new String(
                chmPmglHeader.getSignature()));
    }

    public void testGetBlockNext() {
        Assert.assertEquals(TestParameters.VP_PMGL_BLOCK_NEXT,
                chmPmglHeader.getBlockNext());
    }

    public void testGetBlockPrev() {
        Assert.assertEquals(TestParameters.VP_PMGL_BLOCK_PREV,
                chmPmglHeader.getBlockPrev());
    }

    public void testGetFreeSpace() {
        Assert.assertEquals(TestParameters.VP_PMGL_FREE_SPACE,
                chmPmglHeader.getFreeSpace());
    }

    public void testGetUnknown0008() {
        Assert.assertEquals(TestParameters.VP_PMGL_UNKNOWN_008,
                chmPmglHeader.getUnknown0008());
    }

    public void tearDown() throws Exception {
    }
}
