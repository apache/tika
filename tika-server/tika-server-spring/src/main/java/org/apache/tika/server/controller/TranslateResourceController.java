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

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tika.server.api.TranslateResourceApi;

/**
 * Controller for document translation services with pluggable translators.
 * Handles the /translate and /version endpoints for the Translate Resource tag.
 */
@RestController
public class TranslateResourceController implements TranslateResourceApi {
    @Override
    public ResponseEntity<String> getVersion() {
        return TranslateResourceApi.super.getVersion();
    }

    @Override
    public ResponseEntity<String> postAutoTranslate(String translator, String dest, Resource body) {
        return TranslateResourceApi.super.postAutoTranslate(translator, dest, body);
    }

    @Override
    public ResponseEntity<String> postTranslateAllTranslatorSrcDest(String translator, String src, String dest, Resource body) {
        return TranslateResourceApi.super.postTranslateAllTranslatorSrcDest(translator, src, dest, body);
    }

    @Override
    public ResponseEntity<String> putAutoTranslate(String translator, String dest, Resource body) {
        return TranslateResourceApi.super.putAutoTranslate(translator, dest, body);
    }

    @Override
    public ResponseEntity<String> putTranslateAllTranslatorSrcDest(String translator, String src, String dest, Resource body) {
        return TranslateResourceApi.super.putTranslateAllTranslatorSrcDest(translator, src, dest, body);
    }
}
