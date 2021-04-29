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

import static org.apache.tika.TikaTest.assertNotContained;
import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.server.resource.TikaResource;
import org.apache.tika.server.writer.JSONMessageBodyWriter;
import org.apache.tika.utils.ParserUtils;

public class TikaResourceNoStackTest extends CXFTestBase {

    public static final String TEST_HELLO_WORLD_LONG = "mock/hello_world_long.xml";
    public static final String TEST_NULL_POINTER = "mock/null_pointer.xml";

    private static final String TIKA_PATH = "/tika";

    @Override
    protected boolean isIncludeStackTrace() {
        return false;
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class,
                new SingletonResourceProvider(new TikaResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new TikaServerParseExceptionMapper(false));
        providers.add(new JSONMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testJsonNPE() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH).accept(
                "application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL_POINTER));
        assertEquals(422, response.getStatus());
        String content = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals(0, content.length());
    }

    @Test
    public void testJsonWriteLimit() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .header("writeLimit", "100")
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD_LONG));
        assertEquals(200, response.getStatus());
        Metadata metadata =
                JsonMetadata.fromJson(new InputStreamReader((InputStream) response.getEntity(),
                        StandardCharsets.UTF_8));
        assertEquals("true", metadata.get(
                AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED));
        assertContains("When in the Course of human events",
                metadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertNotContained("political bands",
                metadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
    }

}
