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

import org.apache.tika.server.core.resource.TranslateResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;

public class TranslateResourceTest extends CXFTestBase {

    private static final String TRANSLATE_PATH = "/translate";
    private static final String TRANSLATE_ALL_PATH = TRANSLATE_PATH + "/all";
    private static final String TRANSLATE_TXT = "This won't translate";
    private static final String LINGO_PATH =
            "/org.apache.tika.language.translate.impl.Lingo24Translator";
    private static final String SRCDEST = "/es/en";
    private static final String DEST = "/en";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TranslateResource.class);
        sf.setResourceProvider(TranslateResource.class,
                new SingletonResourceProvider(new TranslateResource(
                        new ServerStatus("", 0), 60000)));

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
    public void testTranslateFull() throws Exception {
        String url = endPoint + TRANSLATE_ALL_PATH + LINGO_PATH + SRCDEST;
        Response response =
                WebClient.create(url).type("text/plain").accept("*/*").put(TRANSLATE_TXT);
        assertNotNull(response);
        String translated = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals(TRANSLATE_TXT, translated);
    }

    @Test
    public void testTranslateAutoLang() throws Exception {
        String url = endPoint + TRANSLATE_ALL_PATH + LINGO_PATH + DEST;
        Response response =
                WebClient.create(url).type("text/plain").accept("*/*").put(TRANSLATE_TXT);
        assertNotNull(response);
        String translated = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals(TRANSLATE_TXT, translated);
    }

}
