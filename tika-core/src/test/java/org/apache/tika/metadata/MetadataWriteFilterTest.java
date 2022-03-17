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
package org.apache.tika.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.TikaConfigTest;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.AutoDetectParserConfig;
import org.apache.tika.parser.ParseContext;

public class MetadataWriteFilterTest extends TikaTest {


    @Test
    public void testMetadataFactoryConfig() throws Exception {
        TikaConfig tikaConfig =
                new TikaConfig(TikaConfigTest.class.getResourceAsStream("TIKA-3695.xml"));
        AutoDetectParserConfig config = tikaConfig.getAutoDetectParserConfig();
        MetadataWriteFilterFactory factory = config.getMetadataWriteFilterFactory();
        assertEquals(241, ((StandardWriteFilterFactory) factory).getMaxEstimatedBytes());
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        String mock = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<mock>";
        for (int i = 0; i < 20; i++) {
            mock += "<metadata action=\"add\" name=\"dc:creator\">01234567890123456789</metadata>";
        }
        mock += "<write element=\"p\" times=\"30\"> hello </write>\n";
        mock += "</mock>";
        Metadata metadata = new Metadata();
        List<Metadata> metadataList =
                getRecursiveMetadata(new ByteArrayInputStream(mock.getBytes(StandardCharsets.UTF_8)),
                        parser, metadata, new ParseContext(), true);
        assertEquals(1, metadataList.size());
        metadata = metadataList.get(0);

        String[] creators = metadata.getValues("dc:creator");
        assertEquals(9, creators.length);
        assertEquals("0123456", creators[8]);
        assertContainsCount(" hello ", metadata.get(TikaCoreProperties.TIKA_CONTENT), 30);
        assertEquals("true", metadata.get(TikaCoreProperties.METADATA_LIMIT_REACHED));
    }

    @Test
    public void testMetadataFactoryFieldsConfig() throws Exception {
        TikaConfig tikaConfig =
                new TikaConfig(TikaConfigTest.class.getResourceAsStream("TIKA-3695-fields.xml"));
        AutoDetectParserConfig config = tikaConfig.getAutoDetectParserConfig();
        MetadataWriteFilterFactory factory = config.getMetadataWriteFilterFactory();
        assertEquals(241, ((StandardWriteFilterFactory) factory).getMaxEstimatedBytes());
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        String mock = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<mock>";
        mock += "<metadata action=\"add\" name=\"dc:subject\">this is not a title</metadata>";
        mock += "<metadata action=\"add\" name=\"dc:title\">this is a title</metadata>";
        for (int i = 0; i < 20; i++) {
            mock += "<metadata action=\"add\" name=\"dc:creator\">01234567890123456789</metadata>";
        }
        mock += "<write element=\"p\" times=\"30\"> hello </write>\n";
        mock += "</mock>";
        Metadata metadata = new Metadata();
        metadata.add("dc:creator", "abcdefghijabcdefghij");
        metadata.add("not-allowed", "not-allowed");
        List<Metadata> metadataList =
                getRecursiveMetadata(new ByteArrayInputStream(mock.getBytes(StandardCharsets.UTF_8)),
                        parser, metadata, new ParseContext(), true);
        assertEquals(1, metadataList.size());
        metadata = metadataList.get(0);
        //test that this was removed during the filter existing stage
        assertNull(metadata.get("not-allowed"));
        //test that this was not allowed because it isn't in the "include" list
        assertNull(metadata.get("dc:subject"));

        String[] creators = metadata.getValues("dc:creator");
        assertEquals("abcdefghijabcdefghij", creators[0]);

        //this gets more than the other test because this is filtering out X-TIKA:Parsed-By", etc.
        assertEquals(12, creators.length);
        assertEquals("012345", creators[11]);
        assertContainsCount(" hello ", metadata.get(TikaCoreProperties.TIKA_CONTENT), 30);
        assertEquals("true", metadata.get(TikaCoreProperties.METADATA_LIMIT_REACHED));
    }
}
