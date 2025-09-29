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

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.server.api.XmpMetadataResourceApi;

public class XmpMetadataController implements XmpMetadataResourceApi {
    @Override
    public Optional<NativeWebRequest> getRequest() {
        return XmpMetadataResourceApi.super.getRequest();
    }

    @Override
    public ResponseEntity<String> postDocumentXmpmetaForm(MultipartFile file) {
        return XmpMetadataResourceApi.super.postDocumentXmpmetaForm(file);
    }

    @Override
    public ResponseEntity<String> putDocumentGetXmpmetaValue(String metadataKey, Resource body) {
        return XmpMetadataResourceApi.super.putDocumentGetXmpmetaValue(metadataKey, body);
    }

    @Override
    public ResponseEntity<String> putDocumentXmpmeta(Resource body) {
        return XmpMetadataResourceApi.super.putDocumentXmpmeta(body);
    }
}
