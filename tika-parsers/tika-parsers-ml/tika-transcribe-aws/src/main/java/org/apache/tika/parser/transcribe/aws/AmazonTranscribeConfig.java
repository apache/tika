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
package org.apache.tika.parser.transcribe.aws;

import java.io.Serializable;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

public class AmazonTranscribeConfig implements Serializable {

    private String clientId;
    private String clientSecret;
    private String bucketName;
    private String region;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) throws TikaConfigException {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) throws TikaConfigException {
        this.clientSecret = clientSecret;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucket(String bucketName) throws TikaConfigException {
        this.bucketName = bucketName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) throws TikaConfigException {
        this.region = region;
    }

    /**
     * RuntimeConfig blocks modification of security-sensitive credential and
     * infrastructure fields at runtime. When a config is obtained from ParseContext
     * (i.e. user-provided at parse time), it should be deserialized as a RuntimeConfig
     * to prevent credential/infrastructure injection.
     * <p>
     * This class is deserialized by ConfigDeserializer (in tika-serialization) which uses
     * Jackson to populate fields via setters. If the JSON contains any credential fields, the
     * overridden setters will throw TikaConfigException.
     */
    public static class RuntimeConfig extends AmazonTranscribeConfig {

        public RuntimeConfig() {
            super();
        }

        @Override
        public void setClientId(String clientId) throws TikaConfigException {
            if (!StringUtils.isBlank(clientId)) {
                throw new TikaConfigException(
                        "Cannot modify clientId at runtime. " +
                                "Credentials must be configured at parser initialization time.");
            }
        }

        @Override
        public void setClientSecret(String clientSecret) throws TikaConfigException {
            if (!StringUtils.isBlank(clientSecret)) {
                throw new TikaConfigException(
                        "Cannot modify clientSecret at runtime. " +
                                "Credentials must be configured at parser initialization time.");
            }
        }

        @Override
        public void setBucket(String bucketName) throws TikaConfigException {
            if (!StringUtils.isBlank(bucketName)) {
                throw new TikaConfigException(
                        "Cannot modify bucketName at runtime. " +
                                "Infrastructure must be configured at parser initialization time.");
            }
        }

        @Override
        public void setRegion(String region) throws TikaConfigException {
            if (!StringUtils.isBlank(region)) {
                throw new TikaConfigException(
                        "Cannot modify region at runtime. " +
                                "Infrastructure must be configured at parser initialization time.");
            }
        }
    }
}
