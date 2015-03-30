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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.resource.TikaResource;
import org.junit.Test;

public class TikaResourceTest extends CXFTestBase {
    public static final String TEST_DOC = "test.doc";
    public static final String TEST_XLSX = "16637.xlsx";
    public static final String TEST_PASSWORD_PROTECTED = "password.xls";
    private static final String TEST_RECURSIVE_DOC = "test_recursive_embedded.docx";

    private static final String TIKA_PATH = "/tika";
    private static final int UNPROCESSEABLE = 422;

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaResource.class);
        sf.setResourceProvider(TikaResource.class,
                new SingletonResourceProvider(new TikaResource(tika)));
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
    public void testSimpleWord() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/msword")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
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

    @Test
    public void testPasswordXLS() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream("password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordHTML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/msword")
                .accept("text/html")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testPasswordXLSHTML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/html")
                .put(ClassLoader.getSystemResourceAsStream("password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordXML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/msword")
                .accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testPasswordXLSXML() throws Exception {
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .type("application/vnd.ms-excel")
                .accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream("password.xls"));

        assertEquals(UNPROCESSEABLE, response.getStatus());
    }

    @Test
    public void testSimpleWordMultipartXML() throws Exception {
        ClassLoader.getSystemResourceAsStream(TEST_DOC);
        Attachment attachmentPart =
                new Attachment("myworddoc", "application/msword", ClassLoader.getSystemResourceAsStream(TEST_DOC));
        WebClient webClient = WebClient.create(endPoint + TIKA_PATH + "/form");
        Response response = webClient.type("multipart/form-data")
                .accept("text/xml")
                .post(attachmentPart);
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("test"));
    }

    @Test
    public void testEmbedded() throws Exception {
        //first try text
        Response response = WebClient.create(endPoint + TIKA_PATH)
                .accept("text/plain")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        String responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("Course of human events"));

        //now go for xml -- different call than text
        response = WebClient.create(endPoint + TIKA_PATH)
                .accept("text/xml")
                .put(ClassLoader.getSystemResourceAsStream(TEST_RECURSIVE_DOC));
        responseMsg = getStringFromInputStream((InputStream) response
                .getEntity());
        assertTrue(responseMsg.contains("Course of human events"));
    }

}
