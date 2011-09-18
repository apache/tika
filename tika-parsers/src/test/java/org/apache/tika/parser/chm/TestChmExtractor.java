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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.chm.accessor.ChmDirectoryListingSet;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.core.ChmExtractor;

public class TestChmExtractor extends TestCase {
    private ChmExtractor chmExtractor = null;

    public void setUp() throws Exception {
        chmExtractor = new ChmExtractor(
                new ByteArrayInputStream(TestParameters.chmData));
    }

    public void testEnumerateChm() {
        List<String> chmEntries = chmExtractor.enumerateChm();
        Assert.assertEquals(TestParameters.VP_CHM_ENTITIES_NUMBER,
                chmEntries.size());
    }

    public void testGetChmDirList() {
        Assert.assertNotNull(chmExtractor.getChmDirList());
    }

    public void testExtractChmEntry() throws TikaException{
        ChmDirectoryListingSet entries = chmExtractor.getChmDirList();
        byte[][] localFile;
        int count = 0;
        for (Iterator<DirectoryListingEntry> it = entries
                .getDirectoryListingEntryList().iterator(); it.hasNext();) {
            localFile = chmExtractor.extractChmEntry(it.next());
            if (localFile != null) {
                ++count;
            }
        }
        Assert.assertEquals(TestParameters.VP_CHM_ENTITIES_NUMBER, count);
    }

    public void testChmParser() throws Exception{
        List<String> files = new ArrayList<String>();
        files.add("/test-documents/testChm.chm");
        files.add("/test-documents/testChm3.chm");

        for (String fileName : files) {
            InputStream stream =
                    TestChmBlockInfo.class.getResourceAsStream(fileName);
            try {
                CHMDocumentInformation chmDocInfo = CHMDocumentInformation.load(stream);
                Metadata md = new Metadata();
                String text = chmDocInfo.getText();
                chmDocInfo.getCHMDocInformation(md);
                assertEquals(TestParameters.VP_CHM_MIME_TYPE, md.toString().trim());
                assertTrue(text.length() > 0);
            } finally {
                stream.close();
            }
        }
    }

    public void tearDown() throws Exception {
    }

}
