/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

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

import org.apache.tika.server.api.LanguageResourceApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Language detection controller.
 * 
 * Provides language identification capabilities for text content.
 * Note: This is a simplified implementation without actual language detection.
 */
@RestController
public class LanguageResourceController implements LanguageResourceApi {

    @Override
    public ResponseEntity<String> postLanguageStream() {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Please POST UTF-8 text content for language detection");
    }

    @Override
    public ResponseEntity<String> putLanguageStream() {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Please PUT UTF-8 text content for language detection");
    }

    @Override
    public ResponseEntity<String> postLanguageString() {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Please POST text string for language detection");
    }

    @Override
    public ResponseEntity<String> putLanguageString() {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Please PUT text string for language detection");
    }

    /**
     * POST /language/stream - Detect language from stream (with body)
     */
    @RequestMapping(value = "/language/stream", method = RequestMethod.POST, produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> postLanguageStreamWithBody(@RequestBody byte[] body) {
        return detectLanguageFromBytes(body);
    }

    /**
     * PUT /language/stream - Detect language from stream (with body)
     */
    @RequestMapping(value = "/language/stream", method = RequestMethod.PUT, produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> putLanguageStreamWithBody(@RequestBody byte[] body) {
        return detectLanguageFromBytes(body);
    }

    /**
     * POST /language/string - Detect language from string (with body)
     */
    @RequestMapping(value = "/language/string", method = RequestMethod.POST, produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> postLanguageStringWithBody(@RequestBody byte[] body) {
        return detectLanguageFromBytes(body);
    }

    /**
     * PUT /language/string - Detect language from string (with body)
     */
    @RequestMapping(value = "/language/string", method = RequestMethod.PUT, produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> putLanguageStringWithBody(@RequestBody byte[] body) {
        return detectLanguageFromBytes(body);
    }

    private ResponseEntity<String> detectLanguageFromBytes(byte[] body) {
        try {
            if (body == null || body.length == 0) {
                return ResponseEntity.badRequest().body("No content provided");
            }

            String text = new String(body, StandardCharsets.UTF_8);
            
            // Simple heuristic language detection (simplified implementation)
            // In a real implementation, you would use proper language detection libraries
            if (text.toLowerCase().contains("the") || text.toLowerCase().contains("and") || text.toLowerCase().contains("is")) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("en");
            } else if (text.toLowerCase().contains("la") || text.toLowerCase().contains("le") || text.toLowerCase().contains("de")) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("fr");
            } else if (text.toLowerCase().contains("der") || text.toLowerCase().contains("die") || text.toLowerCase().contains("das")) {
                return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("de");
            } else {
                return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("unknown");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Error detecting language: " + e.getMessage());
        }
    }
}
