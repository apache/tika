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
package org.apache.tika.server.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

public class TikaResourceFetcherTest extends CXFTestBase {

    private static final String TIKA_PATH = "/tika";


    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class, new SingletonResourceProvider(new TikaResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper(false));
        providers.add(new JSONMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        Path inputDir = null;
        try {
            inputDir = Paths.get(TikaResourceFetcherTest.class
                    .getResource("/test-documents/")
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String configXML = getStringFromInputStream(TikaResourceFetcherTest.class.getResourceAsStream("/configs/tika-config-server-fetcher-template.xml"));
        configXML = configXML.replace("{FETCHER_BASE_PATH}", inputDir
                .toAbsolutePath()
                .toString());

        configXML = configXML.replace("{PORT}", "9998");
        return new ByteArrayInputStream(configXML.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected InputStreamFactory getInputStreamFactory(InputStream is) {
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            FetcherManager fetcherManager = FetcherManager.load(tis.getPath());
            return new FetcherStreamFactory(fetcherManager);
        } catch (IOException | TikaConfigException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHeader() throws Exception {
        MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
        map.putSingle("fetcherName", "fsf");
        map.putSingle("fetchKey", "mock/hello_world.xml");
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .headers(map)
                .accept("text/xml")
                .put(null);
        String xml = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("hello world", xml);
    }

    @Test
    public void testQueryPart() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .query("fetcherName", "fsf")
                .query("fetchKey", "mock/hello_world.xml")
                .accept("text/xml")
                .put(null);
        String xml = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("hello world", xml);
    }

    @Test
    @Disabled("Apache's Hudson does not like the test file or the utf-8 in this source file")
    public void testNonAsciiInQueryParameters() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .query("fetcherName", "fsf")
                .query("fetchKey", "mock/中文.xml")
                .accept("text/xml")
                .put(null);
        String xml = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("你好世界", xml);
    }

    @Test
    @Disabled("Apache's Hudson does not like the test file or the utf-8 in this source file")
    public void testNonAsciiUrlEncodedInQueryParameters() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH)
                .query("fetcherName", "fsf")
                .query("fetchKey", "mock/%E4%B8%AD%E6%96%87.xml")
                .accept("text/xml")
                .put(null);
        String xml = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("你好世界", xml);
    }

}
