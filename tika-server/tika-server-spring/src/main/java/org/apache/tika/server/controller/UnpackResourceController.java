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

import org.apache.tika.server.api.UnpackResourceApi;

/**
 * Controller for extracting embedded documents and returning raw bytes.
 * Handles the /unpack endpoints for the Unpack Resource tag.
 * Also includes text comparison and profiling using TikaEval framework (/eval endpoints).
 */
@RestController
public class UnpackResourceController implements UnpackResourceApi {
    @Override
    public ResponseEntity<Resource> putUnpack(Resource body) {
        return UnpackResourceApi.super.putUnpack(body);
    }

    @Override
    public ResponseEntity<Resource> putUnpackAll(Resource body) {
        return UnpackResourceApi.super.putUnpackAll(body);
    }
}
