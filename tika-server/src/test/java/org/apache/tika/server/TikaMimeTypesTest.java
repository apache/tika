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
import java.util.Map;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.resource.TikaMimeTypes;
import org.eclipse.jetty.util.ajax.JSON;
import org.junit.Test;

public class TikaMimeTypesTest extends CXFTestBase {
    private static final String MIMETYPES_PATH = "/mime-types";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaMimeTypes.class);
        sf.setResourceProvider(
                TikaMimeTypes.class,
                new SingletonResourceProvider(new TikaMimeTypes(tika))
        );
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
    }

    @Test
    public void testGetPlainText() throws Exception {
        Response response = WebClient
                .create(endPoint + MIMETYPES_PATH)
                .type("text/plain")
                .accept("text/plain")
                .get();

        String text = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("text/plain", text);
        assertContains("application/xml", text);
        assertContains("video/x-ogm", text);

        assertContains("supertype: video/ogg", text);

        assertContains("alias:     image/bmp", text);
    }

    @Test
    public void testGetHTML() throws Exception {
        Response response = WebClient
                .create(endPoint + MIMETYPES_PATH)
                .type("text/html")
                .accept("text/html")
                .get();

        String text = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("text/plain", text);
        assertContains("application/xml", text);
        assertContains("video/x-ogm", text);

        assertContains("<h2>text/plain", text);
        assertContains("name=\"text/plain", text);

        assertContains("Super Type: <a href=\"#video/ogg\">video/ogg", text);

        assertContains("Alias: image/bmp", text);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetJSON() throws Exception {
        Response response = WebClient
                .create(endPoint + MIMETYPES_PATH)
                .type(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                .accept(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                .get();

        String jsonStr = getStringFromInputStream((InputStream) response.getEntity());
        Map<String, Map<String, Object>> json = (Map<String, Map<String, Object>>) JSON.parse(jsonStr);

        assertEquals(true, json.containsKey("text/plain"));
        assertEquals(true, json.containsKey("application/xml"));
        assertEquals(true, json.containsKey("video/x-ogm"));
        assertEquals(true, json.containsKey("image/x-ms-bmp"));

        Map<String, Object> bmp = json.get("image/x-ms-bmp");
        assertEquals(true, bmp.containsKey("alias"));
        Object[] aliases = (Object[]) bmp.get("alias");
        assertEquals(1, aliases.length);
        assertEquals("image/bmp", aliases[0]);

        String whichParser = bmp.get("parser").toString();
        assertTrue("Which parser", whichParser.equals("org.apache.tika.parser.ocr.TesseractOCRParser") ||
                whichParser.equals("org.apache.tika.parser.image.ImageParser"));

        Map<String, Object> ogm = json.get("video/x-ogm");
        assertEquals("video/ogg", ogm.get("supertype"));
        assertEquals("org.gagravarr.tika.OggParser", ogm.get("parser"));
    }
}
