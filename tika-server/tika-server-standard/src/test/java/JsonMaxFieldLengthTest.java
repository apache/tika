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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.writer.JSONMessageBodyWriter;

public class JsonMaxFieldLengthTest extends CXFTestBase {

    private static final String TIKA_PATH = "/tika";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class,
                new SingletonResourceProvider(new TikaResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper(false));
        providers.add(new JSONMessageBodyWriter());
        sf.setProviders(providers);
    }
    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("/config/tika-config-json.xml");
    }

    @Test
    public void testLargeJson(@TempDir Path dir) throws Exception {
        //TIKA-4154
        TikaConfig tikaConfig = null;
        try (InputStream is =
                     JsonMetadata.class.getResourceAsStream("/config/tika-config-json.xml")) {
            tikaConfig = new TikaConfig(is);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30000000; i++) {
            sb.append("v");
        }
        Path tmp = Files.createTempFile(dir, "long-json-", ".txt");
        Files.write(tmp, sb.toString().getBytes(UTF_8));
        Response response =
                WebClient.create(endPoint + TIKA_PATH + "/text").accept("application/json")
                        .put(Files.newInputStream(tmp));
        Metadata metadata = JsonMetadata.fromJson(
                new InputStreamReader(((InputStream) response.getEntity()),
                        StandardCharsets.UTF_8));
        String t = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertEquals(30000000, t.trim().length());
    }
}
