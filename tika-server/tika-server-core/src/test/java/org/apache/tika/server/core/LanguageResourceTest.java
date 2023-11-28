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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.resource.LanguageResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;

public class LanguageResourceTest extends CXFTestBase {

    private static final String LANG_PATH = "/language";
    private static final String LANG_STREAM_PATH = LANG_PATH + "/stream";
    private static final String LANG_STRING_PATH = LANG_PATH + "/string";
    private static final String ENGLISH_STRING = "This is English!";
    private static final String FRENCH_STRING = "c’est comme ci comme ça";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(LanguageResource.class);
        sf.setResourceProvider(LanguageResource.class,
                new SingletonResourceProvider(new LanguageResource()));

    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);

    }

    @Test
    public void testDetectEnglishString() throws Exception {
        String url = endPoint + LANG_STRING_PATH;
        Response response =
                WebClient.create(url).type("text/plain").accept("text/plain").put(ENGLISH_STRING);
        assertNotNull(response);
        String readLang = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals("en", readLang);
    }

    @Test
    public void testDetectFrenchString() throws Exception {
        String url = endPoint + LANG_STRING_PATH;
        Response response =
                WebClient.create(url).type("text/plain").accept("text/plain").put(FRENCH_STRING);
        assertNotNull(response);
        String readLang = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals("fr", readLang);
    }

    @Test
    public void testDetectEnglishFile() throws Exception {
        String url = endPoint + LANG_STREAM_PATH;
        Response response = WebClient.create(url).type("text/plain").accept("text/plain")
                .put(getClass().getResourceAsStream("/test-documents/english.txt"));
        assertNotNull(response);
        String readLang = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals("en", readLang);
    }

    @Test
    public void testDetectFrenchFile() throws Exception {
        String url = endPoint + LANG_STREAM_PATH;
        Response response = WebClient.create(url).type("text/plain").accept("text/plain")
                .put(getClass().getResourceAsStream("/test-documents/french.txt"));
        assertNotNull(response);
        String readLang = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals("fr", readLang);
    }

}
