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
package org.apache.tika.pipes.fetchers.googledrive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.pf4j.Extension;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.pipes.fetchers.core.Fetcher;
import org.apache.tika.pipes.fetchers.core.FetcherConfig;
import org.apache.tika.pipes.fetchers.googledrive.config.GoogleDriveFetcherConfig;

@Extension
@Slf4j
public class GoogleDriveFetcher implements Fetcher {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    @Override
    public InputStream fetch(FetcherConfig fetcherConfig, String fetchKey, Map<String, Object> fetchMetadata, Map<String, Object> responseMetadata) {
        GoogleDriveFetcherConfig googleDriveFetcherConfig = (GoogleDriveFetcherConfig) fetcherConfig;
        int tries = 0;
        Exception ex;
        TemporaryResources tmp;
        List<Long> throttleSeconds = googleDriveFetcherConfig.getThrottleSeconds();
        do {
            long start = System.currentTimeMillis();
            try {
                String[] fetchKeySplit = fetchKey.split(",");
                if (fetchKeySplit.length != 2) {
                    throw new TikaException("Invalid fetch key, expected format ${fileId},${subjectUser}: " + fetchKey);
                }

                String fileId = fetchKeySplit[0];
                String subjectUser = fetchKeySplit[1];

                Drive driveService;
                List<String> scopes = googleDriveFetcherConfig.getScopes();
                if (scopes == null || scopes.isEmpty()) {
                    scopes = List.of(DriveScopes.DRIVE_READONLY);
                }
                try {
                    GoogleCredentials baseCredentials = GoogleCredentials
                            .fromStream(new ByteArrayInputStream(Base64
                                    .getDecoder().decode(googleDriveFetcherConfig.getServiceAccountKeyBase64())))
                            .createScoped(scopes);
                    GoogleCredentials delegatedCredentials = baseCredentials.createDelegated(subjectUser);
                    final HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(delegatedCredentials);
                    driveService = new Drive.Builder(
                            new com.google.api.client.http.javanet.NetHttpTransport(),
                            JSON_FACTORY,
                            requestInitializer)
                            .setApplicationName(googleDriveFetcherConfig.getApplicationName())
                            .build();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                Drive.Files.Get get = driveService.files().get(fileId);
                InputStream is = get
                                             .executeMediaAsInputStream();

                if (is == null) {
                    throw new IOException("Empty input stream when we tried to parse " + fetchKey);
                }

                if (googleDriveFetcherConfig.isSpoolToTemp()) {
                    tmp = new TemporaryResources();
                    Path tmpPath = tmp.createTempFile(fileId + ".dat");
                    FileUtils.copyInputStreamToFile(is, tmpPath.toFile());
                    return TikaInputStream.get(tmpPath);
                }
                return TikaInputStream.get(is);

            } catch (Exception e) {
                log.warn("Exception fetching on retry=" + tries, e);
                ex = e;
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                log.debug("Total to fetch {}", elapsed);
            }

            log.warn("Sleeping for {} seconds before retry", throttleSeconds.get(tries));
            try {
                Thread.sleep(throttleSeconds.get(tries) * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } while (++tries < throttleSeconds.size());
        throw new RuntimeException("Could not fetch " + fetchKey, ex);
    }
}
