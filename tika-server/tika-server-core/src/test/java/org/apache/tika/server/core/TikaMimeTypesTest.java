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

import java.io.InputStream;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.resource.TikaMimeTypes;

public class TikaMimeTypesTest extends CXFTestBase {


    private static final String MIMETYPES_PATH = "/mime-types";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaMimeTypes.class);
        sf.setResourceProvider(TikaMimeTypes.class,
                new SingletonResourceProvider(new TikaMimeTypes()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
    }

    @Test
    public void testGetPlainText() throws Exception {
        Response response =
                WebClient.create(endPoint + MIMETYPES_PATH).type("text/plain").accept("text/plain")
                        .get();

        String text = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("text/plain", text);
        assertContains("application/xml", text);
        assertContains("video/x-ogm", text);

        assertContains("supertype: video/ogg", text);

        assertContains("alias:     image/x-ms-bmp", text);
    }

    @Test
    public void testGetHTML() throws Exception {
        Response response =
                WebClient.create(endPoint + MIMETYPES_PATH).type("text/html").accept("text/html")
                        .get();

        String text = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("text/plain", text);
        assertContains("application/xml", text);
        assertContains("video/x-ogm", text);

        assertContains("<h2>", text);
        assertContains("/text/plain\">", text);
        assertContains("name=\"text/plain", text);

        assertContains("Super Type: <a href=\"#video/ogg\">video/ogg", text);

        assertContains("Alias: image/x-ms-bmp", text);
        assertContains("Description: Ogg Vorbis", text);
        assertContains("Extension: .ogg", text);
    }

    @Test
    public void testGetHTMLDetails() throws Exception {
       Response response =
             WebClient.create(endPoint + MIMETYPES_PATH + "/application/cbor")
                      .type("text/html").accept("text/html").get();

       String text = getStringFromInputStream((InputStream) response.getEntity());
       assertNotFound("text/plain", text);
       assertContains("application/cbor", text);

       assertContains("Acronym: CBOR", text);
       assertContains("Link: http://tools.ietf.org/html/rfc7049", text);
       assertContains("Extension: .cbor", text);
    }
}
