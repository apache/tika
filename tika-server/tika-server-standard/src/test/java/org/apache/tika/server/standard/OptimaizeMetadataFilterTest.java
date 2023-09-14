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

package org.apache.tika.server.standard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

public class OptimaizeMetadataFilterTest extends CXFTestBase {

    private static final String TIKA_PATH = "/tika";
    private static final String META_PATH = "/rmeta";
    private static final String TEST_RECURSIVE_DOC = "test-documents/test_recursive_embedded.docx";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class, TikaResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource()));
        sf.setResourceProvider(TikaResource.class,
                new SingletonResourceProvider(new TikaResource()));

    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        providers.add(new JSONMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream(
                "/config/tika-config-langdetect-optimaize-filter.xml");
    }

    @Test
    public void testMeta() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);

        assertEquals(12, metadataList.size());
        assertEquals("Microsoft Office Word",
                metadataList.get(0).get(OfficeOpenXMLExtended.APPLICATION));
        assertContains("plundered our seas", metadataList.get(6).get("X-TIKA:content"));

        assertEquals("a38e6c7b38541af87148dee9634cb811",
                metadataList.get(10).get("X-TIKA:digest:MD5"));

        assertEquals("en", metadataList.get(6).get(TikaCoreProperties.TIKA_DETECTED_LANGUAGE));
        assertEquals("HIGH",
                metadataList.get(6).get(TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE));
    }

    @Test
    public void testTika() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        Metadata metadata = JsonMetadata.fromJson(reader);
        assertEquals("en", metadata.get(TikaCoreProperties.TIKA_DETECTED_LANGUAGE));
        assertEquals("HIGH", metadata.get(TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE));
    }
}
