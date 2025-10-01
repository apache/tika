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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.server.IntegrationTestBase;

/**
 * Integration tests for MetadataController.
 * Tests metadata extraction service endpoints.
 * Ported from the legacy JAX-RS implementation and adapted for Spring Boot supported content types.
 */
public class MetadataControllerIntegrationTest extends IntegrationTestBase {

    private static final String META_PATH = "/meta";
    private static final String TEST_DOC = "/test-documents/test.doc";
    private static final String TEST_PASSWORD_PROTECTED = "/test-documents/password-protected.doc";

    @Test
    public void testSimpleWordJSON() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);

        MvcResult result = mockMvc.perform(put(META_PATH)
                .contentType("application/msword")
                .accept("application/json")
                .content(docStream.readAllBytes()))
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        Reader reader = new InputStreamReader(new ByteArrayInputStream(jsonContent.getBytes(UTF_8)), UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);

        // Basic validation - check that we have some metadata
        assertNotNull(metadata);
        assertTrue(metadata.names().length > 0);

        // Check for common metadata fields that should be present
        String contentType = metadata.get("Content-Type");
        assertNotNull(contentType, "Content-Type should be present");
        assertTrue(contentType.contains("application") || contentType.contains("msword"),
                   "Content type should indicate Word document");
    }

    @Test
    public void testSimpleWordText() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);

        MvcResult result = mockMvc.perform(put(META_PATH)
                .contentType("application/msword")
                .accept("text/plain")
                .content(docStream.readAllBytes()))
                .andExpect(status().isOk())
                .andReturn();

        String textContent = result.getResponse().getContentAsString();
        assertNotNull(textContent);
        assertTrue(textContent.length() > 0, "Should return some text content");
    }

    @Test
    public void testPasswordProtected() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_PASSWORD_PROTECTED);

        // Won't work, no password given
        mockMvc.perform(put(META_PATH)
                .contentType("application/msword")
                .accept("application/json")
                .content(docStream.readAllBytes()))
                .andExpect(status().is5xxServerError());

        // Try again, this time with the wrong password
        docStream = getClass().getResourceAsStream(TEST_PASSWORD_PROTECTED);
        mockMvc.perform(put(META_PATH)
                .contentType("application/msword")
                .accept("application/json")
                .header("Password", "wrong password")
                .content(docStream.readAllBytes()))
                .andExpect(status().is5xxServerError());

        // Try again, this time with the correct password
        docStream = getClass().getResourceAsStream(TEST_PASSWORD_PROTECTED);
        MvcResult result = mockMvc.perform(put(META_PATH)
                .contentType("application/msword")
                .accept("application/json")
                .header("Password", "tika")
                .content(docStream.readAllBytes()))
                .andExpect(status().isOk())
                .andReturn();

        // Check results
        String jsonContent = result.getResponse().getContentAsString();
        Reader reader = new InputStreamReader(new ByteArrayInputStream(jsonContent.getBytes(UTF_8)), UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);
        assertNotNull(metadata);
        assertTrue(metadata.names().length > 0, "Should have extracted metadata with correct password");
    }

    @Test
    public void testJSON() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);

        MvcResult result = mockMvc.perform(put(META_PATH)
                .contentType("application/msword")
                .accept("application/json")
                .content(docStream.readAllBytes()))
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        Reader reader = new InputStreamReader(new ByteArrayInputStream(jsonContent.getBytes(UTF_8)), UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);
        assertNotNull(metadata);
        assertTrue(metadata.names().length > 0, "Should have extracted metadata");
    }

    @Test
    public void testGetField_XXX_NotFound() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);

        mockMvc.perform(put(META_PATH + "/xxx")
                .contentType("application/msword")
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .content(docStream.readAllBytes()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetField_ContentType_JSON_Found() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);

        MvcResult result = mockMvc.perform(put(META_PATH + "/Content-Type")
                .contentType("application/msword")
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .content(docStream.readAllBytes()))
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        Reader reader = new InputStreamReader(new ByteArrayInputStream(jsonContent.getBytes(UTF_8)), UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);
        assertNotNull(metadata.get("Content-Type"));
        assertEquals(1, metadata.names().length, "Should only return the requested field");
    }

    @Test
    public void testGetField_Author_TEXT_Partial_BAD_REQUEST() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);
        byte[] partialContent = copyBytes(docStream, 8000);

        mockMvc.perform(put(META_PATH + "/Author")
                .contentType("application/msword")
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .content(partialContent))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetField_ContentType_TEXT_Partial_Found() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);
        byte[] partialContent = copyBytes(docStream, 12000);

        MvcResult result = mockMvc.perform(put(META_PATH + "/Content-Type")
                .contentType("application/msword")
                .accept(MediaType.TEXT_PLAIN_VALUE)
                .content(partialContent))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertNotNull(content);
        assertTrue(content.length() > 0, "Should return content type value");
    }

    @Test
    public void testGetField_ContentType_JSON_Partial_Found() throws Exception {
        InputStream docStream = getClass().getResourceAsStream(TEST_DOC);
        byte[] partialContent = copyBytes(docStream, 12000);

        MvcResult result = mockMvc.perform(put(META_PATH + "/Content-Type")
                .contentType("application/msword")
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .content(partialContent))
                .andExpect(status().isOk())
                .andReturn();

        String jsonContent = result.getResponse().getContentAsString();
        Reader reader = new InputStreamReader(new ByteArrayInputStream(jsonContent.getBytes(UTF_8)), UTF_8);

        Metadata metadata = JsonMetadata.fromJson(reader);
        assertNotNull(metadata.get("Content-Type"));
        assertEquals(1, metadata.names().length, "Should only return the requested field");
    }

    /**
     * Helper method to copy a specified number of bytes from an InputStream.
     * This simulates partial document uploads for testing partial parsing scenarios.
     */
    private byte[] copyBytes(InputStream stream, int maxBytes) throws Exception {
        byte[] buffer = new byte[maxBytes];
        int bytesRead = stream.read(buffer);
        if (bytesRead < maxBytes) {
            byte[] result = new byte[bytesRead];
            System.arraycopy(buffer, 0, result, 0, bytesRead);
            return result;
        }
        return buffer;
    }
}
