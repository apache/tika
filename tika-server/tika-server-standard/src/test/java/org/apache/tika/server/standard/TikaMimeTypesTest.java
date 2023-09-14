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
package org.apache.tika.server.standard;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.CXFTestBase;
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
    @SuppressWarnings("unchecked")
    public void testGetJSON() throws Exception {
        Response response = WebClient.create(endPoint + MIMETYPES_PATH)
                .type(javax.ws.rs.core.MediaType.APPLICATION_JSON)
                .accept(javax.ws.rs.core.MediaType.APPLICATION_JSON).get();

        String jsonStr = getStringFromInputStream((InputStream) response.getEntity());
        Map<String, Map<String, Object>> json =
                new ObjectMapper().readerFor(Map.class).readValue(jsonStr);

        assertEquals(true, json.containsKey("text/plain"));
        assertEquals(true, json.containsKey("application/xml"));
        assertEquals(true, json.containsKey("video/x-ogm"));
        assertEquals(true, json.containsKey("image/bmp"));

        Map<String, Object> bmp = json.get("image/bmp");
        assertEquals(true, bmp.containsKey("alias"));
        List<Object> aliases = (List) bmp.get("alias");
        assertEquals(2, aliases.size());

        assertEquals("image/x-bmp", aliases.get(0));
        assertEquals("image/x-ms-bmp", aliases.get(1));

        String whichParser = bmp.get("parser").toString();
        assertTrue(whichParser.equals("org.apache.tika.parser.ocr.TesseractOCRParser") ||
                                whichParser.equals("org.apache.tika.parser.image.ImageParser"),
                "Which parser");

        Map<String, Object> ogm = json.get("video/x-ogm");
        assertEquals("video/ogg", ogm.get("supertype"));
        assertEquals("org.gagravarr.tika.OggParser", ogm.get("parser"));
    }

}
