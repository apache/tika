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
package org.apache.tika.metadata.writefilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.TikaConfigTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.AutoDetectParserConfig;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.Test;

public class StandardWriteFilterTest extends TikaTest {

    @Test
    public void testMetadataFactoryConfig() throws Exception {
        TikaConfig tikaConfig =
                new TikaConfig(TikaConfigTest.class.getResourceAsStream("TIKA-3695.xml"));
        AutoDetectParserConfig config = tikaConfig.getAutoDetectParserConfig();
        MetadataWriteFilterFactory factory = config.getMetadataWriteFilterFactory();
        assertEquals(350, ((StandardWriteFilterFactory) factory).getMaxTotalEstimatedBytes());
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        String mock = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>";
        for (int i = 0; i < 20; i++) {
            mock += "<metadata action=\"add\" name=\"dc:creator\">01234567890123456789</metadata>";
        }
        mock += "<write element=\"p\" times=\"30\"> hello </write>\n";
        mock += "</mock>";
        Metadata metadata = new Metadata();
        List<Metadata> metadataList =
                getRecursiveMetadata(
                        new ByteArrayInputStream(mock.getBytes(StandardCharsets.UTF_8)),
                        parser,
                        metadata,
                        new ParseContext(),
                        true);
        assertEquals(1, metadataList.size());
        metadata = metadataList.get(0);

        String[] creators = metadata.getValues("dc:creator");
        assertEquals(3, creators.length);
        assertEquals("01", creators[2]);
        assertContainsCount(" hello ", metadata.get(TikaCoreProperties.TIKA_CONTENT), 30);
        assertTruncated(metadata);
    }

    @Test
    public void testMetadataFactoryFieldsConfig() throws Exception {
        TikaConfig tikaConfig =
                new TikaConfig(TikaConfigTest.class.getResourceAsStream("TIKA-3695-fields.xml"));
        AutoDetectParserConfig config = tikaConfig.getAutoDetectParserConfig();
        MetadataWriteFilterFactory factory = config.getMetadataWriteFilterFactory();
        assertEquals(241, ((StandardWriteFilterFactory) factory).getMaxTotalEstimatedBytes());
        assertEquals(999, ((StandardWriteFilterFactory) factory).getMaxKeySize());
        assertEquals(10001, ((StandardWriteFilterFactory) factory).getMaxFieldSize());
        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        String mock = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>";
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
                getRecursiveMetadata(
                        new ByteArrayInputStream(mock.getBytes(StandardCharsets.UTF_8)),
                        parser,
                        metadata,
                        new ParseContext(),
                        true);
        assertEquals(1, metadataList.size());
        metadata = metadataList.get(0);
        // test that this was removed during the filter existing stage
        assertNull(metadata.get("not-allowed"));
        // test that this was not allowed because it isn't in the "include" list
        assertNull(metadata.get("dc:subject"));

        String[] creators = metadata.getValues("dc:creator");
        assertEquals("abcdefghijabcdefghij", creators[0]);

        // this gets more than the other test because this is filtering out some fields
        assertEquals(3, creators.length);
        assertEquals("012345678901234", creators[2]);
        assertContainsCount(" hello ", metadata.get(TikaCoreProperties.TIKA_CONTENT), 30);
        assertTruncated(metadata);
    }

