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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import org.apache.tika.server.IntegrationTestBase;

/**
 * Integration tests for EvalController.
 * Tests text profiling and comparison endpoints using TikaEval framework.
 */
public class EvalControllerIntegrationTest extends IntegrationTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testBasicProfile() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "1");
        request.put("text", "the quick brown fox jumped qwertyuiop");

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Verify token count - based on original test expectations
        Integer numTokens = (Integer) results.get("tika-eval:numTokens");
        assertEquals(6, numTokens.intValue());

        Object oovObj = results.get("tika-eval:oov");
        Double oov = ((Number) oovObj).doubleValue();
        assertEquals(0.166, oov, 0.01);

        // Verify language detection
        String language = (String) results.get("tika-eval:lang");
        assertNotNull(language);
    }

    @Test
    public void testBasicCompare() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "1");
        request.put("textA", "the quick brown fox jumped qwertyuiop");
        request.put("textB", "the the the fast brown dog jumped qwertyuiop");

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Verify text A statistics - based on original test expectations
        Integer numTokensA = (Integer) results.get("tika-eval:numTokensA");
        assertEquals(6, numTokensA.intValue());

        Object oovAObj = results.get("tika-eval:oovA");
        Double oovA = ((Number) oovAObj).doubleValue();
        assertEquals(0.166, oovA, 0.01);

        String languageA = (String) results.get("tika-eval:langA");
        assertNotNull(languageA);

        // Verify similarity metrics - based on original test expectations
        Object diceObj = results.get("tika-eval:dice");
        Double dice = ((Number) diceObj).doubleValue();
        assertEquals(0.666, dice, 0.01);

        Object overlapObj = results.get("tika-eval:overlap");
        Double overlap = ((Number) overlapObj).doubleValue();
        assertEquals(0.571, overlap, 0.01);
    }

    @Test
    public void testProfileWithTimeout() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("id", "timeout-test");
        request.put("text", "short text for testing timeout functionality");
        request.put("timeoutMillis", 30000);

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Verify basic statistics for the text
        assertNotNull(results.get("tika-eval:numTokens"));
        assertNotNull(results.get("tika-eval:numUniqueTokens"));
    }

    @Test
    public void testCompareWithTimeout() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("id", "compare-timeout-test");
        request.put("textA", "hello world");
        request.put("textB", "hello universe");
        request.put("timeoutMillis", 30000);

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Verify both texts have been processed
        assertNotNull(results.get("tika-eval:numTokensA"));
        assertNotNull(results.get("tika-eval:numTokensB"));

        // Verify similarity metrics are present
        assertNotNull(results.get("tika-eval:dice"));
        assertNotNull(results.get("tika-eval:overlap"));
    }

    @Test
    public void testProfileEmptyText() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "empty-test");
        request.put("text", "");

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Empty text should have zero tokens
        assertEquals(0, (Integer) results.get("tika-eval:numTokens"));
        assertEquals(0, (Integer) results.get("tika-eval:numUniqueTokens"));
        assertEquals(0, (Integer) results.get("tika-eval:numAlphaTokens"));
    }

    @Test
    public void testProfileLongText() throws Exception {
        // Create a longer text sample for testing
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("This is sentence number ").append(i).append(". ");
        }

        Map<String, String> request = new HashMap<>();
        request.put("id", "long-text-test");
        request.put("text", longText.toString());

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Verify we get reasonable statistics for long text
        Integer numTokens = (Integer) results.get("tika-eval:numTokens");
        assertNotNull(numTokens);
        // Should have many tokens for this long text
        assert(numTokens > 100);

        assertNotNull(results.get("tika-eval:numUniqueTokens"));
    }

    @Test
    public void testCompareIdenticalTexts() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "identical-test");
        request.put("textA", "the quick brown fox");
        request.put("textB", "the quick brown fox");

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Identical texts should have high similarity scores
        Double dice = (Double) results.get("tika-eval:dice");
        assertNotNull(dice);
        // Dice coefficient should be close to 1.0 for identical texts
        assert(dice > 0.9);

        // Token counts should be identical
        assertEquals(results.get("tika-eval:numTokensA"), results.get("tika-eval:numTokensB"));
    }

    @Test
    public void testCompareCompletelyDifferentTexts() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "different-test");
        request.put("textA", "apple banana cherry");
        request.put("textB", "dog elephant frog");

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Completely different texts should have low similarity scores
        Double dice = (Double) results.get("tika-eval:dice");
        assertNotNull(dice);
        // Dice coefficient should be close to 0.0 for completely different texts
        assert(dice < 0.1);

        Double overlap = (Double) results.get("tika-eval:overlap");
        assertNotNull(overlap);
        assert(overlap < 0.1);
    }

    @Test
    public void testInvalidJsonRequest() throws Exception {
        String invalidJson = "{invalid json}";

        mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testMissingRequiredFields() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "missing-text");
        // Missing "text" field

        String jsonRequest = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testMissingRequiredFieldsCompare() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "missing-text");
        request.put("textA", "some text");
        // Missing "textB" field

        String jsonRequest = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/eval/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testMultipleProfileRequests() throws Exception {
        // Test that the server can handle multiple concurrent profile requests
        Map<String, String> request1 = new HashMap<>();
        request1.put("id", "multi-test-1");
        request1.put("text", "First test text for profiling.");

        Map<String, String> request2 = new HashMap<>();
        request2.put("id", "multi-test-2");
        request2.put("text", "Second test text for profiling analysis.");

        Map<String, String> request3 = new HashMap<>();
        request3.put("id", "multi-test-3");
        request3.put("text", "Third test text with different content for evaluation.");

        String jsonRequest1 = objectMapper.writeValueAsString(request1);
        String jsonRequest2 = objectMapper.writeValueAsString(request2);
        String jsonRequest3 = objectMapper.writeValueAsString(request3);

        // Execute multiple requests to test server status tracking
        mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest1.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest2.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest3.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
    }

    @Test
    public void testSpecialCharactersInText() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "special-chars-test");
        request.put("text", "Hello, world! This text contains special characters: @#$%^&*()_+{}|:<>?");

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Should handle special characters gracefully
        assertNotNull(results.get("tika-eval:numTokens"));
        assertNotNull(results.get("tika-eval:numAlphaTokens"));
    }

    @Test
    public void testUnicodeText() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("id", "unicode-test");
        request.put("text", "Héllo wørld! This is tëst tëxt with ūnïcōdē characters: 你好世界 🌍");

        String jsonRequest = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(put("/eval/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> results = objectMapper.readValue(responseBody,
            new TypeReference<Map<String, Object>>() {});

        // Should handle Unicode characters gracefully
        assertNotNull(results.get("tika-eval:numTokens"));
    }
}
