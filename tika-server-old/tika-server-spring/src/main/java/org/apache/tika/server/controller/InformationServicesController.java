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
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.Parser;
import org.apache.tika.server.api.InformationServicesApi;
import org.apache.tika.server.model.DefaultDetectorChildrenInner;
import org.apache.tika.server.model.DetailedParser;
import org.apache.tika.server.model.DetailedParsers;
import org.apache.tika.server.model.Parsers;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Information Services controller providing system information endpoints.
 */
@RestController
public class InformationServicesController implements InformationServicesApi {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TikaConfig tikaConfig = TikaConfig.getDefaultConfig();

    @Override
    public ResponseEntity<String> getEndpoints() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Tika Server Endpoints</title></head><body>")
            .append("<h1>Apache Tika Server Endpoints</h1>")
            .append("<ul>")
            .append("<li><strong>GET /</strong> - This endpoint information</li>")
            .append("<li><strong>GET /detectors</strong> - Get detector information</li>")
            .append("<li><strong>PUT /detect/stream</strong> - Detect MIME type of uploaded document</li>")
            .append("<li><strong>GET /mime-types</strong> - Get all MIME types</li>")
            .append("<li><strong>GET /parsers</strong> - Get all parsers</li>")
            .append("<li><strong>GET /parsers/details</strong> - Get detailed parser information</li>")
            .append("<li><strong>GET /tika</strong> - Tika greeting</li>")
            .append("<li><strong>PUT /tika</strong> - Extract text from document</li>")
            .append("<li><strong>PUT /meta</strong> - Extract metadata from document</li>")
            .append("<li><strong>PUT /meta/{key}</strong> - Extract specific metadata value</li>")
            .append("<li><strong>PUT /rmeta</strong> - Extract recursive metadata</li>")
            .append("<li><strong>POST|PUT /language/stream</strong> - Detect language from stream</li>")
            .append("<li><strong>POST|PUT /language/string</strong> - Detect language from string</li>")
            .append("<li><strong>PUT /unpack</strong> - Unpack embedded files</li>")
            .append("</ul></body></html>");
        
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html.toString());
    }

    @Override
    public ResponseEntity<org.apache.tika.server.model.DefaultDetector> getDetectors() {
        try {
            org.apache.tika.detect.DefaultDetector detector = (org.apache.tika.detect.DefaultDetector) tikaConfig.getDetector();
            org.apache.tika.server.model.DefaultDetector apiDetector = new org.apache.tika.server.model.DefaultDetector();
            
            apiDetector.setName(detector.getClass().getName());
            apiDetector.setComposite(true);
            
            List<DefaultDetectorChildrenInner> children = new ArrayList<>();
            // Note: Getting children from DefaultDetector requires reflection or other approaches
            // For now, we'll provide the main detector info
            DefaultDetectorChildrenInner child = new DefaultDetectorChildrenInner();
            child.setName(detector.getClass().getName());
            child.setComposite(false);
            children.add(child);
            
            apiDetector.setChildren(children);
            
            return ResponseEntity.ok(apiDetector);
        } catch (Exception e) {
            // Return error in a structured format
            org.apache.tika.server.model.DefaultDetector errorDetector = new org.apache.tika.server.model.DefaultDetector();
            errorDetector.setName("Error: " + e.getMessage());
            errorDetector.setComposite(false);
            return ResponseEntity.internalServerError().body(errorDetector);
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> getMimetypes() {
        try {
            MimeTypes mimeTypes = tikaConfig.getMimeRepository();
            Map<String, Object> mimeTypesMap = new HashMap<>();
            
            // Get all registered MIME types
            mimeTypes.getMediaTypeRegistry().getTypes().forEach(mediaType -> {
                Map<String, Object> typeInfo = new HashMap<>();
                typeInfo.put("name", mediaType.toString());
                typeInfo.put("type", mediaType.getType());
                typeInfo.put("subtype", mediaType.getSubtype());
                
                try {
                    org.apache.tika.mime.MimeType mimeType = mimeTypes.forName(mediaType.toString());
                    typeInfo.put("description", mimeType.getDescription());
                    typeInfo.put("extensions", mimeType.getExtensions());
                } catch (MimeTypeException e) {
                    // Ignore individual MIME type errors
                }
                
                mimeTypesMap.put(mediaType.toString(), typeInfo);
            });
            
            return ResponseEntity.ok(mimeTypesMap);
        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "Error retrieving MIME types: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorMap);
        }
    }

    @Override
    public ResponseEntity<Parsers> getParsers() {
        try {
            org.apache.tika.parser.DefaultParser defaultParser = (org.apache.tika.parser.DefaultParser) tikaConfig.getParser();
            Parsers parsers = new Parsers();
            
            parsers.setName(defaultParser.getClass().getName());
            parsers.setComposite(true);
            parsers.setDecorated(false);
            
            // Get child parsers
            List<org.apache.tika.server.model.Parser> children = defaultParser.getParsers().keySet().stream()
                                                                              .map(mediaType -> {
                    Parser parser = defaultParser.getParsers().get(mediaType);
                    org.apache.tika.server.model.Parser apiParser = new org.apache.tika.server.model.Parser();
                    apiParser.setName(parser.getClass().getName());
                    apiParser.setComposite(false);
                    apiParser.setDecorated(false);
                    return apiParser;
                })
                                                                              .distinct()
                                                                              .collect(Collectors.toList());
            
            parsers.setChildren(children);
            
            return ResponseEntity.ok(parsers);
        } catch (Exception e) {
            Parsers errorParsers = new Parsers();
            errorParsers.setName("Error: " + e.getMessage());
            errorParsers.setComposite(false);
            errorParsers.setDecorated(false);
            return ResponseEntity.internalServerError().body(errorParsers);
        }
    }

    @Override
    public ResponseEntity<DetailedParsers> getParsersDetails() {
        try {
            org.apache.tika.parser.DefaultParser defaultParser = (org.apache.tika.parser.DefaultParser) tikaConfig.getParser();
            DetailedParsers detailedParsers = new DetailedParsers();
            
            detailedParsers.setName(defaultParser.getClass().getName());
            detailedParsers.setComposite(true);
            detailedParsers.setDecorated(false);
            
            // Get detailed parser information with supported types
            List<DetailedParser> children = new ArrayList<>();
            Map<String, Set<String>> parserToTypes = new HashMap<>();
            
            // Group media types by parser
            defaultParser.getParsers().forEach((mediaType, parser) -> {
                String parserName = parser.getClass().getName();
                parserToTypes.computeIfAbsent(parserName, k -> new HashSet<>()).add(mediaType.toString());
            });
            
            // Create detailed parser objects
            parserToTypes.forEach((parserName, supportedTypes) -> {
                DetailedParser detailedParser = new DetailedParser();
                detailedParser.setName(parserName);
                detailedParser.setComposite(false);
                detailedParser.setDecorated(false);
                detailedParser.setSupportedTypes(new ArrayList<>(supportedTypes));
                children.add(detailedParser);
            });
            
            detailedParsers.setChildren(children);
            
            return ResponseEntity.ok(detailedParsers);
        } catch (Exception e) {
            DetailedParsers errorParsers = new DetailedParsers();
            errorParsers.setName("Error: " + e.getMessage());
            errorParsers.setComposite(false);
            errorParsers.setDecorated(false);
            return ResponseEntity.internalServerError().body(errorParsers);
        }
    }

    @GetMapping("/mime-types/{type}/{subtype}")
    public ResponseEntity<Map<String, Object>> getMimeTypeDetails(@PathVariable String type, @PathVariable String subtype) {
        try {
            String mimeTypeString = type + "/" + subtype;
            MimeTypes mimeTypes = tikaConfig.getMimeRepository();
            
            Map<String, Object> typeInfo = new HashMap<>();
            
            try {
                org.apache.tika.mime.MimeType mimeType = mimeTypes.forName(mimeTypeString);
                typeInfo.put("type", mimeTypeString);
                typeInfo.put("description", mimeType.getDescription());
                typeInfo.put("extensions", mimeType.getExtensions());
                typeInfo.put("defaultExtension", mimeType.getExtension());
                typeInfo.put("uniformTypeIdentifier", mimeType.getUniformTypeIdentifier());
                typeInfo.put("links", mimeType.getLinks());
                
                // Get aliases
                org.apache.tika.mime.MediaType mediaType = org.apache.tika.mime.MediaType.parse(mimeTypeString);
                Set<org.apache.tika.mime.MediaType> aliases = mimeTypes.getMediaTypeRegistry().getAliases(mediaType);
                typeInfo.put("alias", aliases.stream().map(Object::toString).collect(java.util.stream.Collectors.toList()));
                
                // Get supertype
                org.apache.tika.mime.MediaType supertype = mimeTypes.getMediaTypeRegistry().getSupertype(mediaType);
                if (supertype != null && !org.apache.tika.mime.MediaType.OCTET_STREAM.equals(supertype)) {
                    typeInfo.put("supertype", supertype.toString());
                }
                
                // Get parser
                org.apache.tika.parser.DefaultParser defaultParser = (org.apache.tika.parser.DefaultParser) tikaConfig.getParser();
                Parser parser = defaultParser.getParsers().get(mediaType);
                if (parser != null) {
                    typeInfo.put("parser", parser.getClass().getName());
                }
                
                return ResponseEntity.ok(typeInfo);
                
            } catch (MimeTypeException e) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("error", "MIME type not found: " + mimeTypeString);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("error", "Error retrieving MIME type details: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorMap);
        }
    }
    }
}
