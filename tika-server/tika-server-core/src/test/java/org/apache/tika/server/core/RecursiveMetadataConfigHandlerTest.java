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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

/** Handler selection on /rmeta's multipart config endpoint: by path segment and by config part. */
public class RecursiveMetadataConfigHandlerTest extends CXFTestBase {

    private static final String META_PATH = "/rmeta";
    private static final String TEST_DOC = "test-documents/mock/hello_world.xml";
    private static final String XML_FACTORY =
            "{\"basic-content-handler-factory\": {\"type\": \"XML\"}}";

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true;
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(RecursiveMetadataResource.class);
        sf.setResourceProvider(RecursiveMetadataResource.class,
                new SingletonResourceProvider(new RecursiveMetadataResource(tikaResource)));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new MetadataListMessageBodyWriter());
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new BadRequestExceptionMapper());
        sf.setProviders(providers);
    }

    @Test
    public void testConfigPartDefaultsToMarkdown() throws Exception {
        String content = rmetaConfigContent(null, null);
        assertTrue(content.contains("hello world"), content);
        assertFalse(content.contains("<p>"), "default should be markdown, was: " + content);
    }

    @Test
    public void testConfigPartCanSelectXmlHandler() throws Exception {
        String content = rmetaConfigContent(null, XML_FACTORY);
        assertTrue(content.contains("<p>hello world</p>"),
                "config part should be able to select the XML handler, was: " + content);
    }

    @Test
    public void testPathSegmentCanSelectXmlHandler() throws Exception {
        String content = rmetaConfigContent("xml", null);
        assertTrue(content.contains("<p>hello world</p>"),
                "config/<handler> should select the XML handler, was: " + content);
    }

    /** A config part that names no handler is not a conflict. */
    @Test
    public void testPathSegmentAppliesAlongsideAConfigPart() throws Exception {
        String content = rmetaConfigContent("xml", "{\"output-limits\": {\"writeLimit\": 100}}");
        assertTrue(content.contains("<p>hello world</p>"),
                "config/<handler> should still apply when a config part is present, was: " + content);
    }

    /** Naming the handler in the path and in the config part is a 400, not a silent pick. */
    @Test
    public void testPathSegmentPlusConfigFactoryIsBadRequest() throws Exception {
        assertEquals(400, rmetaConfigResponse("markdown", XML_FACTORY).getStatus());
        assertEquals(400, rmetaConfigResponse("xml", XML_FACTORY).getStatus());
    }

    @Test
    public void testUnrecognizedHandlerIsBadRequest() throws Exception {
        assertEquals(400, rmetaConfigResponse("somethingOrOther", null).getStatus());
    }

    @Test
    public void testFormTakesHandlerAsItsOwnPathSegment() throws Exception {
        assertTrue(formContent("/xml").contains("<p>hello world</p>"));
        assertFalse(formContent("").contains("<p>"));
    }

    /** TIKA-1716 built /rmeta/form{handler} as one segment, which also matched /rmeta/formxml. */
    @Test
    public void testFormHandlerIsNotAnUndelimitedSuffix() throws Exception {
        assertEquals(405, formResponse("xml").getStatus());
    }

    private String formContent(String handlerSuffix) throws Exception {
        Response response = formResponse(handlerSuffix);
        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        return JsonMetadataList.fromJson(reader).get(0).get(TikaCoreProperties.TIKA_CONTENT);
    }

    private Response formResponse(String suffix) throws Exception {
        ContentDisposition fileCd =
                new ContentDisposition("form-data; name=\"file\"; filename=\"hello_world.xml\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC), fileCd);
        return WebClient
                .create(endPoint + META_PATH + "/form" + suffix)
                .type("multipart/form-data")
                .accept("application/json")
                .post(fileAtt);
    }

    private String rmetaConfigContent(String handlerSuffix, String configJson) throws Exception {
        Response response = rmetaConfigResponse(handlerSuffix, configJson);
        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        return metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
    }

    private Response rmetaConfigResponse(String handlerSuffix, String configJson) throws Exception {
        ContentDisposition fileCd =
                new ContentDisposition("form-data; name=\"file\"; filename=\"hello_world.xml\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream(TEST_DOC), fileCd);
        List<Attachment> atts = new ArrayList<>();
        atts.add(fileAtt);
        if (configJson != null) {
            atts.add(new Attachment("config", "application/json",
                    new ByteArrayInputStream(configJson.getBytes(UTF_8))));
        }

        String path = META_PATH + "/config" + (handlerSuffix == null ? "" : "/" + handlerSuffix);
        return WebClient
                .create(endPoint + path)
                .type("multipart/form-data")
                .accept("application/json")
                .post(new MultipartBody(atts));
    }
}
