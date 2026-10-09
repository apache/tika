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

import org.apache.tika.config.OperatorOnly;
import org.apache.tika.exception.TikaConfigException;

public class AmazonTranscribeConfig implements Serializable {

    private String clientId;
    private String clientSecret;
    private String bucketName;
    private String region;

    public String getClientId() {
        return clientId;
    }

    @OperatorOnly
    public void setClientId(String clientId) throws TikaConfigException {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    @OperatorOnly
    public void setClientSecret(String clientSecret) throws TikaConfigException {
        this.clientSecret = clientSecret;
    }

    public String getBucketName() {
        return bucketName;
    }

    @OperatorOnly
    public void setBucket(String bucketName) throws TikaConfigException {
        this.bucketName = bucketName;
    }

    public String getRegion() {
        return region;
    }

    @OperatorOnly
    public void setRegion(String region) throws TikaConfigException {
        this.region = region;
    }
}
