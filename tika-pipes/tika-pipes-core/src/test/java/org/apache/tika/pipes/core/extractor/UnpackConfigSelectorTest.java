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
package org.apache.tika.pipes.core.extractor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedBytesSelector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

public class UnpackConfigSelectorTest extends TikaTest {

    @Test
    public void testEmbeddedBytesSelector() throws Exception {
        UnpackConfig config = new UnpackConfig();
        config.setIncludeMimeTypes(Set.of("application/pdf", "application/rtf", "text/plain"));
        config.setIncludeEmbeddedResourceTypes(Set.of("ATTACHMENT", "INLINE"));

        EmbeddedBytesSelector selector = config.createEmbeddedBytesSelector();

        assertFalse(selector.select(getMetadata("", "")));
        assertTrue(selector.select(getMetadata("application/pdf", "")));
        assertTrue(selector.select(getMetadata("application/pdf", "ATTACHMENT")));
        assertTrue(selector.select(getMetadata("application/pdf", "INLINE")));
        assertTrue(selector.select(getMetadata("text/plain;charset=UTF-7", "INLINE")));

        assertFalse(selector.select(getMetadata("application/pdf", "MACRO")));
        assertFalse(selector.select(getMetadata("application/docx", "")));
    }

    @Test
    public void testAcceptAllWhenNoFilters() {
        UnpackConfig config = new UnpackConfig();
        EmbeddedBytesSelector selector = config.createEmbeddedBytesSelector();

        // With no filters, should accept all
        assertTrue(selector.select(getMetadata("application/pdf", "")));
        assertTrue(selector.select(getMetadata("application/docx", "MACRO")));
        assertTrue(selector.select(getMetadata("", "")));
    }

    private Metadata getMetadata(String mime, String embeddedResourceType) {
        Metadata m = new Metadata();
        if (!StringUtils.isBlank(mime)) {
            m.set(Metadata.CONTENT_TYPE, mime);
        }
        if (!StringUtils.isBlank(embeddedResourceType)) {
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, embeddedResourceType);
        }
        return m;
    }
}
