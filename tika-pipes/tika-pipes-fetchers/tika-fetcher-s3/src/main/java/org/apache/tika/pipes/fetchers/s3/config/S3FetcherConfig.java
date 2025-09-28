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
package org.apache.tika.pipes.fetchers.s3.config;

import java.util.List;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import org.pf4j.Extension;

import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;

@Extension
@Getter
@Setter
public class S3FetcherConfig extends DefaultFetcherConfig {
    private boolean spoolToTemp;
    private String region;
    private String profile;
    private String bucket;
    private String commaDelimitedLongs;
    private String prefix;
    private boolean extractUserMetadata;
    private int maxConnections;
    private String credentialsProvider;
    private long maxLength;
    private String sessionToken;
    private String accessKey;
    private String secretKey;
    private String endpointOverride;
    private boolean pathStyleAccessEnabled;
    private List<Long> throttleSeconds;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        S3FetcherConfig that = (S3FetcherConfig) o;
        return spoolToTemp == that.spoolToTemp && extractUserMetadata == that.extractUserMetadata && maxConnections == that.maxConnections && maxLength == that.maxLength && pathStyleAccessEnabled == that.pathStyleAccessEnabled && Objects.equals(region, that.region) && Objects.equals(profile, that.profile) && Objects.equals(bucket, that.bucket) &&
                Objects.equals(commaDelimitedLongs, that.commaDelimitedLongs) && Objects.equals(prefix, that.prefix) && Objects.equals(credentialsProvider, that.credentialsProvider) && Objects.equals(accessKey, that.accessKey) && Objects.equals(secretKey, that.secretKey) && Objects.equals(endpointOverride, that.endpointOverride) &&
                Objects.equals(throttleSeconds, that.throttleSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spoolToTemp, region, profile, bucket, commaDelimitedLongs, prefix, extractUserMetadata, maxConnections, credentialsProvider, maxLength, accessKey, secretKey, endpointOverride, pathStyleAccessEnabled, throttleSeconds);
    }
}
