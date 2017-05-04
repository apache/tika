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
package org.apache.tika.eval.io;


import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Before;
import org.junit.Test;

public class ExtractReaderTest extends TikaTest {

    private Path testJsonFile;
    private Path testTxtFile;

    @Before
    public void setUp() throws Exception {
        testJsonFile = getResourceAsFile("/test-dirs/extractsA/file2_attachANotB.doc.json").toPath();
        testTxtFile = getResourceAsFile("/test-dirs/extractsB/file13_attachANotB.doc.txt").toPath();
    }

    @Test
    public void testBasic() throws Exception {

        ExtractReader extractReader = new ExtractReader();
        List<Metadata> metadataList = extractReader.loadExtract(testJsonFile);

        assertEquals(2, metadataList.size());
        assertEquals(1, metadataList.get(0).getValues(RecursiveParserWrapper.TIKA_CONTENT).length);
        assertEquals(1, metadataList.get(1).getValues(RecursiveParserWrapper.TIKA_CONTENT).length);
        assertContains("fox", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertContains("attachment", metadataList.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));

        extractReader = new ExtractReader(ExtractReader.ALTER_METADATA_LIST.FIRST_ONLY);
        metadataList = extractReader.loadExtract(testJsonFile);
        assertEquals(1, metadataList.size());
        assertEquals(1, metadataList.get(0).getValues(RecursiveParserWrapper.TIKA_CONTENT).length);
        assertContains("fox", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertNotContained("attachment", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));

        extractReader = new ExtractReader(ExtractReader.ALTER_METADATA_LIST.CONCATENATE_CONTENT_INTO_FIRST);
        metadataList = extractReader.loadExtract(testJsonFile);
        assertEquals(1, metadataList.size());
        assertEquals(1, metadataList.get(0).getValues(RecursiveParserWrapper.TIKA_CONTENT).length);
        assertContains("fox", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertContains("attachment", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Test
    public void testTextBasic() throws IOException {
        ExtractReader extractReader = new ExtractReader();
        List<Metadata> metadataList = extractReader.loadExtract(testTxtFile);
        assertEquals(1, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals(1, m.getValues(RecursiveParserWrapper.TIKA_CONTENT).length);
        assertEquals("the quick brown fox fox fox jumped over the lazy lazy dog\n",
                m.get(RecursiveParserWrapper.TIKA_CONTENT));

        //test that the mime is inferred from the file extension
        assertEquals("application/msword", m.get(Metadata.CONTENT_TYPE));
    }



}
