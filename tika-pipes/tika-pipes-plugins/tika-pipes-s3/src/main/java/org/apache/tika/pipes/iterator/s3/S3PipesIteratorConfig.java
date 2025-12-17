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
package org.apache.tika.pipes.iterator.s3;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.pipesiterator.PipesIteratorConfig;

public class S3PipesIteratorConfig extends PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static S3PipesIteratorConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, S3PipesIteratorConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse S3PipesIteratorConfig from JSON", e);
        }
    }

    private String prefix = "";
    private String region;
    private String accessKey;
    private String secretKey;
    private String endpointConfigurationService;
    private String credentialsProvider;
    private String profile;
    private String bucket;
    private String fileNamePattern;
    private int maxConnections = 50;
    private boolean pathStyleAccessEnabled = false;

    public String getPrefix() {
        return prefix;
    }

    public String getRegion() {
        return region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getEndpointConfigurationService() {
        return endpointConfigurationService;
    }

    public String getCredentialsProvider() {
        return credentialsProvider;
    }

    public String getProfile() {
        return profile;
    }

    public String getBucket() {
        return bucket;
    }

    public String getFileNamePattern() {
        return fileNamePattern;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public boolean isPathStyleAccessEnabled() {
        return pathStyleAccessEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof S3PipesIteratorConfig that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return maxConnections == that.maxConnections &&
                pathStyleAccessEnabled == that.pathStyleAccessEnabled &&
                Objects.equals(prefix, that.prefix) &&
                Objects.equals(region, that.region) &&
                Objects.equals(accessKey, that.accessKey) &&
                Objects.equals(secretKey, that.secretKey) &&
                Objects.equals(endpointConfigurationService, that.endpointConfigurationService) &&
                Objects.equals(credentialsProvider, that.credentialsProvider) &&
                Objects.equals(profile, that.profile) &&
                Objects.equals(bucket, that.bucket) &&
                Objects.equals(fileNamePattern, that.fileNamePattern);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(prefix);
        result = 31 * result + Objects.hashCode(region);
        result = 31 * result + Objects.hashCode(accessKey);
        result = 31 * result + Objects.hashCode(secretKey);
        result = 31 * result + Objects.hashCode(endpointConfigurationService);
        result = 31 * result + Objects.hashCode(credentialsProvider);
        result = 31 * result + Objects.hashCode(profile);
        result = 31 * result + Objects.hashCode(bucket);
        result = 31 * result + Objects.hashCode(fileNamePattern);
        result = 31 * result + maxConnections;
        result = 31 * result + Boolean.hashCode(pathStyleAccessEnabled);
        return result;
    }
}
