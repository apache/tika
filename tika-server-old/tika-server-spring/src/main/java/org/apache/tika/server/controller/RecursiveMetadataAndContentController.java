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
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.server.api.RecursiveMetadataAndContentApi;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

/**
 * Recursive metadata and content extraction controller.
 * 
 * Provides recursive parsing capabilities that extract metadata and content 
 * from container documents and all embedded documents.
 */
@RestController
public class RecursiveMetadataAndContentController implements RecursiveMetadataAndContentApi {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ResponseEntity<String> putRmeta() {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Please PUT a document to this endpoint for recursive metadata extraction");
    }

    /**
     * PUT /rmeta - Extract recursive metadata from uploaded document (with body)
     */
    @RequestMapping(value = "/rmeta", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE, consumes = "*/*")
    public ResponseEntity<String> putRmetaWithBody(@RequestBody byte[] body) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(body)) {
            AutoDetectParser parser = new AutoDetectParser();
            RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
            
            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1)
            );
            
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            wrapper.parse(tikaInputStream, handler, metadata, context);
            
            List<Metadata> metadataList = handler.getMetadataList();
            
            // Convert to JSON
            String jsonResult = objectMapper.writeValueAsString(metadataList);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResult);
                
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing document recursively: " + e.getMessage());
        }
    }

    /**
     * PUT /rmeta/{handler} - Extract recursive metadata with specific handler type
     */
    @PutMapping(value = "/rmeta/{handler}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = "*/*")
    public ResponseEntity<String> putRmetaWithHandler(@PathVariable String handler, @RequestBody byte[] body) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(body)) {
            AutoDetectParser parser = new AutoDetectParser();
            RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
            
            BasicContentHandlerFactory.HANDLER_TYPE handlerType = parseHandlerType(handler);
            RecursiveParserWrapperHandler rhandler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(handlerType, -1)
            );
            
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            wrapper.parse(tikaInputStream, rhandler, metadata, context);
            
            List<Metadata> metadataList = rhandler.getMetadataList();
            
            // Convert to JSON
            String jsonResult = objectMapper.writeValueAsString(metadataList);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResult);
                
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing document recursively: " + e.getMessage());
        }
    }

    /**
     * POST /rmeta/form - Extract recursive metadata from multipart/form-data
     */
    @PostMapping(value = "/rmeta/form", produces = MediaType.APPLICATION_JSON_VALUE, 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> postRmetaForm(@RequestParam("file") MultipartFile file) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(file.getInputStream())) {
            AutoDetectParser parser = new AutoDetectParser();
            RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
            
            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1)
            );
            
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            // Set filename if available
            if (file.getOriginalFilename() != null) {
                metadata.set(Metadata.RESOURCE_NAME_KEY, file.getOriginalFilename());
            }
            
            wrapper.parse(tikaInputStream, handler, metadata, context);
            
            List<Metadata> metadataList = handler.getMetadataList();
            
            // Convert to JSON
            String jsonResult = objectMapper.writeValueAsString(metadataList);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResult);
                
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing document recursively: " + e.getMessage());
        }
    }

    /**
     * POST /rmeta/form/{handler} - Extract recursive metadata from form with specific handler
     */
    @PostMapping(value = "/rmeta/form/{handler}", produces = MediaType.APPLICATION_JSON_VALUE, 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> postRmetaFormWithHandler(@PathVariable String handler, 
                                                          @RequestParam("file") MultipartFile file) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(file.getInputStream())) {
            AutoDetectParser parser = new AutoDetectParser();
            RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
            
            BasicContentHandlerFactory.HANDLER_TYPE handlerType = parseHandlerType(handler);
            RecursiveParserWrapperHandler rhandler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(handlerType, -1)
            );
            
            ParseContext context = new ParseContext();
            Metadata metadata = new Metadata();
            
            // Set filename if available
            if (file.getOriginalFilename() != null) {
                metadata.set(Metadata.RESOURCE_NAME_KEY, file.getOriginalFilename());
            }
            
            wrapper.parse(tikaInputStream, rhandler, metadata, context);
            
            List<Metadata> metadataList = rhandler.getMetadataList();
            
            // Convert to JSON
            String jsonResult = objectMapper.writeValueAsString(metadataList);
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonResult);
                
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error processing document recursively: " + e.getMessage());
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
