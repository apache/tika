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
import org.apache.tika.server.writer.MetadataListMessageBodyWriter;
import org.apache.tika.server.writer.TextMessageBodyWriter;
import org.apache.tika.server.writer.XMPMessageBodyWriter;
import org.junit.Assert;
import org.junit.Test;

public class StackTraceTest extends CXFTestBase {
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
        rCoreProviders.add(new SingletonResourceProvider(new MetadataResource()));
        rCoreProviders.add(new SingletonResourceProvider(new RecursiveMetadataResource()));
        rCoreProviders.add(new SingletonResourceProvider(new DetectorResource(new ServerStatus("", 0))));
        rCoreProviders.add(new SingletonResourceProvider(new TikaResource()));
        rCoreProviders.add(new SingletonResourceProvider(new UnpackerResource()));
        sf.setResourceProviders(rCoreProviders);
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new TikaServerParseExceptionMapper(true));
        providers.add(new JSONMessageBodyWriter());
        providers.add(new CSVMessageBodyWriter());
        providers.add(new XMPMessageBodyWriter());
        providers.add(new TextMessageBodyWriter());
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Override
    protected boolean isIncludeStackTrace() {
        return true;
    }

    @Test
    public void testEncrypted() throws Exception {
        for (String path : PATHS) {
            if ("/rmeta".equals(path)) {
                continue;
            }
            String accept = "*/*";
            if ("/tika".equals(path)) {
                accept = "text/plain";
            }
            Response response = WebClient
                    .create(endPoint + path)
                    .accept(accept)
                    .header("Content-Disposition",
                            "attachment; filename=" + TEST_PASSWORD_PROTECTED)
                    .put(ClassLoader.getSystemResourceAsStream(TEST_PASSWORD_PROTECTED));
            assertNotNull("null response: " + path, response);
            assertEquals("unprocessable: " + path, UNPROCESSEABLE, response.getStatus());
            String msg = getStringFromInputStream((InputStream) response
                    .getEntity());
            assertContains("org.apache.tika.exception.EncryptedDocumentException",
                    msg);
        }
    }

    @Test
    public void testNullPointerOnTika() throws Exception {
        for (String path : PATHS) {
            if ("/rmeta".equals(path)) {
                continue;
            }
            String accept = "*/*";
            if ("/tika".equals(path)) {
                accept = "text/plain";
            }
            Response response = WebClient
                    .create(endPoint + path)
                    .accept(accept)
                    .put(ClassLoader.getSystemResourceAsStream(TEST_NULL));
            assertNotNull("null response: " + path, response);
            assertEquals("unprocessable: " + path, UNPROCESSEABLE, response.getStatus());
            String msg = getStringFromInputStream((InputStream) response
                    .getEntity());
            assertContains("Caused by: java.lang.NullPointerException: null pointer message",
                    msg);
        }
    }

    @Test
    public void testEmptyParser() throws Exception {
        //As of Tika 1.23, we're no longer returning 415 for file types
        //that don't have a parser
        //no stack traces for 415
        for (String path : PATHS) {

            Response response = WebClient
                    .create(endPoint + path)
                    .accept("*:*")
                    .put(ClassLoader.getSystemResourceAsStream("testDigilite.fdf"));
            if (path.equals("/unpack")) {
                //"NO CONTENT"
                assertEquals("bad type: " + path, 204, response.getStatus());
            } else {
                assertEquals("bad type: " + path, 200, response.getStatus());
                assertNotNull("null response: " + path, response);
            }
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
