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

import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.server.api.TikaResourceApi;

/**
 * Controller for content extraction with various handlers and formats.
 * Handles the /tika endpoints for the Tika Resource tag.
 */
@RestController
public class TikaController implements TikaResourceApi {
    @Override
    public Optional<NativeWebRequest> getRequest() {
        return TikaResourceApi.super.getRequest();
    }

    @Override
    public ResponseEntity<String> getTika() {
        return TikaResourceApi.super.getTika();
    }

    @Override
    public ResponseEntity<String> postTikaForm(MultipartFile file) {
        return TikaResourceApi.super.postTikaForm(file);
    }

    @Override
    public ResponseEntity<String> postTikaFormMain(MultipartFile file) {
        return TikaResourceApi.super.postTikaFormMain(file);
    }

    @Override
    public ResponseEntity<String> putTika(Resource body) {
        return TikaResourceApi.super.putTika(body);
    }

    @Override
    public ResponseEntity<Map<String, String>> putTikaHandler(String handler, Resource body) {
        return TikaResourceApi.super.putTikaHandler(handler, body);
    }

    @Override
    public ResponseEntity<String> putTikaMain(Resource body) {
        return TikaResourceApi.super.putTikaMain(body);
    }
}
