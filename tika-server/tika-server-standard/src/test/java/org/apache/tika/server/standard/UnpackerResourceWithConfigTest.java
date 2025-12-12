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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.UnpackerResource;
import org.apache.tika.server.core.writer.TarWriter;
import org.apache.tika.server.core.writer.ZipWriter;

public class UnpackerResourceWithConfigTest extends CXFTestBase {
    private static final String BASE_PATH = "/unpack";
    private static final String ALL_PATH = BASE_PATH + "/all";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(UnpackerResource.class);
        sf.setResourceProvider(UnpackerResource.class, new SingletonResourceProvider(new UnpackerResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TarWriter());
        providers.add(new ZipWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        return this
                .getClass()
                .getResourceAsStream("/configs/tika-config-unpacker.json");
    }

    //Test that the PDFParser's renderer can be configured at parse time
    //when specified in tika-config.json
    @Test
    public void testPDFPerPageRenderColor() throws Exception {
        //default is gray scale png; change to rgb and tiff
        String configJson = """
                {
                  "pdf-parser": {
                    "imageStrategy": "RENDER_PAGES_AT_PAGE_END",
                    "ocrImageType": "RGB",
                    "ocrImageFormat": "TIFF"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testColorRendering.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testColorRendering.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH + "/config")
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        Map<String, byte[]> results = readZipArchiveBytes((InputStream) response.getEntity());
        byte[] renderedImage = null;
        for (Map.Entry<String, byte[]> e : results.entrySet()) {
            if (e
                    .getKey()
                    .startsWith("tika-pdfbox-rendering")) {
                renderedImage = e.getValue();
                break;
            }
        }
        assertEquals("image/tiff", TikaLoader
                .loadDefault()
                .loadDetectors()
                .detect(new ByteArrayInputStream(renderedImage), new Metadata())
                .toString());

        try (InputStream is = new ByteArrayInputStream(renderedImage)) {
            BufferedImage image = ImageIO.read(is);
            //top left
            AverageColor averageColor = getAverageColor(image, 0, image.getWidth() / 5, 0, image.getHeight() / 10);
            assertTrue(averageColor.getRed() > 250);
            assertTrue(averageColor.getGreen() < 1);
            assertTrue(averageColor.getBlue() < 1);

            //bottom left = green
            averageColor = getAverageColor(image, 0, image.getWidth() / 5, image.getHeight() / 2 + image.getHeight() / 10, image.getHeight() / 2 + 2 * image.getHeight() / 10);

            assertTrue(averageColor.getRed() < 1);
            assertTrue(averageColor.getGreen() > 250);
            assertTrue(averageColor.getBlue() < 1);

            //bottom right = blue
            averageColor = getAverageColor(image, image.getWidth() / 2 + image.getWidth() / 10, image.getWidth() / 2 + 2 * image.getWidth() / 10,
                    image.getHeight() / 2 + image.getHeight() / 10, image.getHeight() / 2 + 2 * image.getHeight() / 10);

            assertTrue(averageColor.getRed() < 1);
            assertTrue(averageColor.getGreen() < 1);
            assertTrue(averageColor.getBlue() > 250);
        }
    }

    @Test
    public void testPDFPerPageRenderGray() throws Exception {
        String configJson = """
                {
                  "pdf-parser": {
                    "imageStrategy": "RENDER_PAGES_AT_PAGE_END",
                    "ocrImageType": "GRAY",
                    "ocrImageFormat": "JPEG"
                  }
                }
                """;
        ContentDisposition fileCd = new ContentDisposition("form-data; name=\"file\"; filename=\"testColorRendering.pdf\"");
        Attachment fileAtt = new Attachment("file",
                ClassLoader.getSystemResourceAsStream("test-documents/testColorRendering.pdf"), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(configJson.getBytes(StandardCharsets.UTF_8)));

        Response response = WebClient
                .create(CXFTestBase.endPoint + ALL_PATH + "/config")
                .type("multipart/form-data")
                .accept("application/zip")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));
        Map<String, byte[]> results = readZipArchiveBytes((InputStream) response.getEntity());
        byte[] renderedImage = null;
        for (Map.Entry<String, byte[]> e : results.entrySet()) {
            if (e
                    .getKey()
                    .startsWith("tika-pdfbox-rendering")) {
                renderedImage = e.getValue();
                break;
            }
        }
        assertEquals("image/jpeg", TikaLoader
                .loadDefault()
                .loadDetectors()
                .detect(new ByteArrayInputStream(renderedImage), new Metadata())
                .toString());

        try (InputStream is = new ByteArrayInputStream(renderedImage)) {
            BufferedImage image = ImageIO.read(is);
            //top left
            AverageColor averageColor = getAverageColor(image, 0, image.getWidth() / 5, 0, image.getHeight() / 10);

            assertTrue(averageColor.getRed() > 140 && averageColor.getRed() < 160);
            assertTrue(averageColor.getGreen() > 140 && averageColor.getGreen() < 160);
            assertTrue(averageColor.getBlue() > 140 && averageColor.getBlue() < 160);

            //bottom left = green
            averageColor = getAverageColor(image, 0, image.getWidth() / 5, image.getHeight() / 2 + image.getHeight() / 10, image.getHeight() / 2 + 2 * image.getHeight() / 10);

            assertTrue(averageColor.getRed() < 210 && averageColor.getRed() > 190);
            assertTrue(averageColor.getGreen() < 210 && averageColor.getGreen() > 190);
            assertTrue(averageColor.getBlue() < 210 && averageColor.getBlue() > 190);

            //bottom right = blue
            averageColor = getAverageColor(image, image.getWidth() / 2 + image.getWidth() / 10, image.getWidth() / 2 + 2 * image.getWidth() / 10,
                    image.getHeight() / 2 + image.getHeight() / 10, image.getHeight() / 2 + 2 * image.getHeight() / 10);
            assertTrue(averageColor.getRed() < 100 && averageColor.getRed() > 90);
            assertTrue(averageColor.getGreen() < 100 && averageColor.getGreen() > 90);
            assertTrue(averageColor.getBlue() < 100 && averageColor.getBlue() > 90);
        }
    }

}
