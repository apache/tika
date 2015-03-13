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
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.microsoft.POIFSContainerDetector;
import org.apache.tika.parser.pkg.ZipContainerDetector;
import org.apache.tika.server.resource.TikaDetectors;
import org.eclipse.jetty.util.ajax.JSON;
import org.gagravarr.tika.OggDetector;
import org.junit.Test;

public class TikaDetectorsTest extends CXFTestBase {
    private static final String DETECTORS_PATH = "/detectors";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaDetectors.class);
        sf.setResourceProvider(
                TikaDetectors.class,
                new SingletonResourceProvider(new TikaDetectors(tika))
        );
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
    }

    @Test
    public void testGetPlainText() throws Exception {
        Response response = WebClient
                .create(endPoint + DETECTORS_PATH)
                .type("text/plain")
                .accept("text/plain")
                .get();

        String text = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("org.apache.tika.detect.DefaultDetector (Composite Detector)", text);
        assertContains(OggDetector.class.getName(), text);
        assertContains(POIFSContainerDetector.class.getName(), text);
        assertContains(ZipContainerDetector.class.getName(), text);
        assertContains(MimeTypes.class.getName(), text);
    }

    @Test
    public void testGetHTML() throws Exception {
        Response response = WebClient
                .create(endPoint + DETECTORS_PATH)
                .type("text/html")
                .accept("text/html")
                .get();

        String text = getStringFromInputStream((InputStream) response.getEntity());
        assertContains("<h2>DefaultDetector</h2>", text);
        assertContains("Composite", text);

        assertContains("<h3>OggDetector", text);
        assertContains("<h3>POIFSContainerDetector", text);
        assertContains("<h3>MimeTypes", text);

        assertContains(OggDetector.class.getName(), text);
        assertContains(POIFSContainerDetector.class.getName(), text);
        assertContains(ZipContainerDetector.class.getName(), text);
        assertContains(MimeTypes.class.getName(), text);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetJSON() throws Exception {
        Response response = WebClient
                .create(endPoint + DETECTORS_PATH)
                .type(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                .accept(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                .get();

        String jsonStr = getStringFromInputStream((InputStream) response.getEntity());
        Map<String, Map<String, Object>> json = (Map<String, Map<String, Object>>) JSON.parse(jsonStr);

        // Should have a nested structure
        assertTrue(json.containsKey("name"));
        assertTrue(json.containsKey("composite"));
        assertTrue(json.containsKey("children"));
        assertEquals("org.apache.tika.detect.DefaultDetector", json.get("name"));
        assertEquals(Boolean.TRUE, json.get("composite"));

        // At least 4 child detectors, none of them composite
        Object[] children = (Object[]) (Object) json.get("children");
        assertTrue(children.length >= 4);
        boolean hasOgg = false, hasPOIFS = false, hasZIP = false, hasMime = false;
        for (Object o : children) {
            Map<String, Object> d = (Map<String, Object>) o;
            assertTrue(d.containsKey("name"));
            assertTrue(d.containsKey("composite"));
            assertEquals(Boolean.FALSE, d.get("composite"));
            assertEquals(false, d.containsKey("children"));

            String name = (String) d.get("name");
            if (OggDetector.class.getName().equals(name)) {
                hasOgg = true;
            }
            if (POIFSContainerDetector.class.getName().equals(name)) {
                hasPOIFS = true;
            }
            if (ZipContainerDetector.class.getName().equals(name)) {
                hasZIP = true;
            }
            if (MimeTypes.class.getName().equals(name)) {
                hasMime = true;
            }
        }
        assertTrue(hasOgg);
        assertTrue(hasPOIFS);
        assertTrue(hasZIP);
        assertTrue(hasMime);
    }
}
