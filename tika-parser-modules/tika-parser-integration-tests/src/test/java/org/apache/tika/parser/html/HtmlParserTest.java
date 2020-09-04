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
package org.apache.tika.parser.html;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HtmlParserTest extends TikaTest {
    @Test
    public void testDataURI() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testHTML_embedded_img.html");
        assertEquals(2, metadataList.size());
        String content = metadataList.get(0).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        assertContains("some content", content);
        //make sure that you've truncated the data: value
        assertContains("src=\"data:\"", content);
        Metadata imgMetadata = metadataList.get(1);
        assertEquals("image/jpeg", imgMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("moscow-birds",
                Arrays.asList(imgMetadata.getValues(TikaCoreProperties.SUBJECT)));
    }

    @Test
    public void testDataURIInJS() throws Exception {
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/html/tika-config.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testHTML_embedded_img_in_js.html", p);
        assertEquals(3, metadataList.size());
        String content = metadataList.get(0).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        assertContains("some content", content);
        Metadata imgMetadata = metadataList.get(1);
        assertEquals("image/jpeg", imgMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("moscow-birds",
                Arrays.asList(imgMetadata.getValues(TikaCoreProperties.SUBJECT)));
    }
}
