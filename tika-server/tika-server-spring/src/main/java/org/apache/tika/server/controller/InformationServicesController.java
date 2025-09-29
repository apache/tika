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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tika.server.api.InformationServicesApi;
import org.apache.tika.server.model.DefaultDetector;
import org.apache.tika.server.model.DetailedParsers;
import org.apache.tika.server.model.GetMimetypeDetails200Response;
import org.apache.tika.server.model.Parsers;

/**
 * Controller for utility information services.
 * Handles endpoints for available parsers, detectors, mime types, etc.
 * Covers Information Services tag.
 */
@RestController
public class InformationServicesController implements InformationServicesApi {

    @Override
    public ResponseEntity<String> getEndpoints() {
        // TODO: Implement endpoint listing
        return InformationServicesApi.super.getEndpoints();
    }

    @Override
    public ResponseEntity<DefaultDetector> getDetectors() {
        // TODO: Implement detector information
        return InformationServicesApi.super.getDetectors();
    }

    @Override
    public ResponseEntity<Map<String, Object>> getMimetypes() {
        // TODO: Implement mime types listing
        return InformationServicesApi.super.getMimetypes();
    }

    @Override
    public ResponseEntity<GetMimetypeDetails200Response> getMimetypeDetails(String type, String subtype) {
        // TODO: Implement specific mime type details
        return InformationServicesApi.super.getMimetypeDetails(type, subtype);
    }

    @Override
    public ResponseEntity<Parsers> getParsers() {
        // TODO: Implement parsers listing
        return InformationServicesApi.super.getParsers();
    }

    @Override
    public ResponseEntity<DetailedParsers> getParsersDetails() {
        // TODO: Implement detailed parsers information
        return InformationServicesApi.super.getParsersDetails();
    }
}
