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
package org.apache.tika.pipes.fetchers.google.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.pipes.fetcher.config.AbstractConfig;

public class GoogleFetcherConfig extends AbstractConfig {
    private long[] throttleSeconds;
    private boolean spoolToTemp;
    protected String serviceAccountKeyBase64;
    protected String subjectUser;
    private List<String> scopes = new ArrayList<>();

    public boolean isSpoolToTemp() {
        return spoolToTemp;
    }

    public GoogleFetcherConfig setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
        return this;
    }

    public long[] getThrottleSeconds() {
        return throttleSeconds;
    }

    public GoogleFetcherConfig setThrottleSeconds(long[] throttleSeconds) {
        this.throttleSeconds = throttleSeconds;
        return this;
    }

    public String getServiceAccountKeyBase64() {
        return serviceAccountKeyBase64;
    }

    public GoogleFetcherConfig setServiceAccountKeyBase64(String serviceAccountKeyBase64) {
        this.serviceAccountKeyBase64 = serviceAccountKeyBase64;
        return this;
    }

    public String getSubjectUser() {
        return subjectUser;
    }

    public GoogleFetcherConfig setSubjectUser(String subjectUser) {
        this.subjectUser = subjectUser;
        return this;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public GoogleFetcherConfig setScopes(List<String> scopes) {
        this.scopes = scopes;
        return this;
    }
}
