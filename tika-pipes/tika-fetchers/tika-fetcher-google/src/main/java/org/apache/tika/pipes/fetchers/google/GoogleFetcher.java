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
package org.apache.tika.pipes.fetchers.google;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
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
import org.apache.tika.pipes.fetchers.google.config.GoogleFetcherConfig;


/**
 * Google Fetcher allows the fetching of files from a Google Drive, using a
 * service account key.
 *
 * Fetch Keys are ${fileId},${subjectUser}, where the subject user is the
 * organizer of the file. This user is necessary as part of the key as the
 * service account must act on behalf of the user when querying for the file.
 */
public class GoogleFetcher extends AbstractFetcher implements Initializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleFetcher.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private GoogleCredentials baseCredentials;

    private Drive driveService;
    private boolean spoolToTemp;
    private List<String> scopes;

    private GoogleFetcherConfig config = new GoogleFetcherConfig();

    public GoogleFetcher() {
        scopes = new ArrayList<>();
        scopes.add(DriveScopes.DRIVE_READONLY);
    }

    public GoogleFetcher(GoogleFetcherConfig config) {
        this.config = config;
    }

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
        config.setThrottleSeconds(throttleSeconds);
    }

    @Field
    public void setSpoolToTemp(boolean spoolToTemp) {
        config.setSpoolToTemp(spoolToTemp);
    }

    @Field
    public void setServiceAccountKeyBase64(String serviceAccountKeyBase64) {
        config.setServiceAccountKeyBase64(serviceAccountKeyBase64);
    }

    @Field
    public void setSubjectUser(String subjectUser) {
        config.setSubjectUser(subjectUser);
    }

    @Field
    public void setScopes(List<String> scopes) {
        config.setScopes(new ArrayList<>(scopes));
        if (config.getScopes().isEmpty()) {
            config.getScopes().add(DriveScopes.DRIVE_READONLY);
        }
    }

    @Override
    public void initialize(Map<String, Param> map) throws TikaConfigException {
        try {
            baseCredentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(config.getServiceAccountKeyBase64())))
                    .createScoped(scopes);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to initialize Google Drive service", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler initializableProblemHandler) throws TikaConfigException {
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) throws TikaException, IOException {
        int tries = 0;
        Exception ex = null;

        do {
            long start = System.currentTimeMillis();
            try {
                String[] fetchKeySplit = fetchKey.split(",");
                if (fetchKeySplit.length != 2) {
                    throw new TikaException("Invalid fetch key, expected format ${fileId},${subjectUser}: " + fetchKey);
                }

                String fileId = fetchKeySplit[0];
                String subjectUser = fetchKeySplit[1];

                GoogleCredentials delegatedCredentials = baseCredentials.createDelegated(subjectUser);
                final HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(delegatedCredentials);

                driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), 
                    JSON_FACTORY, 
                    requestInitializer).setApplicationName("tika-fetcher-google").build();

                InputStream is = driveService.files()
                        .get(fileId)
                        .executeMediaAsInputStream();

                if (is == null) {
                    throw new IOException("Empty input stream when we tried to parse " + fetchKey);
                }

                if (spoolToTemp) {
                    File tempFile = Files.createTempFile("spooled-temp", ".dat").toFile();
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

            long[] throttleSeconds = config.getThrottleSeconds();

            LOGGER.warn("Sleeping for {} seconds before retry", throttleSeconds[tries]);
            try {
                Thread.sleep(throttleSeconds[tries] * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } while (++tries < config.getThrottleSeconds().length);

        throw new TikaException("Could not fetch " + fetchKey, ex);
    }
}
