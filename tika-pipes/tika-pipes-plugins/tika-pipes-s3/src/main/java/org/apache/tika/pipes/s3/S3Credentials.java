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
package org.apache.tika.pipes.s3;

import java.util.List;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;

import org.apache.tika.exception.TikaConfigException;

/**
 * Maps the {@code credentialsProvider} setting shared by the S3 fetcher, emitter and
 * pipes iterator to an AWS SDK credentials provider.
 */
public final class S3Credentials {

    public static final String INSTANCE = "instance";
    public static final String WEB_IDENTITY = "web_identity";
    public static final String PROFILE = "profile";
    public static final String KEY_SECRET = "key_secret";
    public static final String DEFAULT = "default";

    public static final List<String> NAMES = List.of(INSTANCE, WEB_IDENTITY, PROFILE, KEY_SECRET, DEFAULT);

    private static final String DESCRIBE_NAMES =
            "'instance', 'web_identity', 'profile', 'key_secret' or 'default'";

    private S3Credentials() {
    }

    public static boolean isKnown(String credentialsProvider) {
        return NAMES.contains(credentialsProvider);
    }

    public static String describeNames() {
        return DESCRIBE_NAMES;
    }

    public static AwsCredentialsProvider build(String credentialsProvider, String profile,
                                               String accessKey, String secretKey)
            throws TikaConfigException {
        if (credentialsProvider == null) {
            throw new TikaConfigException("credentialsProvider must be set to " + DESCRIBE_NAMES);
        }
        switch (credentialsProvider) {
            case INSTANCE:
                return InstanceProfileCredentialsProvider.builder().build();
            case WEB_IDENTITY:
                return WebIdentityTokenFileCredentialsProvider.builder().build();
            case PROFILE:
                return ProfileCredentialsProvider.builder().profileName(profile).build();
            case KEY_SECRET:
                return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
            case DEFAULT:
                return DefaultCredentialsProvider.builder().build();
            default:
                throw new TikaConfigException("credentialsProvider must be " + DESCRIBE_NAMES
                        + ", but was: " + credentialsProvider);
        }
    }
}
