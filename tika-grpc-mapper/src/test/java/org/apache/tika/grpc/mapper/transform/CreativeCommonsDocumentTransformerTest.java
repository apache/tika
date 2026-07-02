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
package org.apache.tika.grpc.mapper.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPRights;

/**
 * Verifies {@link CreativeCommonsDocumentTransformer} against a synthetic XMP rights
 * metadata set (this module's test-document corpus has no dedicated CC-licensed fixture),
 * and confirms it does not fire on documents with no rights-related metadata at all.
 */
class CreativeCommonsDocumentTransformerTest {

    @Test
    void mapsRightsAndLicenseUrlAndTagsTheRest() {
        Metadata metadata = new Metadata();
        metadata.set(XMPRights.MARKED, "True");
        metadata.set(XMPRights.WEB_STATEMENT, "https://creativecommons.org/licenses/by/4.0/");
        metadata.set(TikaCoreProperties.RIGHTS, "Creative Commons Attribution 4.0");

        CreativeCommonsDocumentTransformer transformer = new CreativeCommonsDocumentTransformer();
        assertTrue(transformer.appliesTo(metadata));

        Document.Builder builder = Document.newBuilder();
        java.util.Set<String> consumed = new java.util.HashSet<>();
        transformer.transform(metadata, builder, consumed);
        MetadataTagger.appendTail(metadata, consumed, builder);

        assertEquals("https://creativecommons.org/licenses/by/4.0/", builder.getMetadata().getLicenseUrl());
        assertEquals("Creative Commons Attribution 4.0", builder.getMetadata().getRights());
        assertTrue(builder.getExtraCount() > 0);
    }

    @Test
    void doesNotApplyToDocumentsWithNoRightsMetadata() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        CreativeCommonsDocumentTransformer transformer = new CreativeCommonsDocumentTransformer();
        assertFalse(transformer.appliesTo(metadata));
    }
}
