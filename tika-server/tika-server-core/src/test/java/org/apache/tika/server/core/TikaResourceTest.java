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

import org.apache.cxf.attachment.AttachmentUtil;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.core.resource.TikaResource;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_ENCODING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TikaResourceTest extends CXFTestBase {
    public static final String TEST_HELLO_WORLD = "test-documents/mock/hello_world.xml";
    public static final String TEST_OOM = "test-documents/mock/fake_oom.xml";

    private static final String STREAM_CLOSED_FAULT = "java.io.IOException: Stream Closed";

    private static final String TIKA_PATH = "/tika";
    private static final int UNPROCESSEABLE = 422;

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
        sf.setProviders(providers);
    }

    @Test
    public void testHelloWorld() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("text/plain").accept("text/plain").get();
        assertEquals(TikaResource.GREETING,
                getStringFromInputStream((InputStream) response.getEntity()));
    }


    @Test
    public void testJAXBAndActivationDependency() {
        //TIKA-2778
        AttachmentUtil.getCommandMap();
    }

    @Test
    public void testOOMInLegacyMode() throws Exception {

        Response response = null;
        try {
            response = WebClient
                    .create(endPoint + TIKA_PATH)
                    .accept("text/plain")
                    .put(ClassLoader
                            .getSystemResourceAsStream(TEST_OOM));
        } catch (Exception e) {
            //oom may or may not cause an exception depending
            //on the timing
        }

        response = WebClient
                .create(endPoint + TIKA_PATH)
                .accept("text/plain")
                .put(ClassLoader
                        .getSystemResourceAsStream(TEST_HELLO_WORLD));
        String responseMsg = getStringFromInputStream((InputStream) response.getEntity());

        assertContains("hello world", responseMsg);
    }
    @Test
    public void testApplicationWadl() throws Exception {
        Response response = WebClient
                .create(endPoint + TIKA_PATH + "?_wadl")
                .accept("text/plain").get();
        String resp = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(resp.startsWith("<application"));
    }
}
