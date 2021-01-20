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

package org.apache.tika.server.classic;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.FetcherStreamFactory;
import org.apache.tika.server.core.InputStreamFactory;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Ignore("turn into actual unit tests")
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
    protected InputStreamFactory getInputStreamFactory(TikaConfig tikaConfig) {
        return new FetcherStreamFactory(tikaConfig.getFetcher());
    }

    @Test
    public void testBasic() throws Exception {
        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .acceptEncoding("gzip")
                .header("fetcherString", "url:https://tika.apache.org").put("");

        Reader reader = new InputStreamReader(new GzipCompressorInputStream((InputStream) response.getEntity()), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        TikaTest.debug(metadataList);
    }

}
