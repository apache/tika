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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

import org.apache.tika.server.core.resource.TikaServerStatus;
import org.apache.tika.server.core.writer.JSONObjWriter;

public class TikaServerStatusTest extends CXFTestBase {

    private final static String STATUS_PATH = "/status";
    private final static String SERVER_ID = UUID.randomUUID().toString();

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaServerStatus.class);
        sf.setResourceProvider(TikaServerStatus.class, new SingletonResourceProvider(
                new TikaServerStatus(new ServerStatus(SERVER_ID, 0))));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testBasic() throws Exception {
        Response response = WebClient.create(endPoint + STATUS_PATH).get();
        String jsonString = getStringFromInputStream((InputStream) response.getEntity());
        JsonNode root = new ObjectMapper().readTree(jsonString);
        assertTrue(root.has("server_id"));
        assertTrue(root.has("status"));
        assertTrue(root.has("millis_since_last_parse_started"));
        assertTrue(root.has("files_processed"));
        assertEquals("OPERATING", root.get("status").asText());
        assertEquals(0, root.get("files_processed").intValue());
        long millis = root.get("millis_since_last_parse_started").longValue();
        assertTrue(millis > 0 && millis < 360000);
        assertEquals(SERVER_ID, root.get("server_id").asText());
    }
}
