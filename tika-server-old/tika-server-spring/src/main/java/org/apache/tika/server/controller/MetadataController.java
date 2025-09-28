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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.server.api.MetadataResourceApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata extraction controller.
 * 
 * Provides metadata extraction capabilities for uploaded documents.
 */
@RestController
@RequestMapping("/meta")
public class MetadataController implements MetadataResourceApi {

    private final AutoDetectParser parser = new AutoDetectParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * PUT /meta - Extract metadata from uploaded document (API interface)
     */
    @Override
    public ResponseEntity<Map<String, String>> putDocumentMeta() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Please PUT a document to this endpoint for metadata extraction");
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /meta/{key} - Extract specific metadata value (API interface)  
     */
    @Override
    public ResponseEntity<Map<String, String>> putDocumentGetMetaValue(String metadataKey) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Please PUT a document to this endpoint for metadata extraction of key: " + metadataKey);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /meta - Extract metadata from uploaded document
     */
    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = "*/*")
    public ResponseEntity<String> extractMetadata(@RequestBody byte[] document) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(document)) {
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            
            parser.parse(tikaInputStream, handler, metadata, context);
            
            // Convert metadata to JSON
            Map<String, Object> metadataMap = new HashMap<>();
            for (String name : metadata.names()) {
                String[] values = metadata.getValues(name);
                if (values.length == 1) {
                    metadataMap.put(name, values[0]);
                } else {
                    metadataMap.put(name, values);
                }
            }
            
            return ResponseEntity.ok(objectMapper.writeValueAsString(metadataMap));
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("{\"error\": \"Error extracting metadata: " + e.getMessage() + "\"}");
        }
    }

    /**
     * PUT /meta/{key} - Extract specific metadata value from uploaded document
     */
    @PutMapping(value = "/{metadataKey}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = "*/*")
    public ResponseEntity<String> extractSpecificMetadata(@PathVariable String metadataKey, @RequestBody byte[] document) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(document)) {
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            
            parser.parse(tikaInputStream, handler, metadata, context);
            
            String value = metadata.get(metadataKey);
            Map<String, Object> result = new HashMap<>();
            result.put(metadataKey, value != null ? value : "");
            
            return ResponseEntity.ok(objectMapper.writeValueAsString(result));
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("{\"error\": \"Error extracting metadata: " + e.getMessage() + "\"}");
        }
    }

    /**
     * POST /meta/form - Extract metadata from multipart/form-data uploaded document
     */
    @PostMapping(value = "/form", produces = {MediaType.APPLICATION_JSON_VALUE, "text/csv"}, 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> extractMetadataFromForm(@RequestParam("file") MultipartFile file) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(file.getInputStream())) {
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            
            // Set filename if available
            if (file.getOriginalFilename() != null) {
                metadata.set(Metadata.RESOURCE_NAME_KEY, file.getOriginalFilename());
            }
            
            parser.parse(tikaInputStream, handler, metadata, context);
            
            // Convert metadata to JSON
            Map<String, Object> metadataMap = new HashMap<>();
            for (String name : metadata.names()) {
                String[] values = metadata.getValues(name);
                if (values.length == 1) {
                    metadataMap.put(name, values[0]);
                } else {
                    metadataMap.put(name, values);
                }
            }
            
            return ResponseEntity.ok(objectMapper.writeValueAsString(metadataMap));
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("{\"error\": \"Error extracting metadata: " + e.getMessage() + "\"}");
        }
    }
}
