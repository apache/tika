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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tika.server.api.PipesResourceApi;
import org.apache.tika.server.model.PostPipes200Response;
import org.apache.tika.server.model.PostPipesRequest;

/**
 * Controller for processing documents through the Tika pipes framework.
 * Handles the /pipes endpoint for the Pipes Resource tag.
 */
@RestController
@RequestMapping("/pipes")
public class PipesController implements PipesResourceApi {

    @Override
    public ResponseEntity<PostPipes200Response> postPipes(PostPipesRequest postPipesRequest) {
        // TODO: Implement POST /pipes
        return PipesResourceApi.super.postPipes(postPipesRequest);
    }
}
