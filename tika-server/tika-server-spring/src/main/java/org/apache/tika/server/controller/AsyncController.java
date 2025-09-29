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

import org.apache.tika.server.api.AsyncResourceApi;
import org.apache.tika.server.model.PostAsync200Response;
import org.apache.tika.server.model.PostAsyncRequest;

/**
 * Controller for asynchronous processing of documents using fetch-emit tuples.
 * Handles the /async endpoint for the Async Resource tag.
 */
@RestController
@RequestMapping("/async")
public class AsyncController implements AsyncResourceApi {

    @Override
    public ResponseEntity<PostAsync200Response> postAsync(PostAsyncRequest postAsyncRequest) {
        // TODO: Implement async processing
        return AsyncResourceApi.super.postAsync(postAsyncRequest);
    }
}
