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

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.server.api.DetectorResourceApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Document type detection controller.
 * 
 * Provides MIME type detection capabilities for uploaded documents.
 */
@RestController
@RequestMapping("/detect")
public class DetectorController implements DetectorResourceApi {

    private final DefaultDetector detector = new DefaultDetector();

    /**
     * PUT /detect/stream - Detect MIME type of uploaded document
     */
    @Override
    @PutMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> putStream() {
        return ResponseEntity.ok("Please PUT a document to this endpoint for MIME type detection");
    }

    /**
     * PUT /detect/stream - Detect MIME type of uploaded document (with body)
     */
    @PutMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> detectStream(@RequestBody byte[] document) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(document)) {
            Metadata metadata = new Metadata();
            MediaType mediaType = detector.detect(tikaInputStream, metadata);
            
            return ResponseEntity.ok(mediaType.toString());
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body("Error detecting document type: " + e.getMessage());
        }
    }
}
