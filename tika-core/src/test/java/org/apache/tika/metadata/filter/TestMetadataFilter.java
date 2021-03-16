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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import org.apache.tika.config.AbstractTikaConfigTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class TestMetadataFilter extends AbstractTikaConfigTest {

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    @Test
    public void testDefault() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");

        MetadataFilter defaultFilter = new DefaultMetadataFilter();
        defaultFilter.filter(metadata);

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
        filter.filter(metadata);
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
        filter.filter(metadata);
        assertEquals(1, metadata.names().length);
        assertEquals("author", metadata.get("author"));
        assertNull(metadata.get("title"));
    }

    @Test
    public void testConfigIncludeFilter() throws Exception {
        TikaConfig config = getConfig("TIKA-3137-include.xml");
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");
        metadata.set("content", "content");

        config.getMetadataFilter().filter(metadata);

        assertEquals(2, metadata.size());
        assertEquals("title", metadata.get("title"));
        assertEquals("author", metadata.get("author"));
    }

    @Test
    public void testConfigExcludeFilter() throws Exception {
        TikaConfig config = getConfig("TIKA-3137-exclude.xml");
        Metadata metadata = new Metadata();
        metadata.set("title", "title");
        metadata.set("author", "author");
        metadata.set("content", "content");

        config.getMetadataFilter().filter(metadata);

        assertEquals(1, metadata.size());
        assertEquals("content", metadata.get("content"));
    }

    @Test
    public void testConfigIncludeAndUCFilter() throws Exception {
        TikaConfig config = getConfig("TIKA-3137-include-uc.xml");
        String[] expectedTitles = new String[]{"TITLE1", "TITLE2", "TITLE3"};
        Metadata metadata = new Metadata();
        metadata.add("title", "title1");
        metadata.add("title", "title2");
        metadata.add("title", "title3");
        metadata.set("author", "author");
        metadata.set("content", "content");

        config.getMetadataFilter().filter(metadata);

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
        filter.filter(metadata);
        assertEquals(0, metadata.size());

        metadata.set(Metadata.CONTENT_TYPE, MediaType.text("plain").toString());
        metadata.set("author", "author");
        filter.filter(metadata);
        assertEquals(2, metadata.size());
        assertEquals("author", metadata.get("author"));

    }

    @Test
    public void testMimeClearingFilterConfig() throws Exception {
        TikaConfig config = getConfig("TIKA-3137-mimes-uc.xml");

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, MediaType.image("jpeg").toString());
        metadata.set("author", "author");

        MetadataFilter filter = config.getMetadataFilter();
        filter.filter(metadata);
        debug(metadata);
        assertEquals(0, metadata.size());

        metadata.set(Metadata.CONTENT_TYPE, MediaType.text("plain").toString());
        metadata.set("author", "author");
        filter.filter(metadata);
        assertEquals(2, metadata.size());
        assertEquals("AUTHOR", metadata.get("author"));

    }
}
