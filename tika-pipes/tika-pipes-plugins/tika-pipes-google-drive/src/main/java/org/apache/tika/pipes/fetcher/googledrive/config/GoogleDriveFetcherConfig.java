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
package org.apache.tika.pipes.fetcher.googledrive.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public class GoogleDriveFetcherConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static GoogleDriveFetcherConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, GoogleDriveFetcherConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse GoogleDriveFetcherConfig from JSON", e);
        }
    }

    private List<Long> throttleSeconds;
    private boolean spoolToTemp;
    private String serviceAccountKeyBase64;
    private String subjectUser;
    private String applicationName = "tika-pipes";
    private List<String> scopes = new ArrayList<>();

    public List<Long> getThrottleSeconds() {
        return throttleSeconds;
    }

    public GoogleDriveFetcherConfig setThrottleSeconds(List<Long> throttleSeconds) {
        this.throttleSeconds = throttleSeconds;
        return this;
    }

    public boolean isSpoolToTemp() {
        return spoolToTemp;
    }

    public GoogleDriveFetcherConfig setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
        return this;
    }

    public String getServiceAccountKeyBase64() {
        return serviceAccountKeyBase64;
    }

    public GoogleDriveFetcherConfig setServiceAccountKeyBase64(String serviceAccountKeyBase64) {
        this.serviceAccountKeyBase64 = serviceAccountKeyBase64;
        return this;
    }

    public String getSubjectUser() {
        return subjectUser;
    }

    public GoogleDriveFetcherConfig setSubjectUser(String subjectUser) {
        this.subjectUser = subjectUser;
        return this;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public GoogleDriveFetcherConfig setApplicationName(String applicationName) {
        this.applicationName = applicationName;
        return this;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public GoogleDriveFetcherConfig setScopes(List<String> scopes) {
        this.scopes = scopes;
        return this;
    }
}
