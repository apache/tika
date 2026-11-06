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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.ReadabilityMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

public class ReadabilityConfigTest extends TikaTest {

    private static final String PARA = "<p>The committee met on Tuesday, and after a long debate, it voted to adopt"
            + " the proposal, which had been revised twice since the spring.</p>";
    private static final String PAGE = "<html><head><title>Council vote passes after long debate</title></head><body>"
            + "<nav class=\"menu\"><a href=\"/\">Most read nav link</a></nav>"
            + "<article>" + PARA.repeat(8) + "</article></body></html>";

    @Test
    public void testFromJsonConfig() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-readability.json");
        ParseContext context = loader.loadParseContext();
        CompositeJsoupFilter filters = (CompositeJsoupFilter) context.get(JsoupFilter.class);
        assertEquals(1, filters.getFilters().size());
        assertEquals(5000, ((ReadabilityJsoupFilter) filters.getFilters().get(0)).getMaxElements());

        Parser p = loader.loadAutoDetectParser();
        Metadata m = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(PAGE.getBytes(StandardCharsets.UTF_8))) {
            XMLResult r = getXML(tis, p, m, context);
            assertEquals(ReadabilityMetadata.STATUS_EXTRACTED, r.metadata.get(ReadabilityMetadata.STATUS));
            assertNotContained("Most read nav link", r.xml);
        }
    }

    private static ParseContext fromRequest(String json) throws Exception {
        ParseContext context = ParseContextDeserializer.readParseContext(new ObjectMapper().readTree(json), true);
        ParseContextUtils.resolveAll(context, ReadabilityConfigTest.class.getClassLoader());
        return context;
    }

    @Test
    public void testRequestMayEnableButNotLoosenLimits() throws Exception {
        ParseContext context = fromRequest("{\"readability-jsoup-filter\":{\"requireReaderable\":false}}");
        ReadabilityJsoupFilter filter = (ReadabilityJsoupFilter) context.get(JsoupFilter.class);
        assertFalse(filter.isRequireReaderable());
        assertEquals(ReadabilityJsoupFilter.DEFAULT_MAX_ELEMENTS, filter.getMaxElements());

        for (String limit : new String[]{"maxElements", "maxTextScanned"}) {
            String json = "{\"readability-jsoup-filter\":{\"" + limit + "\":2000000000}}";
            Exception e = assertThrows(Exception.class, () -> fromRequest(json), json);
            assertTrue(e.getMessage().contains("Cannot modify"), e.getMessage());
        }
    }

    @Test
    public void testRequestListForm() throws Exception {
        CompositeJsoupFilter filters = (CompositeJsoupFilter) fromRequest(
                "{\"jsoup-filters\":[\"readability-jsoup-filter\"]}").get(JsoupFilter.class);
        assertEquals(ReadabilityJsoupFilter.class, filters.getFilters().get(0).getClass());

        assertTrue(((CompositeJsoupFilter) fromRequest("{\"jsoup-filters\":[]}").get(JsoupFilter.class))
                .getFilters().isEmpty());

        String loosen = "{\"jsoup-filters\":[{\"readability-jsoup-filter\":{\"maxElements\":2000000000}}]}";
        Exception e = assertThrows(Exception.class, () -> fromRequest(loosen));
        assertTrue(e.getMessage().contains("Cannot modify"), e.getMessage());

        String wrongType = "{\"jsoup-filters\":[\"include-field-metadata-filter\"]}";
        e = assertThrows(Exception.class, () -> fromRequest(wrongType));
        assertTrue(e.getMessage().contains("not assignable"), e.getMessage());
    }

    @Test
    public void testSingleAndListCollide() {
        String both = "{\"readability-jsoup-filter\":{},\"jsoup-filters\":[\"readability-jsoup-filter\"]}";
        Exception e = assertThrows(Exception.class, () -> fromRequest(both));
        assertTrue(e.getMessage().contains("Duplicate parse-context entries"), e.getMessage());
    }

    @Test
    public void testEmbeddedHtmlNotFilteredByDefault() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-readability.json");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("page.html"));
            zos.write(PAGE.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            list = getRecursiveMetadata(tis, loader.loadAutoDetectParser(), new Metadata(), loader.loadParseContext(), true);
        }
        assertEquals(2, list.size());
        Metadata html = list.get(1);
        assertNull(html.get(ReadabilityMetadata.STATUS));
        assertContains("Most read nav link", html.get(TikaCoreProperties.TIKA_CONTENT));
    }
}
