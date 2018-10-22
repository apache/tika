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
package org.apache.tika.parser.wordperfect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.junit.Test;

import java.io.InputStream;

/**
 * Junit test class for the {@link WordPerfectParser}.
 * @author Pascal Essiembre
 */
public class WordPerfectTest extends TikaTest {

    @Test
    public void testWordPerfectParser() throws Exception {
        XMLResult r = getXML("testWordPerfect.wpd");
        assertEquals(WordPerfectParser.WP_6_x.toString(),
                r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(1, r.metadata.getValues(Metadata.CONTENT_TYPE).length);
        assertContains("<p>AND FURTHER</p>", r.xml);
        assertContains("test1-2", r.xml);
    }

    @Test
    public void testVersion50() throws Exception {
        //test file "testWordPerfect_5_0.wp" is from govdocs1: 126546.wp
        XMLResult r = getXML("testWordPerfect_5_0.wp");
        assertEquals(WordPerfectParser.WP_5_0.toString(),
                r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(1, r.metadata.getValues(Metadata.CONTENT_TYPE).length);
        assertContains("<p>Surrounded by her family", r.xml);
    }

    @Test
    public void testVersion51() throws Exception {
        //testfile "testWordperfect_5_1.wp is from govdocs1: 758750.wp
        XMLResult r = getXML("testWordPerfect_5_1.wp");
        assertEquals(WordPerfectParser.WP_5_1.toString(),
                r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(1, r.metadata.getValues(Metadata.CONTENT_TYPE).length);
        assertContains("<p>STUDY RESULTS: Existing condition", r.xml);
        assertContains("Seattle nonstop flights.</p>", r.xml);
    }

    @Test
    public void testDeletedText() throws Exception {
        String xml = getXML("testWordPerfect.wpd").xml;
        assertContains("this was deleted.", xml);


        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/wordperfect/tika-config.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);

        Parser p = tikaConfig.getParser();

        xml = getXML("testWordPerfect.wpd", p).xml;
        assertNotContained("this was deleted", xml);
    }
}
