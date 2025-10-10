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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.server.api.MetadataResourceApi;
import org.apache.tika.server.service.TikaLoggingService;
import org.apache.tika.server.service.TikaMetadataFillerService;
import org.apache.tika.server.service.TikaParseContextService;
import org.apache.tika.server.service.TikaParsingService;

/**
 * Controller for metadata extraction services.
 * Handles the /meta and /xmpmeta endpoints for Metadata Resource and XMP Metadata Resource tags.
 * Ported from the legacy JAX-RS MetadataResource implementation.
 */
@RestController
public class MetadataController implements MetadataResourceApi {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataController.class);

    @Autowired
    private TikaMetadataFillerService tikaMetadataFillerService;

    @Autowired
    private TikaLoggingService tikaLoggingService;

    @Autowired
    private TikaParseContextService tikaParseContextService;

    @Autowired
    private TikaParsingService tikaParsingService;

    @Autowired
    private Parser parser;

    @Autowired
    private DigestingParser.Digester digester;

    @Override
    public ResponseEntity<Map<String, String>> postDocumentMetaForm(MultipartFile file) {
        try {
            Metadata metadata = new Metadata();
            Map<String, String> headers = new HashMap<>();
            // Convert MultipartFile headers if available
            if (file.getOriginalFilename() != null) {
                headers.put("Content-Disposition", "filename=" + file.getOriginalFilename());
            }
            if (file.getContentType() != null) {
                headers.put("Content-Type", file.getContentType());
            }

            Metadata result = parseMetadata(file.getInputStream(), metadata, headers);
            return ResponseEntity.ok(metadataToMap(result));
        } catch (Exception e) {
            LOG.error("Failed to process multipart form metadata extraction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> putDocumentGetMetaValue(String metadataKey, Resource body) {
        try {
            Metadata metadata = new Metadata();
            Map<String, String> headers = new HashMap<>();

            boolean success = false;
            try {
                parseMetadata(body.getInputStream(), metadata, headers);
                success = true;
            } catch (Exception e) {
                LOG.info("Failed to process field {}", metadataKey, e);
            }

            if (!success || metadata.get(metadataKey) == null) {
                return ResponseEntity.status(success ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST).build();
            }

            // Remove fields we don't care about for the response
            Metadata filteredMetadata = new Metadata();
            String[] values = metadata.getValues(metadataKey);
            for (String value : values) {
                filteredMetadata.add(metadataKey, value);
            }

            return ResponseEntity.ok(metadataToMap(filteredMetadata));
        } catch (Exception e) {
            LOG.error("Failed to extract metadata field: " + metadataKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> putDocumentMeta(Resource body) {
        try {
            Metadata metadata = new Metadata();
            Map<String, String> headers = new HashMap<>();

            Metadata result = parseMetadata(body.getInputStream(), metadata, headers);
            return ResponseEntity.ok(metadataToMap(result));
        } catch (Exception e) {
            LOG.error("Failed to extract metadata", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Additional Spring Boot endpoints to handle the legacy API patterns
     */
    @PutMapping(value = "/meta", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> getMetadata(@RequestBody Resource body,
                                       @RequestHeader HttpHeaders httpHeaders,
                                       @RequestHeader(value = "Accept", defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept,
                                       HttpServletRequest request) throws Exception {
        Metadata metadata = new Metadata();
        Map<String, String> headerMap = convertHeaders(httpHeaders);

        Metadata result = parseMetadata(body.getInputStream(), metadata, headerMap);

        if (accept.contains(MediaType.TEXT_PLAIN_VALUE)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(metadataToText(result));
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(metadataToMap(result));
        }
    }

    @PutMapping(value = "/meta/{field}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> getMetadataField(@RequestBody Resource body,
                                            @PathVariable String field,
                                            @RequestHeader HttpHeaders httpHeaders,
                                            @RequestHeader(value = "Accept", defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept,
                                            HttpServletRequest request) throws Exception {

        HttpStatus defaultErrorResponse = HttpStatus.BAD_REQUEST;
        Metadata metadata = new Metadata();
        Map<String, String> headerMap = convertHeaders(httpHeaders);

        boolean success = false;
        try {
            parseMetadata(body.getInputStream(), metadata, headerMap);
            defaultErrorResponse = HttpStatus.NOT_FOUND;
            success = true;
        } catch (Exception e) {
            LOG.info("Failed to process field {}", field, e);
        }

        if (!success || metadata.get(field) == null) {
            return ResponseEntity.status(defaultErrorResponse)
                    .body("Failed to get metadata field " + field);
        }

        // Remove fields we don't care about for the response
        Metadata filteredMetadata = new Metadata();
        String[] values = metadata.getValues(field);
        for (String value : values) {
            filteredMetadata.add(field, value);
        }

        if (accept.contains(MediaType.TEXT_PLAIN_VALUE)) {
            String value = filteredMetadata.get(field);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(value != null ? value : "");
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(metadataToMap(filteredMetadata));
        }
    }

    /**
     * Core metadata parsing method ported from legacy implementation
     */
    protected Metadata parseMetadata(InputStream is, Metadata metadata, Map<String, String> headers) throws IOException {
        final ParseContext context = new ParseContext();

        MultiValueMap<String, String> multiValueHeaders = new org.springframework.util.LinkedMultiValueMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            multiValueHeaders.add(entry.getKey(), entry.getValue());
        }

        tikaMetadataFillerService.fillMetadata(parser, metadata, multiValueHeaders);
        tikaParseContextService.fillParseContext(multiValueHeaders, metadata, context);

        // No need to parse embedded docs
        context.set(DocumentSelector.class, metadata1 -> false);

        tikaLoggingService.logRequest(LOG, "/meta", metadata);
        tikaParsingService.parse(parser, LOG, "/meta", is, new LanguageHandler() {
            public void endDocument() {
                String language = getLanguage().getLanguage();
                if (language != null) {
                    metadata.set("language", language);
                }
            }
        }, metadata, context);
        return metadata;
    }

    /**
     * Convert Metadata to Map for JSON responses
     */
    private Map<String, String> metadataToMap(Metadata metadata) {
        Map<String, String> map = new HashMap<>();
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            if (values.length == 1) {
                map.put(name, values[0]);
            } else if (values.length > 1) {
                map.put(name, String.join(", ", values));
            }
        }
        return map;
    }

    /**
     * Convert Metadata to text for plain text responses
     */
    private String metadataToText(Metadata metadata) {
        StringBuilder sb = new StringBuilder();
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            for (String value : values) {
                sb.append(name).append(": ").append(value).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Convert Spring HttpHeaders to Map
     */
    private Map<String, String> convertHeaders(HttpHeaders httpHeaders) {
        Map<String, String> headerMap = new HashMap<>();
        for (Map.Entry<String, java.util.List<String>> entry : httpHeaders.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                headerMap.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return headerMap;
    }
}
