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
package org.apache.tika.serialization.serdes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Operator JSON (restricted=false) is trusted; per-request JSON (restricted=true) is not. */
public class ParseContextTrustTest {

    @Test
    public void testTrustFollowsRestriction() throws Exception {
        var node = new ObjectMapper().readTree("{\"tesseract-ocr-parser\": {\"skipOcr\": true}}");
        assertTrue(ParseContextDeserializer.readParseContext(node, false)
                .getJsonConfig("tesseract-ocr-parser").trusted());
        assertFalse(ParseContextDeserializer.readParseContext(node, true)
                .getJsonConfig("tesseract-ocr-parser").trusted());
    }
}
