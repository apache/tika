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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class StandardMetadataLimiterTest extends TikaTest {


    @Test
    public void testMetadataFactoryConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3695.json"));
        AutoDetectParser parser = (AutoDetectParser) loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        MetadataWriteLimiterFactory factory = context.get(MetadataWriteLimiterFactory.class);
        assertEquals(330, ((StandardMetadataLimiterFactory) factory).getMaxTotalBytes());
        assertFalse(((StandardMetadataLimiterFactory) factory).getIncludeFields().isEmpty(),
                "includeFields should not be empty");
        assertTrue(((StandardMetadataLimiterFactory) factory).getIncludeFields().contains("dc:creator"),
                "includeFields should contain dc:creator");
        ParseContext parseContext = new ParseContext();
        parseContext.set(MetadataWriteLimiterFactory.class, factory);
        String mock = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<mock>";
        for (int i = 0; i < 20; i++) {
            mock += "<metadata action=\"add\" name=\"dc:creator\">01234567890123456789</metadata>";
        }
        mock += "<write element=\"p\" times=\"30\"> hello </write>\n";
        mock += "</mock>";
        Metadata metadata = Metadata.newInstance(parseContext);
        List<Metadata> metadataList =
                getRecursiveMetadata(TikaInputStream.get(mock.getBytes(StandardCharsets.UTF_8)),
                        parser, metadata, parseContext, true);
        assertEquals(1, metadataList.size());
        metadata = metadataList.get(0);

        String[] creators = metadata.getValues("dc:creator");
        assertEquals(2, creators.length);
        assertEquals("012345678901", creators[1]);
        assertContainsCount(" hello ", metadata.get(TikaCoreProperties.TIKA_CONTENT), 30);
        assertTruncated(metadata);
    }

    @Test
    public void testMetadataFactoryFieldsConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3695-fields.json"));
        AutoDetectParser parser = (AutoDetectParser) loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        MetadataWriteLimiterFactory factory = context.get(MetadataWriteLimiterFactory.class);
        assertEquals(421, ((StandardMetadataLimiterFactory) factory).getMaxTotalBytes());
        assertEquals(999, ((StandardMetadataLimiterFactory) factory).getMaxKeySize());
        assertEquals(10001, ((StandardMetadataLimiterFactory) factory).getMaxFieldSize());
        assertFalse(((StandardMetadataLimiterFactory) factory).getIncludeFields().isEmpty(),
                "includeFields should not be empty");
        assertTrue(((StandardMetadataLimiterFactory) factory).getIncludeFields().contains("dc:creator"),
                "includeFields should contain dc:creator");
        ParseContext parseContext = new ParseContext();
        parseContext.set(MetadataWriteLimiterFactory.class, factory);
        String mock = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<mock>";
        mock += "<metadata action=\"add\" name=\"dc:subject\">this is not a title</metadata>";
        mock += "<metadata action=\"add\" name=\"dc:title\">this is a title</metadata>";
        for (int i = 0; i < 20; i++) {
            mock += "<metadata action=\"add\" name=\"dc:creator\">01234567890123456789</metadata>";
        }
        mock += "<write element=\"p\" times=\"30\"> hello </write>\n";
        mock += "</mock>";
        Metadata metadata = Metadata.newInstance(parseContext);
        metadata.add("dc:creator", "abcdefghijabcdefghij");
        metadata.add("not-allowed", "not-allowed");
        List<Metadata> metadataList =
                getRecursiveMetadata(TikaInputStream.get(mock.getBytes(StandardCharsets.UTF_8)),
                        parser, metadata, parseContext, true);
        assertEquals(1, metadataList.size());
        metadata = metadataList.get(0);
        //test that this was removed during the filter existing stage
        assertNull(metadata.get("not-allowed"));
        //test that this was not allowed because it isn't in the "include" list
        assertNull(metadata.get("dc:subject"));

        String[] creators = metadata.getValues("dc:creator");
        assertEquals("abcdefghijabcdefghij", creators[0]);

        //this gets more than the other test because this is filtering out some fields
        assertEquals(3, creators.length);
        assertEquals("012345678901234", creators[2]);
        assertContainsCount(" hello ", metadata.get(TikaCoreProperties.TIKA_CONTENT), 30);
        assertTruncated(metadata);
    }

    @Test
    public void testKeySizeFilter() throws Exception {
        Metadata metadata = filter(10, 1000, 10000, 100,
                Collections.EMPTY_SET, Collections.EMPTY_SET, true);
        //test that must add keys are not truncated
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "some-long-parser1");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "some-long-parser2");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "some-long-parser3");
        assertEquals(3, metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY).length);

        metadata.add(OfficeOpenXMLExtended.DOC_SECURITY_STRING, "some doc-security-string");
        //truncated to 10 bytes in UTF-16 = 5 characters
        assertEquals("some doc-security-string", metadata.getValues("exten")[0]);
        assertTruncated(metadata);

        metadata.set(OfficeOpenXMLExtended.APP_VERSION, "some other string");
        assertEquals("some other string", metadata.getValues("exten")[0]);
        assertTruncated(metadata);
    }

    @Test
    public void testAfterMaxHit() throws Exception {
        String k = "dc:creator";//20 bytes
        //key is > maxTotalBytes, so the value isn't even added
        Metadata metadata = filter(100, 10000, 10,
                100, Collections.EMPTY_SET, Collections.EMPTY_SET, false);
        metadata.set(k, "ab");
        assertEquals(1, metadata.names().length);
        assertEquals("true", metadata.get(TikaCoreProperties.TRUNCATED_METADATA));

        metadata = filter(100, 10000, 50, 100,
                Collections.EMPTY_SET, Collections.EMPTY_SET, false);
        for (int i = 0; i < 10; i++) {
            metadata.set(k, "abcde");
        }

        assertEquals(1, metadata.names().length);
        assertEquals("abcde", metadata.getValues(k)[0]);
        assertNull(metadata.get(TikaCoreProperties.TRUNCATED_METADATA));

        metadata.add(k, "abcde");//40
        metadata.add(k, "abc");//46
        metadata.add(k, "abcde");//only the first character is taken from this
        metadata.add(k, "abcde");//this shouldn't even be countenanced

        assertEquals(2, metadata.names().length);
        assertEquals(4, metadata.getValues(k).length);
        assertEquals("abcde", metadata.getValues(k)[0]);
        assertEquals("abcde", metadata.getValues(k)[1]);
        assertEquals("abc", metadata.getValues(k)[2]);
        assertEquals("a", metadata.getValues(k)[3]);
        assertEquals("true", metadata.get(TikaCoreProperties.TRUNCATED_METADATA));

        //this will force a reset of the total max bytes because
        //this is a set, not an add.  This should get truncated at 15 chars = 30 bytes
        metadata.set(k, "abcdefghijklmnopqrstuvwx");
        assertEquals(2, metadata.names().length);
        assertEquals(1, metadata.getValues(k).length);
        assertEquals("abcdefghijklmno", metadata.getValues(k)[0]);
        assertTruncated(metadata);
    }

    @Test
    public void testMinSizeForAlwaysInclude() throws Exception {
        //test that mimes don't get truncated
        Metadata metadata = filter(100, 10, 10000, 100,
                Collections.EMPTY_SET, Collections.EMPTY_SET, true);

        String mime = getLongestMime().toString();
        metadata.set(Metadata.CONTENT_TYPE, mime);
        assertEquals(mime, metadata.get(Metadata.CONTENT_TYPE));

        //test that other fields are truncated
        metadata.set("dc:title", "abcdefghij");
        assertEquals("abcde", metadata.get("dc:title"));
        assertTruncated(metadata);
    }

    @Test
    public void testMaxFieldValues() throws Exception {
        Metadata metadata = filter(100, 10000, 10000, 3,
                Collections.EMPTY_SET, Collections.EMPTY_SET, true);
        for (int i = 0; i < 10; i++) {
            metadata.add(TikaCoreProperties.SUBJECT, "ab");
        }
        assertEquals(3, metadata.getValues(TikaCoreProperties.SUBJECT).length);
    }

    @Test
    public void testAddOrder() throws Exception {
        StandardMetadataLimiter standardWriteFilter = new StandardMetadataLimiter(100, 1000, 100000, 10, Set.of(), Set.of(), true);
        Metadata m = new Metadata(standardWriteFilter);
        m.add("test", "foo");
        m.add("test", "bar");
        m.add("test", "baz");

        assertArrayEquals(new String[]{"foo", "bar", "baz"}, m.getValues("test"));
    }

    @Test
    public void testNullValues() throws Exception {
        StandardMetadataLimiter standardWriteFilter = new StandardMetadataLimiter(100, 1000, 100000, 10, Set.of(), Set.of(), true);
        Metadata m = new Metadata(standardWriteFilter);
        m.set("test", "foo");
        m.set("test", null);

        assertEquals(0, m.names().length);
        assertNull(m.get("test"));

        //now test adding
        m = new Metadata(standardWriteFilter);
        m.add("test", "foo");
        m.add("test", null);
        //Not sure this is the behavior we want, but it is what we're currently doing.
        assertArrayEquals(new String[]{"foo"}, m.getValues("test"));

        //now check when empty not allowed
        standardWriteFilter = new StandardMetadataLimiter(100, 1000, 100000, 10, Set.of(), Set.of(), false);
        m = new Metadata(standardWriteFilter);
        m.set("test", "foo");
        assertEquals(1, m.names().length);
        assertEquals("foo", m.get("test"));

        m.set("test", null);
        assertEquals(0, m.names().length);
        assertNull(m.get("test"));

        m.add("test", "foo");
        m.add("test", null);

        assertEquals(1, m.names().length);
        assertEquals(1, m.getValues("test").length);
    }

    @Test
    public void testNullKeys() {
        StandardMetadataLimiter standardWriteFilter = new StandardMetadataLimiter(100, 1000, 100000, 10, Set.of(), Set.of(), true);
        Metadata m = new Metadata(standardWriteFilter);
        Exception ex = assertThrows(NullPointerException.class, () -> {
            m.set((String) null, "foo");
        });
        ex = assertThrows(NullPointerException.class, () -> {
            m.set((Property) null, "foo");
        });

        ex = assertThrows(NullPointerException.class, () -> {
            m.add((Property) null, "foo");
        });

        ex = assertThrows(NullPointerException.class, () -> {
            m.add((Property) null, "foo");
        });

    }

    @Test
    public void testExclude() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3695-exclude.json"));
        Parser parser = loader.loadAutoDetectParser();
        ParseContext parseContext = loader.loadParseContext();
        String mock = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<mock>";
        mock += "<metadata action=\"add\" name=\"dc:creator\">01234567890123456789</metadata>";
        mock += "<metadata action=\"add\" name=\"subject\">01234567890123456789</metadata>";
        mock += "<metadata action=\"add\" name=\"subjectB\">01234567890123456789</metadata>";
        mock += "<write element=\"p\" times=\"1\"> hello </write>\n";
        mock += "</mock>";
        Metadata metadata = Metadata.newInstance(parseContext);
        List<Metadata> metadataList =
                getRecursiveMetadata(TikaInputStream.get(mock.getBytes(StandardCharsets.UTF_8)),
                        parser, metadata, parseContext, true);
        assertEquals(1, metadataList.size());
        metadata = metadataList.get(0);
        // Verify the key fields - dc:creator and subjectB should be present, subject should be excluded
        assertEquals("01234567890123456789", metadata.get("dc:creator"));
        assertEquals("01234567890123456789", metadata.get("subjectB"));
        assertNull(metadata.get("subject"));
    }


    private void assertTruncated(Metadata metadata) {
        assertEquals("true", metadata.get(TikaCoreProperties.TRUNCATED_METADATA));
    }
    private Metadata filter(int maxKeySize, int maxFieldSize, int maxTotalBytes,
                            int maxValuesPerField,
                            Set<String> includeFields, Set<String> excludeFields, boolean includeEmpty) {
        MetadataWriteLimiter filter = new StandardMetadataLimiter(maxKeySize, maxFieldSize,
                maxTotalBytes, maxValuesPerField, includeFields, excludeFields, includeEmpty);
        return new Metadata(filter);
    }

    public MediaType getLongestMime() throws Exception {
        MimeTypes types = MimeTypes.getDefaultMimeTypes();
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
