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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.mock.MockTask;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.resource.RecursiveMetadataResource;
import org.apache.tika.server.core.writer.MetadataListMessageBodyWriter;

/** A config-named enricher reaches the forked worker and its output comes back (TIKA-4872). */
public class InferenceBindingsWireTest extends CXFTestBase {

    private static final String META_PATH = "/rmeta";

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
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return getClass().getResourceAsStream("/configs/tika-config-inference-bindings.json");
    }

    @Override
    protected boolean isAllowPerRequestConfig() {
        return true;
    }

    @Test
    public void testBindingOutputSurvivesFork() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", png);

        Response response = WebClient
                .create(endPoint + META_PATH)
                .accept("application/json")
                .put(png.toByteArray());

        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("mock-images", metadata.get(MockTask.MARKER_KEY));
        assertEquals("1", metadata.get(MockTask.UNITS_KEY));
    }

    /** A request asks for a PDF's pages; the PAGES binding's output lands on the PDF. */
    @Test
    public void testPagesPerRequest() throws Exception {
        String config = """
                { "parse-context": {
                    "pdf-parser": { "ocr": { "strategy": "NO_OCR", "dpi": 20 },
                                    "inference": { "input": ["PAGES"] } },
                    "inference": { "bindings": ["mock-pages"] } } }
                """;
        ContentDisposition fileCd = new ContentDisposition(
                "form-data; name=\"file\"; filename=\"two-pages.pdf\"");
        Attachment fileAtt = new Attachment("file", new ByteArrayInputStream(twoPagePdf()), fileCd);
        Attachment configAtt = new Attachment("config", "application/json",
                new ByteArrayInputStream(config.getBytes(UTF_8)));

        Response response = WebClient
                .create(endPoint + META_PATH + "/config")
                .type("multipart/form-data")
                .accept("application/json")
                .post(new MultipartBody(Arrays.asList(fileAtt, configAtt)));

        assertEquals(200, response.getStatus());
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("mock-pages", metadata.get(MockTask.MARKER_KEY));
        assertEquals("2", metadata.get(MockTask.UNITS_KEY));
    }

    private static byte[] twoPagePdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 1; i <= 2; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText("This is page " + i + " of the test document.");
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
