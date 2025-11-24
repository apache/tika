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

import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.ClientCertificateCredentialsConfig;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.ClientSecretCredentialsConfig;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.MicrosoftGraphFetcherConfig;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Fetches files from Microsoft Graph API.
 * Fetch keys are ${siteDriveId},${driveItemId}
 */
public class MicrosoftGraphFetcher extends AbstractTikaExtension implements Fetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftGraphFetcher.class);

    private MicrosoftGraphFetcherConfig config;
    private GraphServiceClient graphClient;

    private MicrosoftGraphFetcher(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }

    public static MicrosoftGraphFetcher build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        MicrosoftGraphFetcherConfig config = MicrosoftGraphFetcherConfig.load(extensionConfig.jsonConfig());
        MicrosoftGraphFetcher fetcher = new MicrosoftGraphFetcher(extensionConfig);
        fetcher.config = config;
        fetcher.initialize();
        return fetcher;
    }

    private void initialize() throws TikaConfigException {
        String[] scopes = config.getScopes().toArray(new String[0]);
        if (config.getClientCertificateCredentialsConfig() != null) {
            ClientCertificateCredentialsConfig credentials = config.getClientCertificateCredentialsConfig();
            graphClient = new GraphServiceClient(
                    new ClientCertificateCredentialBuilder().clientId(credentials.getClientId())
                            .tenantId(credentials.getTenantId()).pfxCertificate(
                                    new ByteArrayInputStream(credentials.getCertificateBytes()))
                            .clientCertificatePassword(credentials.getCertificatePassword())
                            .build(), scopes);
        } else if (config.getClientSecretCredentialsConfig() != null) {
            ClientSecretCredentialsConfig credentials = config.getClientSecretCredentialsConfig();
            graphClient = new GraphServiceClient(
                    new ClientSecretCredentialBuilder().tenantId(credentials.getTenantId())
                            .clientId(credentials.getClientId())
                            .clientSecret(credentials.getClientSecret()).build(), scopes);
        } else {
            throw new TikaConfigException("Must specify either clientCertificateCredentialsConfig or clientSecretCredentialsConfig");
        }
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) throws TikaException, IOException {
        long[] throttleSeconds = config.getThrottleSeconds();
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
            if (throttleSeconds != null && tries < throttleSeconds.length) {
                LOGGER.warn("Sleeping for {} seconds before retry", throttleSeconds[tries]);
                try {
                    Thread.sleep(throttleSeconds[tries] * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } while (throttleSeconds != null && ++tries < throttleSeconds.length);
        throw new TikaException("Could not parse " + fetchKey, ex);
    }
}
