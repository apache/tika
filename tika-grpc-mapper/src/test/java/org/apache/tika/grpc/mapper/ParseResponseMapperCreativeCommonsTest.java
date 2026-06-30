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
package org.apache.tika.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.CreativeCommonsMetadata;
import org.apache.tika.grpc.v1.ParseResponse;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPRights;

class ParseResponseMapperCreativeCommonsTest {

    @Test
    void overlaysCreativeCommonsOnGenericDocument() {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(XMPRights.WEB_STATEMENT, "http://creativecommons.org/licenses/by/4.0/");
        metadata.set(XMPRights.USAGE_TERMS, "Creative Commons Attribution 4.0");
        metadata.set(XMPRights.MARKED, "True");
        metadata.set(XMPRights.OWNER, "Example Author");

        ParseResponse response = ParseResponseMapper.map(
                metadata, List.of(metadata), "body text", "cc-doc", "OK", 1L);

        assertTrue(response.hasGeneric());
        assertTrue(response.hasCreativeCommons());
        CreativeCommonsMetadata cc = response.getCreativeCommons();
        assertEquals("http://creativecommons.org/licenses/by/4.0/", cc.getWebStatement());
        assertEquals("Creative Commons Attribution 4.0", cc.getUsageTerms());
        assertTrue(cc.getRightsMarked());
        assertEquals("Example Author", cc.getRightsOwners(0));
    }

    @Test
    void builderMapsXmpRightsFieldsDirectly() {
        Metadata metadata = new Metadata();
        metadata.set(XMPRights.CERTIFICATE, "cert-ref");
        metadata.set(XMPRights.WEB_STATEMENT, "http://example.org/license");

        CreativeCommonsMetadata cc = org.apache.tika.grpc.mapper.builders.CreativeCommonsMetadataBuilder
                .build(metadata, "test.Parser", "4.0", java.util.Set.of());

        assertEquals("cert-ref", cc.getRightsCertificate());
        assertEquals("http://example.org/license", cc.getWebStatement());
    }

}
