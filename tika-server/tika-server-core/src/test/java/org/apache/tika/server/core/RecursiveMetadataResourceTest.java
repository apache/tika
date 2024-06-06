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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

public class RecursiveMetadataResourceTest extends CXFTestBase {

    private static final String META_PATH = "/rmeta";

    public static final String TEST_NULL_POINTER = "test-documents/mock/null_pointer.xml";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testNPE() throws Exception {
        Response response = WebClient.create(endPoint + META_PATH).accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_NULL_POINTER));

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        Metadata metadata = metadataList.get(0);
        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("some content", metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("null pointer message",
                metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));

    }
    /*
    @Test
    public void testWriteLimitInAll() throws Exception {
        //specify your file directory here
        Path testDocs = Paths.get("..../tika-parsers/src/test/resources/test-documents");
        for (File f : testDocs.toFile().listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
            System.out.println(f.getName());
            testWriteLimit(f);
        }
    }
    private void testWriteLimit(File f) throws Exception {
        Response response = WebClient.create(endPoint + META_PATH+"/text").accept(
                "application/json")
                .put(f);
        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        int totalLen = 0;
        StringBuilder sb = new StringBuilder();
        for (Metadata m : metadataList) {
            String txt = m.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
            sb.append(txt);
            totalLen += (txt == null) ? 0 : txt.length();
        }
        String fullText = sb.toString();
        Random r = new Random();
        for (int i = 0; i < 20; i++) {
            int writeLimit = r.nextInt(totalLen+100);
            response = WebClient.create(endPoint + META_PATH+"/text").accept(
                    "application/json")
                    .header("writeLimit", Integer.toString(writeLimit)).put(f);
            assertEquals(200, response.getStatus());
            reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
            List<Metadata> writeLimitMetadataList = JsonMetadataList.fromJson(reader);
            int len = 0;
            StringBuilder extracted = new StringBuilder();
            for (Metadata m : writeLimitMetadataList) {
                String txt = m.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
                len += (txt == null) ? 0 : txt.length();
                extracted.append(txt);
            }
            if (totalLen > len) {
                boolean wlr = false;
                for (Metadata m : writeLimitMetadataList) {
                    if ("true".equals(m.get(AbstractRecursiveParserWrapperHandler.WRITE_LIMIT_REACHED))) {
                        wlr = true;
                    }
                }
                System.out.println(f.getName() + " actualLen:" + len + " : writeLimit: "
                        + writeLimit + " : totalLen: "+totalLen);
                assertTrue(f.getName() + ": writelimit: " + writeLimit + " len: "+len,
                        len <= writeLimit);
                assertEquals(f.getName() +" writeLimit: " + writeLimit +
                                " : fullLen:" + totalLen + " limitedLen: " +len,
                        true, wlr);
            } else if (len > totalLen) {
                fail("len should never be > totalLen "+len + "  : "+ totalLen);
            }
        }
    }
    */

}
