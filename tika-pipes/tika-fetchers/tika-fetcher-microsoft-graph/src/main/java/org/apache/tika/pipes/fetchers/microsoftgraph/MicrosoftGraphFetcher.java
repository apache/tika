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
package org.apache.tika.pipes.fetchers.microsoftgraph;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.ClientCertificateCredentialsConfig;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.ClientSecretCredentialsConfig;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.MicrosoftGraphFetcherConfig;

/**
 * Fetches files from Microsoft Graph API.
 * Fetch keys are ${siteDriveId},${driveItemId}
 */
public class MicrosoftGraphFetcher extends AbstractFetcher implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftGraphFetcher.class);
    private GraphServiceClient graphClient;
    private MicrosoftGraphFetcherConfig microsoftGraphFetcherConfig;
    private long[] throttleSeconds;

    public MicrosoftGraphFetcher() {

    }

    public MicrosoftGraphFetcher(MicrosoftGraphFetcherConfig microsoftGraphFetcherConfig) {
        this.microsoftGraphFetcherConfig = microsoftGraphFetcherConfig;
    }

    /**
     * Set seconds to throttle retries as a comma-delimited list, e.g.: 30,60,120,600
     *
     * @param commaDelimitedLongs
     * @throws TikaConfigException
     */
    @Field
    public void setThrottleSeconds(String commaDelimitedLongs) throws TikaConfigException {
        String[] longStrings = commaDelimitedLongs.split(",");
        long[] seconds = new long[longStrings.length];
        for (int i = 0; i < longStrings.length; i++) {
            try {
                seconds[i] = Long.parseLong(longStrings[i]);
            } catch (NumberFormatException e) {
                throw new TikaConfigException(e.getMessage());
            }
        }
        setThrottleSeconds(seconds);
    }

    public void setThrottleSeconds(long[] throttleSeconds) {
        this.throttleSeconds = throttleSeconds;
    }

    @Override
    public void initialize(Map<String, Param> map) {
        String[] scopes = microsoftGraphFetcherConfig
                .getScopes().toArray(new String[0]);
        if (microsoftGraphFetcherConfig.getCredentials() instanceof ClientCertificateCredentialsConfig) {
            ClientCertificateCredentialsConfig credentials =
                    (ClientCertificateCredentialsConfig) microsoftGraphFetcherConfig.getCredentials();
            graphClient = new GraphServiceClient(
                    new ClientCertificateCredentialBuilder().clientId(credentials.getClientId())
                            .tenantId(credentials.getTenantId()).pfxCertificate(
                                    new ByteArrayInputStream(credentials.getCertificateBytes()))
                            .clientCertificatePassword(credentials.getCertificatePassword())
                            .build(), scopes);
        } else if (microsoftGraphFetcherConfig.getCredentials() instanceof ClientSecretCredentialsConfig) {
            ClientSecretCredentialsConfig credentials =
                    (ClientSecretCredentialsConfig) microsoftGraphFetcherConfig.getCredentials();
            graphClient = new GraphServiceClient(
                    new ClientSecretCredentialBuilder().tenantId(credentials.getTenantId())
                            .clientId(credentials.getClientId())
                            .clientSecret(credentials.getClientSecret()).build(), scopes);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler initializableProblemHandler)
            throws TikaConfigException {
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) throws TikaException, IOException {
        int tries = 0;
        Exception ex;
        do {
            try {
                long start = System.currentTimeMillis();
                String[] fetchKeySplit = fetchKey.split(",");
                String siteDriveId = fetchKeySplit[0];
                String driveItemId = fetchKeySplit[1];
                InputStream is = graphClient.drives().byDriveId(siteDriveId).items()
                        .byDriveItemId(driveItemId).content().get();

                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("Total to fetch {}", elapsed);
                return is;
            } catch (Exception e) {
                LOGGER.warn("Exception fetching on retry=" + tries, e);
                ex = e;
            }
            LOGGER.warn("Sleeping for {} seconds before retry", throttleSeconds[tries]);
            try {
                Thread.sleep(throttleSeconds[tries]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } while (++tries < throttleSeconds.length);
        throw new TikaException("Could not parse " + fetchKey, ex);
    }
}
