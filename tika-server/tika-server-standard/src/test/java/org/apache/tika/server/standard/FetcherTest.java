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
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.FetcherStreamFactory;
import org.apache.tika.server.core.InputStreamFactory;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;


@Disabled("turn into actual unit tests -- this relies on network connectivity...bad")
public class FetcherTest extends CXFTestBase {

    private static final String META_PATH = "/rmeta";
    private static final String TEXT_PATH = "/text";

    private static final String TEST_RECURSIVE_DOC = "test-documents/test_recursive_embedded.docx";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("/config/tika-config-url-fetcher.xml");
    }

    @Override
    protected InputStreamFactory getInputStreamFactory(InputStream tikaConfigInputStream) {
        try (TikaInputStream tis = TikaInputStream.get(tikaConfigInputStream)) {
            FetcherManager fetcherManager = FetcherManager.load(tis.getPath());
            return new FetcherStreamFactory(fetcherManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testBasic() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH).accept("application/json")
                .acceptEncoding("gzip").header("fetcherName", "url")
                .header("fetchKey", "https://tika.apache.org").put("");

        Reader reader = new InputStreamReader(
                new GzipCompressorInputStream((InputStream) response.getEntity()), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        Metadata parent = metadataList.get(0);
        String txt = parent.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("toolkit detects and extracts metadata", txt);
        assertEquals("Apache Tika – Apache Tika", parent.get(TikaCoreProperties.TITLE));
    }

}
