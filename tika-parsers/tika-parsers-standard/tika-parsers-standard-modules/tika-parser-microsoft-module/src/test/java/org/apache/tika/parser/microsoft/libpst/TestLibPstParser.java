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
package org.apache.tika.parser.microsoft.libpst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PST;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

public class TestLibPstParser extends TikaTest {

    private static boolean LIBPST_EXISTS = false;

    @BeforeAll
    public static void setUp() {
        LIBPST_EXISTS = LibPstParser.checkQuietly();
    }

    @Test
    public void testBasic() throws Exception {
        if (!LIBPST_EXISTS) {
            return;
        }
        TikaConfig tikaConfig = new TikaConfig(TestLibPstParser.class.getResourceAsStream("tika-libpst-config.xml"));
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testPST.pst", p);
        //libpst is non-deterministic when creating msg files -- sometimes we get 7, sometimes 8
        assumeTrue(metadataList.size() == 8);

        Metadata m0 = metadataList.get(0);
        assertEquals("org.apache.tika.parser.microsoft.libpst.LibPstParser", m0.getValues(TikaCoreProperties.TIKA_PARSED_BY)[1]);
        int validPaths = 0;
        for (int i = 1; i < metadataList.size(); i++) {
            String path = metadataList
                    .get(i)
                    .get(PST.PST_FOLDER_PATH);
            if (path != null) {
                assertEquals("hong-thai.nguyen", path);
                validPaths++;
            }
        }
        //NOTE: this processing via lib pst misses an email (with an ooxml attachment) embedded inside an email
        assertEquals(7, validPaths);

        assertEquals("Hong-Thai Nguyen", metadataList
                .get(1)
                .get(Message.MESSAGE_TO_DISPLAY_NAME));
        assertContains("See you there!", metadataList
                .get(1)
                .get(TikaCoreProperties.TIKA_CONTENT));

        assertEquals("NOTE", metadataList
                .get(7)
                .get(Office.MAPI_MESSAGE_CLASS));
    }

    @Test
    public void testEml() throws Exception {
        if (!LIBPST_EXISTS) {
            return;
        }
        TikaConfig tikaConfig = new TikaConfig(TestLibPstParser.class.getResourceAsStream("tika-libpst-eml-config.xml"));
        Parser p = new AutoDetectParser(tikaConfig);

        List<Metadata> metadataList = getRecursiveMetadata("testPST.pst", p);
        assertEquals(10, metadataList.size());
        Metadata m0 = metadataList.get(0);
        assertEquals("org.apache.tika.parser.microsoft.libpst.LibPstParser", m0.getValues(TikaCoreProperties.TIKA_PARSED_BY)[1]);
        int validPaths = 0;
        for (int i = 1; i < metadataList.size(); i++) {
            String path = metadataList
                    .get(i)
                    .get(PST.PST_FOLDER_PATH);
            if (path != null) {
                assertEquals("hong-thai.nguyen", path);
                validPaths++;
            }
        }
        assertEquals(7, validPaths);
        assertContains("See you there!", metadataList
                .get(3)
                .get(TikaCoreProperties.TIKA_CONTENT));

        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", metadataList
                .get(4)
                .get(Metadata.CONTENT_TYPE));
    }

}
