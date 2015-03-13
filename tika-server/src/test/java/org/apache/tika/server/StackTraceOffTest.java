package org.apache.tika.server;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.resource.DetectorResource;
import org.apache.tika.server.resource.MetadataResource;
import org.apache.tika.server.resource.RecursiveMetadataResource;
import org.apache.tika.server.resource.TikaResource;
import org.apache.tika.server.resource.UnpackerResource;
import org.apache.tika.server.writer.CSVMessageBodyWriter;
import org.apache.tika.server.writer.JSONMessageBodyWriter;
import org.apache.tika.server.writer.TextMessageBodyWriter;
import org.apache.tika.server.writer.XMPMessageBodyWriter;
import org.junit.Assert;
import org.junit.Test;


/**
 * Test to make sure that no stack traces are returned
 * when the stack trace param is set to false.
 */
public class StackTraceOffTest extends CXFTestBase {
    public static final String TEST_NULL = "mock/null_pointer.xml";
    public static final String TEST_PASSWORD_PROTECTED = "password.xls";

    private static final String[] PATHS = new String[]{
            "/tika",
            "/rmeta",
            "/unpack",
            "/meta",
    };
    private static final int UNPROCESSEABLE = 422;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> rCoreProviders = new ArrayList<ResourceProvider>();
        rCoreProviders.add(new SingletonResourceProvider(new MetadataResource(tika)));
        rCoreProviders.add(new SingletonResourceProvider(new RecursiveMetadataResource(tika)));
        rCoreProviders.add(new SingletonResourceProvider(new DetectorResource(tika)));
        rCoreProviders.add(new SingletonResourceProvider(new TikaResource(tika)));
        rCoreProviders.add(new SingletonResourceProvider(new UnpackerResource(tika)));
        sf.setResourceProviders(rCoreProviders);
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new TikaServerParseExceptionMapper(false));
        providers.add(new JSONMessageBodyWriter());
        providers.add(new CSVMessageBodyWriter());
        providers.add(new XMPMessageBodyWriter());
        providers.add(new TextMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testEncrypted() throws Exception {
        for (String path : PATHS) {
            Response response = WebClient
                    .create(endPoint + path)
                    .accept("*/*")
                    .header("Content-Disposition",
                            "attachment; filename=" + TEST_PASSWORD_PROTECTED)
                    .put(ClassLoader.getSystemResourceAsStream(TEST_PASSWORD_PROTECTED));
            assertNotNull("null response: " + path, response);
            assertEquals("unprocessable: " + path, UNPROCESSEABLE, response.getStatus());
            String msg = getStringFromInputStream((InputStream) response
                    .getEntity());
            assertEquals("should be empty: " + path, "", msg);
        }
    }

    @Test
    public void testNullPointerOnTika() throws Exception {
        for (String path : PATHS) {
            Response response = WebClient
                    .create(endPoint + path)
                    .accept("*/*")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_NULL));
            assertNotNull("null response: " + path, response);
            assertEquals("unprocessable: " + path, UNPROCESSEABLE, response.getStatus());
            String msg = getStringFromInputStream((InputStream) response
                    .getEntity());
            assertEquals("should be empty: " + path, "", msg);
        }
    }

    @Test
    public void test415() throws Exception {
        //no stack traces for 415
        for (String path : PATHS) {
            Response response = WebClient
                    .create(endPoint + path)
                    .type("blechdeblah/deblechdeblah")
                    .accept("*/*")
                    .header("Content-Disposition",
                            "attachment; filename=null_pointer.evil")
                    .put(ClassLoader.getSystemResourceAsStream(TEST_NULL));
            assertNotNull("null response: " + path, response);
            assertEquals("bad type: " + path, 415, response.getStatus());
            String msg = getStringFromInputStream((InputStream) response
                    .getEntity());
            assertEquals("should be empty: " + path, "", msg);
        }
    }

    //For now, make sure that non-complete document
    //still returns BAD_REQUEST.  We may want to
    //make MetadataResource return the same types of parse
    //exceptions as the others...
    @Test
    public void testMeta() throws Exception {
        InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

        Response response = WebClient.create(endPoint + "/meta" + "/Author").type("application/msword")
                .accept(MediaType.TEXT_PLAIN).put(copy(stream, 8000));
        Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        String msg = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals("Failed to get metadata field Author", msg);
    }
}
