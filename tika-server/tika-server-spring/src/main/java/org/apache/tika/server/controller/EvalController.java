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

package org.apache.tika.server.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;

import org.apache.tika.server.api.EvalResourceApi;
import org.apache.tika.server.model.PutEvalCompare200Response;
import org.apache.tika.server.model.PutEvalCompareRequest;
import org.apache.tika.server.model.PutEvalProfile200Response;
import org.apache.tika.server.model.PutEvalProfileRequest;

public class EvalController implements EvalResourceApi {
    @Override
    public Optional<NativeWebRequest> getRequest() {
        return EvalResourceApi.super.getRequest();
    }

    @Override
    public ResponseEntity<PutEvalCompare200Response> putEvalCompare(PutEvalCompareRequest putEvalCompareRequest) {
        return EvalResourceApi.super.putEvalCompare(putEvalCompareRequest);
    }

    @Override
    public ResponseEntity<PutEvalProfile200Response> putEvalProfile(PutEvalProfileRequest putEvalProfileRequest) {
        return EvalResourceApi.super.putEvalProfile(putEvalProfileRequest);
    }
}
