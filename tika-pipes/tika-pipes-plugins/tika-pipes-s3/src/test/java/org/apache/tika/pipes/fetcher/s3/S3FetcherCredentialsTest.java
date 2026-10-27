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
package org.apache.tika.pipes.fetcher.s3;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.fetcher.s3.config.S3FetcherConfig;
import org.apache.tika.plugins.ExtensionConfig;

public class S3FetcherCredentialsTest {

    @Test
    public void testWebIdentity() throws Exception {
        assertInstanceOf(WebIdentityTokenFileCredentialsProvider.class,
                S3Fetcher.buildCredentialsProvider(new S3FetcherConfig().setCredentialsProvider("web_identity")));
    }

    @Test
    public void testDefaultsToInstance() throws Exception {
        assertInstanceOf(InstanceProfileCredentialsProvider.class,
                S3Fetcher.buildCredentialsProvider(new S3FetcherConfig()));
    }

    @Test
    public void testKeySecret() throws Exception {
        S3FetcherConfig config = new S3FetcherConfig()
                .setCredentialsProvider("key_secret")
                .setAccessKey("access")
                .setSecretKey("secret");
        assertInstanceOf(StaticCredentialsProvider.class, S3Fetcher.buildCredentialsProvider(config));
    }

    @Test
    public void testUnknownProvider() {
        TikaConfigException e = assertThrows(TikaConfigException.class,
                () -> S3Fetcher.buildCredentialsProvider(new S3FetcherConfig().setCredentialsProvider("bogus")));
        assertTrue(e.getMessage().contains("'web_identity'"), e.getMessage());
    }

    @Test
    public void testBuildWithWebIdentity() throws Exception {
        // Credentials resolve on the first request, so the fetcher builds without a web identity
        // token in the environment.
        String json = "{\"bucket\": \"bucket\", \"region\": \"us-east-1\", \"credentialsProvider\": \"web_identity\"}";
        assertNotNull(S3Fetcher.build(new ExtensionConfig("s3", "s3-fetcher", json)));
    }

    @Test
    public void testStsAvailable() {
        // The web identity provider loads this from the sts module when resolving credentials.
        // Without it, every request fails with "To use web identity tokens, the 'sts' service
        // module must be on the class path."
        assertDoesNotThrow(() -> Class.forName(
                "software.amazon.awssdk.services.sts.internal.StsWebIdentityCredentialsProviderFactory"));
    }
}