    @Test
    public void testKeySizeFilter() throws Exception {
        Metadata metadata = filter(10, 1000, 10000, 100, null, true);
        // test that must add keys are not truncated
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "some-long-parser1");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "some-long-parser2");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "some-long-parser3");
        assertEquals(3, metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY).length);

        metadata.add(OfficeOpenXMLExtended.DOC_SECURITY_STRING, "some doc-security-string");
        // truncated to 10 bytes in UTF-16 = 5 characters
        assertEquals("some doc-security-string", metadata.getValues("exten")[0]);
        assertTruncated(metadata);

        metadata.set(OfficeOpenXMLExtended.APP_VERSION, "some other string");
        assertEquals("some other string", metadata.getValues("exten")[0]);
        assertTruncated(metadata);
    }

    @Test
    public void testAfterMaxHit() throws Exception {
        String k = "dc:creator"; // 20 bytes
        // key is > maxTotalBytes, so the value isn't even added
        Metadata metadata = filter(100, 10000, 10, 100, null, false);
        metadata.set(k, "ab");
        assertEquals(1, metadata.names().length);
        assertEquals("true", metadata.get(TikaCoreProperties.TRUNCATED_METADATA));

        metadata = filter(100, 10000, 50, 100, null, false);
        for (int i = 0; i < 10; i++) {
            metadata.set(k, "abcde");
        }

        assertEquals(1, metadata.names().length);
        assertEquals("abcde", metadata.getValues(k)[0]);
        assertNull(metadata.get(TikaCoreProperties.TRUNCATED_METADATA));

        metadata.add(k, "abcde"); // 40
        metadata.add(k, "abc"); // 46
        metadata.add(k, "abcde"); // only the first character is taken from this
        metadata.add(k, "abcde"); // this shouldn't even be countenanced

        assertEquals(2, metadata.names().length);
        assertEquals(4, metadata.getValues(k).length);
        assertEquals("abcde", metadata.getValues(k)[0]);
        assertEquals("abcde", metadata.getValues(k)[1]);
        assertEquals("abc", metadata.getValues(k)[2]);
        assertEquals("a", metadata.getValues(k)[3]);
        assertEquals("true", metadata.get(TikaCoreProperties.TRUNCATED_METADATA));

        // this will force a reset of the total max bytes because
        // this is a set, not an add.  This should get truncated at 15 chars = 30 bytes
        metadata.set(k, "abcdefghijklmnopqrstuvwx");
        assertEquals(2, metadata.names().length);
        assertEquals(1, metadata.getValues(k).length);
        assertEquals("abcdefghijklmno", metadata.getValues(k)[0]);
        assertTruncated(metadata);
    }

    @Test
    public void testMinSizeForAlwaysInclude() throws Exception {
        // test that mimes don't get truncated
        Metadata metadata = filter(100, 10, 10000, 100, null, true);

        String mime = getLongestMime().toString();
        metadata.set(Metadata.CONTENT_TYPE, mime);
        assertEquals(mime, metadata.get(Metadata.CONTENT_TYPE));

        // test that other fields are truncated
        metadata.set("dc:title", "abcdefghij");
        assertEquals("abcde", metadata.get("dc:title"));
        assertTruncated(metadata);
    }

    @Test
    public void testMaxFieldValues() throws Exception {
        Metadata metadata = filter(100, 10000, 10000, 3, null, true);
        for (int i = 0; i < 10; i++) {
            metadata.add(TikaCoreProperties.SUBJECT, "ab");
        }
        assertEquals(3, metadata.getValues(TikaCoreProperties.SUBJECT).length);
    }

    private void assertTruncated(Metadata metadata) {
        assertEquals("true", metadata.get(TikaCoreProperties.TRUNCATED_METADATA));
    }

    private Metadata filter(
            int maxKeySize,
            int maxFieldSize,
            int maxTotalBytes,
            int maxValuesPerField,
            Set<String> includeFields,
            boolean includeEmpty) {
        MetadataWriteFilter filter =
                new StandardWriteFilter(
                        maxKeySize,
                        maxFieldSize,
                        maxTotalBytes,
                        maxValuesPerField,
                        includeFields,
                        includeEmpty);
        Metadata metadata = new Metadata();
        metadata.setMetadataWriteFilter(filter);
        return metadata;
    }

    public MediaType getLongestMime() throws Exception {
        MimeTypes types = TikaConfig.getDefaultConfig().getMimeRepository();
        MediaTypeRegistry registry = types.getMediaTypeRegistry();
        int maxLength = -1;
        MediaType longest = null;
        for (MediaType mt : registry.getTypes()) {
            int len = mt.toString().length() * 2;
            if (len > maxLength) {
                maxLength = len;
                longest = mt;
            }
        }
        return longest;
    }
}
