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

import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.accessor.ChmDirectoryListingSet;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.core.ChmExtractor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

public class TestChmExtractor {
    private ChmExtractor chmExtractor = null;

    @Before
    public void setUp() throws Exception {
        chmExtractor = new ChmExtractor(
                new ByteArrayInputStream(TestParameters.chmData));
    }

    @Test
    public void testEnumerateChm() {
        List<String> chmEntries = chmExtractor.enumerateChm();
        assertEquals(TestParameters.VP_CHM_ENTITIES_NUMBER,
                chmEntries.size());
    }

    @Test
    public void testGetChmDirList() {
        assertNotNull(chmExtractor.getChmDirList());
    }

    @Test
    public void testExtractChmEntry() throws TikaException{
        ChmDirectoryListingSet entries = chmExtractor.getChmDirList();
        
        int count = 0;
        for (DirectoryListingEntry directoryListingEntry : entries.getDirectoryListingEntryList()) {
            chmExtractor.extractChmEntry(directoryListingEntry);
            ++count;
        }
        assertEquals(TestParameters.VP_CHM_ENTITIES_NUMBER, count);
    }

}
