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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import org.apache.tika.server.IntegrationTestBase;

/**
 * Integration tests for LanguageResourceController.
 * Tests language identification service endpoints.
 * Ported from the legacy JAX-RS implementation.
 */
public class LanguageResourceControllerIntegrationTest extends IntegrationTestBase {

    private static final String LANG_PATH = "/language";
    private static final String LANG_STREAM_PATH = LANG_PATH + "/stream";
    private static final String LANG_STRING_PATH = LANG_PATH + "/string";
    private static final String ENGLISH_STRING = "This is English!";
    private static final String FRENCH_STRING = "c'est comme ci comme ça";

    @Test
    public void testDetectEnglishString() throws Exception {
        mockMvc.perform(put(LANG_STRING_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(ENGLISH_STRING))
                .andExpect(status().isOk())
                .andExpect(content().string("en"));
    }

    @Test
    public void testDetectFrenchString() throws Exception {
        mockMvc.perform(put(LANG_STRING_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(FRENCH_STRING))
                .andExpect(status().isOk())
                .andExpect(content().string("fr"));
    }

    @Test
    public void testDetectEnglishFile() throws Exception {
        InputStream englishStream = getClass().getResourceAsStream("/test-documents/english.txt");

        mockMvc.perform(put(LANG_STREAM_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(englishStream.readAllBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string("en"));
    }

    @Test
    public void testDetectFrenchFile() throws Exception {
        InputStream frenchStream = getClass().getResourceAsStream("/test-documents/french.txt");

        mockMvc.perform(put(LANG_STREAM_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(frenchStream.readAllBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string("fr"));
    }

    @Test
    public void testDetectEnglishStringPost() throws Exception {
        mockMvc.perform(post(LANG_STRING_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(ENGLISH_STRING))
                .andExpect(status().isOk())
                .andExpect(content().string("en"));
    }

    @Test
    public void testDetectFrenchStringPost() throws Exception {
        mockMvc.perform(post(LANG_STRING_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(FRENCH_STRING))
                .andExpect(status().isOk())
                .andExpect(content().string("fr"));
    }

    @Test
    public void testDetectEnglishFilePost() throws Exception {
        InputStream englishStream = getClass().getResourceAsStream("/test-documents/english.txt");

        mockMvc.perform(post(LANG_STREAM_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(englishStream.readAllBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string("en"));
    }

    @Test
    public void testDetectFrenchFilePost() throws Exception {
        InputStream frenchStream = getClass().getResourceAsStream("/test-documents/french.txt");

        mockMvc.perform(post(LANG_STREAM_PATH)
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.TEXT_PLAIN)
                .content(frenchStream.readAllBytes()))
                .andExpect(status().isOk())
                .andExpect(content().string("fr"));
    }
}
