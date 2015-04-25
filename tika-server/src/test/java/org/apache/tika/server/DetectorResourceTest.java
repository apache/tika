/**
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
import static org.junit.Assert.assertNotNull;

import javax.ws.rs.core.Response;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.resource.DetectorResource;
import org.apache.tika.server.writer.TarWriter;
import org.apache.tika.server.writer.ZipWriter;
import org.junit.Test;

public class DetectorResourceTest extends CXFTestBase {

    private static final String DETECT_PATH = "/detect";
    private static final String DETECT_STREAM_PATH = DETECT_PATH + "/stream";
    private static final String FOO_CSV = "foo.csv";
    private static final String CDEC_CSV_NO_EXT = "CDEC_WEATHER_2010_03_02";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(DetectorResource.class);
        sf.setResourceProvider(DetectorResource.class,
                new SingletonResourceProvider(new DetectorResource(tika)));

    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);

    }

    @Test
    public void testDetectCsvWithExt() throws Exception {
        String url = endPoint + DETECT_STREAM_PATH;
        Response response = WebClient
                .create(endPoint + DETECT_STREAM_PATH)
                .type("text/csv")
                .accept("*/*")
                .header("Content-Disposition",
                        "attachment; filename=" + FOO_CSV)
                .put(ClassLoader.getSystemResourceAsStream(FOO_CSV));
        assertNotNull(response);
        String readMime = getStringFromInputStream((InputStream) response
                .getEntity());
        assertEquals("text/csv", readMime);

    }

    @Test
    public void testDetectCsvNoExt() throws Exception {
        String url = endPoint + DETECT_STREAM_PATH;
        Response response = WebClient
                .create(endPoint + DETECT_STREAM_PATH)
                .type("text/csv")
                .accept("*/*")
                .header("Content-Disposition",
                        "attachment; filename=" + CDEC_CSV_NO_EXT)
                .put(ClassLoader.getSystemResourceAsStream(CDEC_CSV_NO_EXT));
        assertNotNull(response);
        String readMime = getStringFromInputStream((InputStream) response
                .getEntity());
        assertEquals("text/plain", readMime);

        // now trick it by adding .csv to the end
        response = WebClient
                .create(endPoint + DETECT_STREAM_PATH)
                .type("text/csv")
                .accept("*/*")
                .header("Content-Disposition",
                        "attachment; filename=" + CDEC_CSV_NO_EXT + ".csv")
                .put(ClassLoader.getSystemResourceAsStream(CDEC_CSV_NO_EXT));
        assertNotNull(response);
        readMime = getStringFromInputStream((InputStream) response.getEntity());
        assertEquals("text/csv", readMime);

    }
}
