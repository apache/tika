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
package org.apache.tika.pipes.fetchers.googledrive.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import org.pf4j.Extension;

import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;

@Extension
@Getter
@Setter
public class GoogleDriveFetcherConfig extends DefaultFetcherConfig {
    private List<Long> throttleSeconds;
    private boolean spoolToTemp;
    private String serviceAccountKeyBase64;
    private String subjectUser;
    private String applicationName = "tika-pipes";
    private List<String> scopes = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GoogleDriveFetcherConfig that = (GoogleDriveFetcherConfig) o;
        return spoolToTemp == that.spoolToTemp && Objects.equals(throttleSeconds, that.throttleSeconds) && Objects.equals(serviceAccountKeyBase64, that.serviceAccountKeyBase64) && Objects.equals(subjectUser, that.subjectUser) && Objects.equals(applicationName, that.applicationName) && Objects.equals(scopes, that.scopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(throttleSeconds, spoolToTemp, serviceAccountKeyBase64, subjectUser, applicationName, scopes);
    }
}
