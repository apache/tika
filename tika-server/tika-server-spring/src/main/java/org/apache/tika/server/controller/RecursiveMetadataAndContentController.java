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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.server.api.RecursiveMetadataAndContentApi;

/**
 * Controller for recursive metadata and content extraction.
 * Handles the /rmeta endpoints for the Recursive Metadata and Content tag.
 */
@RestController
public class RecursiveMetadataAndContentController implements RecursiveMetadataAndContentApi {
    @Override
    public Optional<NativeWebRequest> getRequest() {
        return RecursiveMetadataAndContentApi.super.getRequest();
    }

    @Override
    public ResponseEntity<List<Map<String, String>>> postRmetaForm(MultipartFile file) {
        return RecursiveMetadataAndContentApi.super.postRmetaForm(file);
    }

    @Override
    public ResponseEntity<List<Map<String, String>>> postRmetaFormHandler(String handler, MultipartFile file) {
        return RecursiveMetadataAndContentApi.super.postRmetaFormHandler(handler, file);
    }

    @Override
    public ResponseEntity<List<Map<String, String>>> putRmeta(Resource body) {
        return RecursiveMetadataAndContentApi.super.putRmeta(body);
    }

    @Override
    public ResponseEntity<List<Map<String, String>>> putRmetaHandler(String handler, Resource body) {
        return RecursiveMetadataAndContentApi.super.putRmetaHandler(handler, body);
    }
}
