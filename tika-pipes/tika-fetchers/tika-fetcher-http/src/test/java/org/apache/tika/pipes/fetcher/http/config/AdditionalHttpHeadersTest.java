package org.apache.tika.pipes.fetcher.http.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AdditionalHttpHeadersTest {

    ObjectMapper om = new ObjectMapper();

    @Test
    void testToAndFromJson() throws JsonProcessingException {
        AdditionalHttpHeaders additionalHttpHeaders = new AdditionalHttpHeaders();
        additionalHttpHeaders.getHeaders().put("nick1", "val1");
        additionalHttpHeaders.getHeaders().put("nick2", "val2");

        String json = om.writeValueAsString(additionalHttpHeaders);

        AdditionalHttpHeaders additionalHttpHeaders2 = om.readValue(json, AdditionalHttpHeaders.class);
        assertEquals(additionalHttpHeaders, additionalHttpHeaders2);
    }
}
