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
package org.apache.tika.pipes.emitter.s3;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginJson;

public record S3EmitterConfig(
        String region,
        String bucket,
        String credentialsProvider,
        String profile,
        String accessKey,
        String secretKey,
        String endpointConfigurationService,
        String prefix,
        String fileExtension,
        Boolean spoolToTemp,
        Integer maxConnections,
        boolean pathStyleAccessEnabled
) {

    private static final String DEFAULT_FILE_EXTENSION = "json";
    private static final boolean DEFAULT_SPOOL_TO_TEMP = true;
    private static final int DEFAULT_MAX_CONNECTIONS = 50;

    /**
     * Boxed so an absent value is distinguishable from an explicit one. maxConnections in
     * particular reaches ApacheHttpClient, which rejects zero.
     */
    public S3EmitterConfig {
        if (fileExtension == null) {
            fileExtension = DEFAULT_FILE_EXTENSION;
        }
        if (spoolToTemp == null) {
            spoolToTemp = DEFAULT_SPOOL_TO_TEMP;
        }
        if (maxConnections == null) {
            maxConnections = DEFAULT_MAX_CONNECTIONS;
        }
    }

    public static S3EmitterConfig load(final String json)
            throws TikaConfigException {
        return PluginJson.read(json, S3EmitterConfig.class);
    }

    public void validate() throws TikaConfigException {
        if (bucket == null || bucket.isBlank()) {
            throw new TikaConfigException("'bucket' must not be empty");
        }
        if (region == null || region.isBlank()) {
            throw new TikaConfigException("'region' must not be empty");
        }
        if (credentialsProvider == null || credentialsProvider.isBlank()) {
            throw new TikaConfigException("'credentialsProvider' must be set to 'profile', 'instance' or 'key_secret'");
        }
        if (!credentialsProvider.equals("profile")
                && !credentialsProvider.equals("instance")
                && !credentialsProvider.equals("key_secret")) {
            throw new TikaConfigException(
                    "credentialsProvider must be 'profile', 'instance' or 'key_secret', but was: " + credentialsProvider);
        }
        if (credentialsProvider.equals("profile") && (profile == null || profile.isBlank())) {
            throw new TikaConfigException("'profile' must be set when credentialsProvider is 'profile'");
        }
        if (credentialsProvider.equals("key_secret")) {
            if (accessKey == null || accessKey.isBlank()) {
                throw new TikaConfigException("'accessKey' must be set when credentialsProvider is 'key_secret'");
            }
            if (secretKey == null || secretKey.isBlank()) {
                throw new TikaConfigException("'secretKey' must be set when credentialsProvider is 'key_secret'");
            }
        }
    }

    // Handle prefix normalization (strip trailing /)
    public String normalizedPrefix() {
        if (prefix == null) {
            return null;
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}
