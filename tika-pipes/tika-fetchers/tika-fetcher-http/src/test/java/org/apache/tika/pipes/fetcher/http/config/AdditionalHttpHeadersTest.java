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
