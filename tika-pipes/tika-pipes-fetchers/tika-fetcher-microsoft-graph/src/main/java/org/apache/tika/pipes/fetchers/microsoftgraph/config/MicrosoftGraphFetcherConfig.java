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
package org.apache.tika.pipes.fetchers.microsoftgraph.config;

import java.util.ArrayList;
import java.util.List;

import org.pf4j.Extension;

import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;

@Extension
public class MicrosoftGraphFetcherConfig extends DefaultFetcherConfig {
    private List<Long> throttleSeconds;
    private boolean spoolToTemp;
    protected String tenantId;
    protected String clientId;
    private String clientSecret;
    private String certificateBytesBase64;
    private String certificatePassword;
    private List<String> scopes = new ArrayList<>();

    public boolean isSpoolToTemp() {
        return spoolToTemp;
    }

    public MicrosoftGraphFetcherConfig setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
        return this;
    }

    public List<Long> getThrottleSeconds() {
        return throttleSeconds;
    }

    public MicrosoftGraphFetcherConfig setThrottleSeconds(List<Long> throttleSeconds) {
        this.throttleSeconds = throttleSeconds;
        return this;
    }

    public String getTenantId() {
        return tenantId;
    }

    public MicrosoftGraphFetcherConfig setTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public MicrosoftGraphFetcherConfig setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public MicrosoftGraphFetcherConfig setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getCertificateBytesBase64() {
        return certificateBytesBase64;
    }

    public void setCertificateBytesBase64(String certificateBytesBase64) {
        this.certificateBytesBase64 = certificateBytesBase64;
    }

    public String getCertificatePassword() {
        return certificatePassword;
    }

    public MicrosoftGraphFetcherConfig setCertificatePassword(String certificatePassword) {
        this.certificatePassword = certificatePassword;
        return this;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public MicrosoftGraphFetcherConfig setScopes(List<String> scopes) {
        this.scopes = scopes;
        return this;
    }
}
