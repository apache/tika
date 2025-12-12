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
package org.apache.tika.metadata.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

public class TestMetadataFilter extends TikaTest {

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    @Test
    public void testDefault() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");

        MetadataFilter defaultFilter = new NoOpFilter();
        metadata = filterOne(defaultFilter, metadata);

        assertEquals(2, metadata.names().length);
        assertEquals("title", metadata.get("title"));
        assertEquals("author", metadata.get("author"));
    }

    @Test
    public void testIncludeFilter() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");

        MetadataFilter filter = new IncludeFieldMetadataFilter(set("title"));
        metadata = filterOne(filter, metadata);
        assertEquals(1, metadata.names().length);
        assertEquals("title", metadata.get("title"));
        assertNull(metadata.get("author"));
    }

    @Test
    public void testExcludeFilter() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");

        MetadataFilter filter = new ExcludeFieldMetadataFilter(set("title"));
        metadata = filterOne(filter, metadata);
        assertEquals(1, metadata.names().length);
        assertEquals("author", metadata.get("author"));
        assertNull(metadata.get("title"));
    }

    @Test
    public void testConfigIncludeFilter() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3137-include.json"));
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");
        metadata.set("content", "content");

        metadata = filterOne(loader.loadMetadataFilters(), metadata);

        assertEquals(2, metadata.size());
        assertEquals("title", metadata.get("title"));
        assertEquals("author", metadata.get("author"));
    }

    @Test
    public void testConfigExcludeFilter() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3137-exclude.json"));
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");
        metadata.set("content", "content");

        metadata = filterOne(loader.loadMetadataFilters(), metadata);
        assertEquals(1, metadata.size());
        assertEquals("content", metadata.get("content"));
    }

    @Test
    public void testConfigIncludeAndUCFilter() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3137-include-uc.json"));
        String[] expectedTitles = new String[]{"TITLE1", "TITLE2", "TITLE3"};
        Metadata metadata = new Metadata();
        metadata.add("title", "title1");
        metadata.add("title", "title2");
        metadata.add("title", "title3");
        metadata.set("author", "author");
        metadata.set("content", "content");

        metadata = filterOne(loader.loadMetadataFilters(), metadata);

        assertEquals(2, metadata.size());
        assertArrayEquals(expectedTitles, metadata.getValues("title"));
        assertEquals("AUTHOR", metadata.get("author"));
    }

    @Test
    public void testMimeClearingFilter() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, MediaType.image("jpeg").toString());
        metadata.set("author", "author");

        MetadataFilter filter = new ClearByMimeMetadataFilter(set("image/jpeg", "application/pdf"));
        metadata = filterOne(filter, metadata);
        assertEquals(0, metadata.size());

        metadata.set(Metadata.CONTENT_TYPE, MediaType.text("plain").toString());
        metadata.set("author", "author");
        metadata = filterOne(filter, metadata);
        assertEquals(2, metadata.size());
        assertEquals("author", metadata.get("author"));

    }

    @Test
    public void testMimeClearingFilterConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3137-mimes-uc.json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, MediaType.image("jpeg").toString());
        metadata.set("author", "author");

        MetadataFilter filter = loader.loadMetadataFilters();
        metadata = filterOne(filter, metadata);
        assertEquals(0, metadata.size());

        metadata.set(Metadata.CONTENT_TYPE, MediaType.text("plain").toString());
        metadata.set("author", "author");
        metadata = filterOne(filter, metadata);
        assertEquals(2, metadata.size());
        assertEquals("AUTHOR", metadata.get("author"));
    }

    @Test
    public void testFieldNameMapping() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3137-field-mapping.json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "quick brown fox");
        metadata.set("author", "author");
        metadata.set("a", "a-value");

        MetadataFilter filter = loader.loadMetadataFilters();
        metadata = filterOne(filter, metadata);
        assertEquals("quick brown fox", metadata.get("content"));
        assertEquals("a-value", metadata.get("b"));
        assertNull(metadata.get("author"));
        assertNull(metadata.get("a"));
    }

    @Test
    public void testDateNormalizingFilter() throws Exception {
        //test that a Date lacking a timezone, if interpreted as Los Angeles, for example,
        //yields a UTC string that is properly +7 hours.
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.CREATED, "2021-07-23T01:02:24");
        DateNormalizingMetadataFilter filter = new DateNormalizingMetadataFilter();
        filter.setDefaultTimeZone("America/Los_Angeles");
        filter.filter(m);
        assertEquals("2021-07-23T08:02:24Z", m.get(TikaCoreProperties.CREATED));
    }

    @Test
    public void testCaptureGroupBasic() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-4133-capture-group.json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "quick brown fox");
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=UTF-8");

        MetadataFilter filter = loader.loadMetadataFilters();
        metadata = filterOne(filter, metadata);
        assertEquals("quick brown fox", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("text/html", metadata.get("mime"));
    }

    @Test
    public void testCaptureGroupNoSemiColon() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-4133-capture-group.json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "quick brown fox");
        metadata.set(Metadata.CONTENT_TYPE, "text/html");

        MetadataFilter filter = loader.loadMetadataFilters();
        metadata = filterOne(filter, metadata);
        assertEquals("quick brown fox", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("text/html", metadata.get("mime"));
    }

    @Test
    public void testCaptureGroupOverwrite() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-4133-capture-group-overwrite.json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "quick brown fox");
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=UTF-8");

        MetadataFilter filter = loader.loadMetadataFilters();
        metadata = filterOne(filter, metadata);
        assertEquals("quick brown fox", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("text/html", metadata.get(Metadata.CONTENT_TYPE));

        // now test that a single match overwrites all the values
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=UTF-8");
        metadata.add(TikaCoreProperties.TIKA_CONTENT.toString(), "text/html; charset=UTF-8");
        metadata.add(TikaCoreProperties.TIKA_CONTENT.toString(), "text/plain; charset=UTF-8");
        metadata.add(TikaCoreProperties.TIKA_CONTENT.toString(), "application/pdf; charset=UTF-8");

        metadata = filterOne(filter, metadata);
        assertEquals(1, metadata.getValues(Metadata.CONTENT_TYPE).length);
        assertEquals("text/html", metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testAttachmentTypeMetadataFilter() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-4261-clear-by-embedded-type.json"));
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.INLINE.name());
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=UTF-8");

        MetadataFilter filter = loader.loadMetadataFilters();
        metadata = filterOne(filter, metadata);
        assertEquals(0, metadata.names().length);

        metadata = new Metadata();
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.ALTERNATE_FORMAT_CHUNK
                .name());
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=UTF-8");
        metadata = filterOne(filter, metadata);
        assertEquals(2, metadata.names().length);
    }

    private static Metadata filterOne(MetadataFilter filter, Metadata singleMetadata) throws TikaException {
        return filter.filter(List.of(singleMetadata)).get(0);
    }
}
