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
package org.apache.tika.pipes.fetcher.googledrive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.googledrive.config.GoogleDriveFetcherConfig;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;

public class GoogleDriveFetcher extends AbstractTikaExtension implements Fetcher {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveFetcher.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public static GoogleDriveFetcher build(ExtensionConfig pluginConfig)
            throws TikaConfigException, IOException {
        GoogleDriveFetcherConfig config =
                GoogleDriveFetcherConfig.load(pluginConfig.json());
        GoogleDriveFetcher fetcher = new GoogleDriveFetcher(pluginConfig, config);
        fetcher.initialize();
        return fetcher;
    }

    private GoogleDriveFetcherConfig config;

    public GoogleDriveFetcher(ExtensionConfig pluginConfig, GoogleDriveFetcherConfig config) {
        super(pluginConfig);
        this.config = config;
    }

    public void initialize() throws IOException, TikaConfigException {
        // Initialization if needed
    }

    @Override
    public TikaInputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext)
            throws IOException, TikaException {
        int tries = 0;
        Exception ex = null;
        List<Long> throttleSeconds = config.getThrottleSeconds();
        int maxTries = (throttleSeconds != null && !throttleSeconds.isEmpty()) ?
                throttleSeconds.size() : 1;

        do {
            long start = System.currentTimeMillis();
            try {
                String[] fetchKeySplit = fetchKey.split(",");
                if (fetchKeySplit.length != 2) {
                    throw new TikaException(
                            "Invalid fetch key, expected format ${fileId},${subjectUser}: " +
                                    fetchKey);
                }

                String fileId = fetchKeySplit[0];
                String subjectUser = fetchKeySplit[1];

                Drive driveService = createDriveService(subjectUser);
                Drive.Files.Get get = driveService.files().get(fileId);
                InputStream is = get.executeMediaAsInputStream();

                if (is == null) {
                    throw new IOException(
                            "Empty input stream when we tried to parse " + fetchKey);
                }

                if (config.isSpoolToTemp()) {
                    TemporaryResources tmp = new TemporaryResources();
                    Path tmpPath = tmp.createTempFile(metadata);
                    FileUtils.copyInputStreamToFile(is, tmpPath.toFile());
                    return TikaInputStream.get(tmpPath);
                }
                return TikaInputStream.get(is);

            } catch (Exception e) {
                LOG.warn("Exception fetching on retry=" + tries, e);
                ex = e;
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                LOG.debug("Total to fetch {}", elapsed);
            }

            if (throttleSeconds != null && tries < throttleSeconds.size()) {
                LOG.warn("Sleeping for {} seconds before retry", throttleSeconds.get(tries));
                try {
                    Thread.sleep(throttleSeconds.get(tries) * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } while (++tries < maxTries);

        if (ex instanceof TikaException) {
            throw (TikaException) ex;
        } else if (ex instanceof IOException) {
            throw (IOException) ex;
        }
        throw new TikaException("Could not fetch " + fetchKey, ex);
    }

    private Drive createDriveService(String subjectUser) throws IOException {
        List<String> scopes = config.getScopes();
        if (scopes == null || scopes.isEmpty()) {
            scopes = List.of(DriveScopes.DRIVE_READONLY);
        }

        GoogleCredentials baseCredentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(
                        Base64.getDecoder().decode(config.getServiceAccountKeyBase64())))
                .createScoped(scopes);

        GoogleCredentials delegatedCredentials = baseCredentials.createDelegated(subjectUser);
        final HttpRequestInitializer requestInitializer =
                new HttpCredentialsAdapter(delegatedCredentials);

        return new Drive.Builder(new NetHttpTransport(), JSON_FACTORY, requestInitializer)
                .setApplicationName(config.getApplicationName())
                .build();
    }
}
