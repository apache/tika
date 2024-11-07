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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.MicrosoftGraphFetcherConfig;

/**
 * Fetches files from Microsoft Graph API.
 * Fetch keys are ${siteDriveId},${driveItemId}
 */
public class MicrosoftGraphFetcher extends AbstractFetcher implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MicrosoftGraphFetcher.class);
    private GraphServiceClient graphClient;
    private MicrosoftGraphFetcherConfig config = new MicrosoftGraphFetcherConfig();
    private long[] throttleSeconds;
    private boolean spoolToTemp;


    public MicrosoftGraphFetcher() {

    }

    public MicrosoftGraphFetcher(MicrosoftGraphFetcherConfig config) {
        this.config = config;
    }

    /**
     * Set seconds to throttle retries as a comma-delimited list, e.g.: 30,60,120,600
     *
     * @param commaDelimitedLongs
     * @throws TikaConfigException
     */
    @Field
    public void setThrottleSeconds(String commaDelimitedLongs) throws TikaConfigException {
        String[] longStrings = (commaDelimitedLongs == null ? "" : commaDelimitedLongs).split(",");
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

    @Field
    public void setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
    }

    @Field
    public void setTenantId(String tenantId) {
        config.setTenantId(tenantId);
    }

    @Field
    public void setClientId(String clientId) {
        config.setClientId(clientId);
    }

    @Field
    public void setClientSecret(String clientSecret) {
        config.setClientSecret(clientSecret);
    }

    @Field
    public void setCertificateBytesBase64(String certificateBytesBase64) {
        config.setCertificateBytesBase64(certificateBytesBase64);
    }

    @Field
    public void setCertificatePassword(String certificatePassword) {
        config.setCertificatePassword(certificatePassword);
    }

    @Field
    public void setScopes(List<String> scopes) {
        config.setScopes(new ArrayList<>(scopes));
        if (config.getScopes().isEmpty()) {
            config.getScopes().add("https://graph.microsoft.com/.default");
        }
    }

    @Override
    public void initialize(Map<String, Param> map) {
        String[] scopes = config
                .getScopes()
                .toArray(new String[0]);
        if (config.getCertificateBytesBase64() != null) {
            graphClient = new GraphServiceClient(new ClientCertificateCredentialBuilder()
                    .clientId(config.getClientId())
                    .tenantId(config.getTenantId())
                    .pfxCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(config.getCertificateBytesBase64())))
                    .clientCertificatePassword(config.getCertificatePassword())
                    .build(), scopes);
        } else if (config.getClientSecret() != null) {
            graphClient = new GraphServiceClient(new ClientSecretCredentialBuilder()
                    .tenantId(config.getTenantId())
                    .clientId(config.getClientId())
                    .clientSecret(config.getClientSecret())
                    .build(), scopes);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler initializableProblemHandler) throws TikaConfigException {
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) throws TikaException, IOException {
        int tries = 0;
        Exception ex;
        do {
            long start = System.currentTimeMillis();
            try {
                String[] fetchKeySplit = fetchKey.split(",");
                String siteDriveId = fetchKeySplit[0];
                String driveItemId = fetchKeySplit[1];
                InputStream is = graphClient
                        .drives()
                        .byDriveId(siteDriveId)
                        .items()
                        .byDriveItemId(driveItemId)
                        .content()
                        .get();

                if (is == null) {
                    throw new IOException("Empty input stream when we tried to parse " + fetchKey);
                }
                if (spoolToTemp) {
                    File tempFile = Files
                            .createTempFile("spooled-temp", ".dat")
                            .toFile();
                    FileUtils.copyInputStreamToFile(is, tempFile);
                    LOGGER.info("Spooled to temp file {}", tempFile);
                    return TikaInputStream.get(tempFile.toPath());
                }
                return TikaInputStream.get(is);
            } catch (Exception e) {
                LOGGER.warn("Exception fetching on retry=" + tries, e);
                ex = e;
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("Total to fetch {}", elapsed);
            }
            LOGGER.warn("Sleeping for {} seconds before retry", throttleSeconds[tries]);
            try {
                Thread.sleep(throttleSeconds[tries]);
            } catch (InterruptedException e) {
                Thread
                        .currentThread()
                        .interrupt();
            }
        } while (++tries < throttleSeconds.length);
        throw new TikaException("Could not parse " + fetchKey, ex);
    }
}
