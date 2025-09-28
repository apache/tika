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
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.apache.tika.server.api.TikaResourceApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Main Tika REST API controller implementing core document processing endpoints.
 * 
 * This controller provides the primary Tika functionality including:
 * - Document text extraction
 * - Health/status checking  
 * - Basic document processing
 */
@RestController
@RequestMapping("/")
public class TikaController implements TikaResourceApi {

    private final AutoDetectParser parser = new AutoDetectParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET / - Root endpoint that returns a welcome message
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> welcome() {
        return ResponseEntity.ok(
            "This is Tika Server (" + getClass().getPackage().getImplementationVersion() + "). " +
            "Please PUT a document to extract text and metadata."
        );
    }

    /**
     * GET /tika - Returns greeting indicating server is up (API interface)
     */
    @Override
    @GetMapping(value = "/tika", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getTika() {
        return ResponseEntity.ok(
            "This is Tika Server. Please PUT a document to /tika to extract text."
        );
    }

    /**
     * PUT /tika - Extract text from uploaded document (API interface)
     */
    @Override
    public ResponseEntity<String> putTika() {
        return ResponseEntity.ok(
            "This is Tika Server. Please PUT a document to /tika to extract text."
        );
    }

    /**
     * GET /tika - Returns greeting indicating server is up
     */
    @GetMapping(value = "/tika", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getTikaAlternative() {
        return ResponseEntity.ok(
            "This is Tika Server. Please PUT a document to /tika to extract text."
        );
    }

    /**
     * PUT /tika - Extract text from uploaded document
     */
    @PutMapping(value = "/tika", produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> putTikaWithBody(@RequestBody byte[] document) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(document)) {
            BodyContentHandler handler = new BodyContentHandler(-1); // No limit on content
            ParseContext context = new ParseContext();
            
            parser.parse(tikaInputStream, handler, new org.apache.tika.metadata.Metadata(), context);
            
            return ResponseEntity.ok(handler.toString());
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing document: " + e.getMessage());
        }
    }

    /**
     * POST /tika - Extract text from multipart file upload
     */
    @PostMapping(value = "/tika", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> postTika(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded");
        }

        try (InputStream inputStream = file.getInputStream()) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            
            parser.parse(inputStream, handler, new org.apache.tika.metadata.Metadata(), context);
            
            return ResponseEntity.ok(handler.toString());
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing file: " + e.getMessage());
        }
    }

    /**
     * POST /tika/form - Extract text from multipart/form-data
     */
    @PostMapping(value = "/tika/form", 
                produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_HTML_VALUE, 
                           MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_JSON_VALUE},
                consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> postTikaForm(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded");
        }

        try (InputStream inputStream = file.getInputStream()) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            // Set filename if available
            if (file.getOriginalFilename() != null) {
                metadata.set(Metadata.RESOURCE_NAME_KEY, file.getOriginalFilename());
            }
            
            parser.parse(inputStream, handler, metadata, context);
            
            return ResponseEntity.ok(handler.toString());
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing file: " + e.getMessage());
        }
    }

    /**
     * PUT /tika/main - Extract main content using Boilerpipe
     */
    @PutMapping(value = "/tika/main", produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> putTikaMain(@RequestBody byte[] document) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(document)) {
            StringWriter writer = new StringWriter();
            ContentHandler handler = new BoilerpipeContentHandler(writer);
            ParseContext context = new ParseContext();
            
            parser.parse(tikaInputStream, handler, new Metadata(), context);
            
            return ResponseEntity.ok(writer.toString());
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing document with Boilerpipe: " + e.getMessage());
        }
    }

    /**
     * POST /tika/form/main - Extract main content from multipart/form-data using Boilerpipe
     */
    @PostMapping(value = "/tika/form/main", produces = MediaType.TEXT_PLAIN_VALUE, 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> postTikaFormMain(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded");
        }

        try (InputStream inputStream = file.getInputStream()) {
            StringWriter writer = new StringWriter();
            ContentHandler handler = new BoilerpipeContentHandler(writer);
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            // Set filename if available
            if (file.getOriginalFilename() != null) {
                metadata.set(Metadata.RESOURCE_NAME_KEY, file.getOriginalFilename());
            }
            
            parser.parse(inputStream, handler, metadata, context);
            
            return ResponseEntity.ok(writer.toString());
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing file with Boilerpipe: " + e.getMessage());
        }
    }

    /**
     * PUT /tika/{handler} - Extract content with specific handler type
     */
    @PutMapping(value = "/tika/{handler}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = "*/*")
    public ResponseEntity<String> putTikaWithHandler(@PathVariable String handler, @RequestBody byte[] document) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(document)) {
            BasicContentHandlerFactory.HANDLER_TYPE handlerType = parseHandlerType(handler);
            ContentHandler contentHandler = new BasicContentHandlerFactory(handlerType, -1).getNewContentHandler();
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            parser.parse(tikaInputStream, contentHandler, metadata, context);
            
            // Convert metadata to JSON with extracted content
            Map<String, Object> result = new HashMap<>();
            
            // Add extracted content
            result.put("X-TIKA:content", contentHandler.toString());
            
            // Add metadata
            for (String name : metadata.names()) {
                String[] values = metadata.getValues(name);
                if (values.length == 1) {
                    result.put(name, values[0]);
                } else {
                    result.put(name, values);
                }
            }
            
            return ResponseEntity.ok(objectMapper.writeValueAsString(result));
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("{\"error\": \"Error processing document: " + e.getMessage() + "\"}");
        }
    }

    private BasicContentHandlerFactory.HANDLER_TYPE parseHandlerType(String handlerTypeName) {
        if (handlerTypeName == null) {
            return BasicContentHandlerFactory.HANDLER_TYPE.XML;
        }
        
        switch (handlerTypeName.toLowerCase()) {
            case "text":
                return BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
            case "html":
                return BasicContentHandlerFactory.HANDLER_TYPE.HTML;
            case "ignore":
                return BasicContentHandlerFactory.HANDLER_TYPE.IGNORE;
            case "xml":
            default:
                return BasicContentHandlerFactory.HANDLER_TYPE.XML;
        }
    }
}
