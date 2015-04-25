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
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pkg.PackageParser;
import org.apache.tika.server.resource.TikaParsers;
import org.eclipse.jetty.util.ajax.JSON;
import org.gagravarr.tika.OpusParser;
import org.junit.Test;

public class TikaParsersTest extends CXFTestBase {
    private static final String PARSERS_SUMMARY_PATH = "/parsers";
    private static final String PARSERS_DETAILS_PATH = "/parsers/details";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaParsers.class);
        sf.setResourceProvider(
                TikaParsers.class,
                new SingletonResourceProvider(new TikaParsers(tika))
        );
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
    }

    protected String getPath(boolean withDetails) {
        return withDetails ? PARSERS_DETAILS_PATH : PARSERS_SUMMARY_PATH;
    }

    @Test
    public void testGetPlainText() throws Exception {
        for (boolean details : new boolean[]{false, true}) {
            Response response = WebClient
                    .create(endPoint + getPath(details))
                    .type("text/plain")
                    .accept("text/plain")
                    .get();

            String text = getStringFromInputStream((InputStream) response.getEntity());
            assertContains("org.apache.tika.parser.DefaultParser (Composite Parser)", text);
            assertContains(OpusParser.class.getName(), text);
            assertContains(PackageParser.class.getName(), text);
            assertContains(OOXMLParser.class.getName(), text);

            if (details) {
                // Should have the mimetypes they handle
                assertContains("text/plain", text);
                assertContains("application/pdf", text);
                assertContains("audio/ogg", text);
            } else {
                // Shouldn't do
                assertNotFound("text/plain", text);
                assertNotFound("application/pdf", text);
                assertNotFound("audio/ogg", text);
            }
        }
    }

    @Test
    public void testGetHTML() throws Exception {
        for (boolean details : new boolean[]{false, true}) {
            Response response = WebClient
                    .create(endPoint + getPath(details))
                    .type("text/html")
                    .accept("text/html")
                    .get();

            String text = getStringFromInputStream((InputStream) response.getEntity());
            assertContains("<h2>DefaultParser</h2>", text);
            assertContains("Composite", text);

            assertContains("<h3>OpusParser", text);
            assertContains("<h3>PackageParser", text);
            assertContains("<h3>OOXMLParser", text);

            assertContains(OpusParser.class.getName(), text);
            assertContains(PackageParser.class.getName(), text);
            assertContains(OOXMLParser.class.getName(), text);

            if (details) {
                // Should have the mimetypes they handle
                assertContains("<li>text/plain", text);
                assertContains("<li>application/pdf", text);
                assertContains("<li>audio/ogg", text);
            } else {
                // Shouldn't do
                assertNotFound("text/plain", text);
                assertNotFound("application/pdf", text);
                assertNotFound("audio/ogg", text);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetJSON() throws Exception {
        for (boolean details : new boolean[]{false, true}) {
            Response response = WebClient
                    .create(endPoint + getPath(details))
                    .type(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                    .accept(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                    .get();

            String jsonStr = getStringFromInputStream((InputStream) response.getEntity());
            Map<String, Map<String, Object>> json = (Map<String, Map<String, Object>>) JSON.parse(jsonStr);

            // Should have a nested structure
            assertEquals(true, json.containsKey("name"));
            assertEquals(true, json.containsKey("composite"));
            assertEquals(true, json.containsKey("children"));
            assertEquals("org.apache.tika.parser.DefaultParser", json.get("name"));
            assertEquals(Boolean.TRUE, json.get("composite"));

            // At least 20 child parsers which aren't composite
            Object[] children = (Object[]) (Object) json.get("children");
            assertTrue(children.length >= 20);
            boolean hasOpus = false, hasOOXML = false, hasPDF = false, hasZip = false;
            int nonComposite = 0;
            for (Object o : children) {
                Map<String, Object> d = (Map<String, Object>) o;
                assertEquals(true, d.containsKey("name"));
                assertEquals(true, d.containsKey("composite"));
                assertEquals(Boolean.FALSE, d.get("composite"));
                assertEquals(false, d.containsKey("children"));

                if (d.get("composite") == Boolean.FALSE) nonComposite++;

                // Will only have mime types if requested
                assertEquals(details, d.containsKey("supportedTypes"));

                String name = (String) d.get("name");
                if (OpusParser.class.getName().equals(name)) {
                    hasOpus = true;
                }
                if (OOXMLParser.class.getName().equals(name)) {
                    hasOOXML = true;
                }
                if (PDFParser.class.getName().equals(name)) {
                    hasPDF = true;
                }
                if (PackageParser.class.getName().equals(name)) {
                    hasZip = true;
                }
            }
            assertEquals(true, hasOpus);
            assertEquals(true, hasOOXML);
            assertEquals(true, hasPDF);
            assertEquals(true, hasZip);
            assertTrue(nonComposite > 20);
        }
    }
}
