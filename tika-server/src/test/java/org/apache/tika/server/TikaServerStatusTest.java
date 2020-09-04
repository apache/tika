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
package org.apache.tika.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.resource.RecursiveMetadataResource;
import org.apache.tika.server.resource.TikaResource;
import org.apache.tika.server.resource.TikaServerStatus;
import org.apache.tika.server.writer.JSONMessageBodyWriter;
import org.apache.tika.server.writer.JSONObjWriter;
import org.apache.tika.server.writer.MetadataListMessageBodyWriter;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TikaServerStatusTest extends CXFTestBase {

    private final static String STATUS_PATH = "/status";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaServerStatus.class);
        sf.setResourceProvider(TikaServerStatus.class,
                new SingletonResourceProvider(new TikaServerStatus(new ServerStatus())));
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
        String jsonString =
                getStringFromInputStream((InputStream) response.getEntity());
        JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
        assertTrue(root.has("status"));
        assertTrue(root.has("millis_since_last_parse_started"));
        assertTrue(root.has("files_processed"));
        assertEquals("OPERATING", root.getAsJsonPrimitive("status").getAsString());
        assertEquals(0, root.getAsJsonPrimitive("files_processed").getAsInt());
        long millis = root.getAsJsonPrimitive("millis_since_last_parse_started").getAsInt();
        assertTrue(millis > 0 && millis < 360000);
    }
}
