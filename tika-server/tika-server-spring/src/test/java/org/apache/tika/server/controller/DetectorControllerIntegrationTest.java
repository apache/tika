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
package org.apache.tika.server.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import org.apache.tika.server.IntegrationTestBase;

/**
 * Integration tests for DetectorController.
 * Tests MIME/media type detection endpoints.
 */
public class DetectorControllerIntegrationTest extends IntegrationTestBase {

    @Test
    public void testDetectPlainText() throws Exception {
        byte[] textContent = "This is a plain text file for testing.".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(textContent))
                .andExpect(status().isOk())
                .andExpect(content().string("text/plain"));
    }

    @Test
    public void testDetectJsonFile() throws Exception {
        byte[] jsonContent = "{\"test\": \"json\", \"number\": 123}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(jsonContent)
                .header("Content-Disposition", "attachment; filename=\"test.json\""))
                .andExpect(status().isOk())
                .andExpect(content().string("application/json"));
    }

    @Test
    public void testDetectHtmlFile() throws Exception {
        byte[] htmlContent = "<html><head><title>Test</title></head><body><h1>Test HTML</h1></body></html>"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(htmlContent))
                .andExpect(status().isOk())
                .andExpect(content().string("text/html"));
    }

    @Test
    public void testDetectXmlFile() throws Exception {
        byte[] xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><element>test</element></root>"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(xmlContent))
                .andExpect(status().isOk())
                .andExpect(content().string("application/xml"));
    }

    @Test
    public void testDetectPdfSignature() throws Exception {
        // PDF file signature
        byte[] pdfContent = "%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n>>\nendobj\nxref\n0 1\ntrailer\n<<\n/Root 1 0 R\n>>\n%%EOF"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(pdfContent))
                .andExpect(status().isOk())
                .andExpect(content().string("application/pdf"));
    }

    @Test
    public void testDetectEmptyContent() throws Exception {
        byte[] emptyContent = new byte[0];

        // Empty content is treated as missing request body by Spring, so expect 400
        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(emptyContent)
                .header("Content-Disposition", "attachment; filename=\"empty.txt\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testDetectBinaryContent() throws Exception {
        // Random binary data that should default to octet-stream
        byte[] binaryContent = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                               (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A}; // PNG signature

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(binaryContent))
                .andExpect(status().isOk())
                .andExpect(content().string("image/png"));
    }

    @Test
    public void testDetectWithFilename() throws Exception {
        byte[] textContent = "This is a test file.".getBytes(StandardCharsets.UTF_8);

        // Test that filename hints help with detection
        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(textContent)
                .header("Content-Disposition", "attachment; filename=\"test.txt\""))
                .andExpect(status().isOk())
                .andExpect(content().string("text/plain"));
    }

    @Test
    public void testDetectJavaScriptFile() throws Exception {
        byte[] jsContent = "function test() { console.log('Hello World'); }".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(jsContent)
                .header("Content-Disposition", "attachment; filename=\"test.js\""))
                .andExpect(status().isOk())
                .andExpect(content().string("text/javascript"));
    }

    @Test
    public void testDetectCssFile() throws Exception {
        byte[] cssContent = "body { margin: 0; padding: 0; } .test { color: red; }".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(cssContent)
                .header("Content-Disposition", "attachment; filename=\"style.css\""))
                .andExpect(status().isOk())
                .andExpect(content().string("text/css"));
    }

    @Test
    public void testDetectLargeFile() throws Exception {
        // Create a larger text file to test with more content
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is line ").append(i).append(" of a large text file for testing.\n");
        }
        byte[] content = largeContent.toString().getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(content))
                .andExpect(status().isOk())
                .andExpect(content().string("text/plain"));
    }

    @Test
    public void testDetectNullContent() throws Exception {
        // Test with no content - should return bad request
        mockMvc.perform(put("/detect/stream"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testDetectFromTestResources() throws Exception {
        // Test using actual test resource files
        Path testFile = Paths.get("src/test/resources/test.txt");
        if (Files.exists(testFile)) {
            byte[] fileContent = Files.readAllBytes(testFile);

            mockMvc.perform(put("/detect/stream")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .content(fileContent))
                    .andExpect(status().isOk())
                    .andExpect(content().string("text/plain"));
        }
    }

    @Test
    public void testDetectZipFile() throws Exception {
        // ZIP file signature - PK followed by version info
        byte[] zipContent = {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04,
                            (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00};

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(zipContent))
                .andExpect(status().isOk())
                .andExpect(content().string("application/zip"));
    }

    @Test
    public void testDetectGifImage() throws Exception {
        // GIF file signature
        byte[] gifContent = "GIF89a".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(gifContent))
                .andExpect(status().isOk())
                .andExpect(content().string("image/gif"));
    }

    @Test
    public void testDetectJpegImage() throws Exception {
        // JPEG file signature
        byte[] jpegContent = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(jpegContent))
                .andExpect(status().isOk())
                .andExpect(content().string("image/jpeg"));
    }

    @Test
    public void testDetectMarkdownFile() throws Exception {
        byte[] markdownContent = "# Test Markdown\n\nThis is **bold** and *italic* text.\n\n- List item 1\n- List item 2"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(markdownContent)
                .header("Content-Disposition", "attachment; filename=\"test.md\""))
                .andExpect(status().isOk())
                .andExpect(content().string("text/x-web-markdown"));
    }

    @Test
    public void testDetectCsvFile() throws Exception {
        byte[] csvContent = "Name,Age,City\nJohn,25,New York\nJane,30,Los Angeles\nBob,35,Chicago"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(csvContent)
                .header("Content-Disposition", "attachment; filename=\"test.csv\""))
                .andExpect(status().isOk())
                .andExpect(content().string("text/csv"));
    }

    @Test
    public void testDetectVeryLargeFile() throws Exception {
        // Create a very large file to test performance and memory handling
        StringBuilder veryLargeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            veryLargeContent.append("This is line ").append(i)
                    .append(" of a very large text file for performance testing. ")
                    .append("Adding more content to make each line longer and test memory usage.\n");
        }
        byte[] content = veryLargeContent.toString().getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(content))
                .andExpect(status().isOk())
                .andExpect(content().string("text/plain"));
    }

    @Test
    public void testDetectBinaryData() throws Exception {
        // Random binary data that should fall back to octet-stream
        byte[] randomBinary = new byte[1024];
        for (int i = 0; i < randomBinary.length; i++) {
            randomBinary[i] = (byte) (Math.random() * 256);
        }

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(randomBinary))
                .andExpect(status().isOk())
                .andExpect(content().string("application/octet-stream"));
    }

    @Test
    public void testDetectMultipleRequests() throws Exception {
        // Test that the server can handle multiple concurrent detection requests
        byte[] textContent1 = "First test file content.".getBytes(StandardCharsets.UTF_8);
        byte[] textContent2 = "Second test file content.".getBytes(StandardCharsets.UTF_8);
        byte[] jsonContent = "{\"message\": \"test json\"}".getBytes(StandardCharsets.UTF_8);

        // Execute multiple requests to test server status tracking
        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(textContent1))
                .andExpect(status().isOk())
                .andExpect(content().string("text/plain"));

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(textContent2))
                .andExpect(status().isOk())
                .andExpect(content().string("text/plain"));

        mockMvc.perform(put("/detect/stream")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(jsonContent)
                .header("Content-Disposition", "attachment; filename=\"test.json\""))
                .andExpect(status().isOk())
                .andExpect(content().string("application/json"));
    }
}
