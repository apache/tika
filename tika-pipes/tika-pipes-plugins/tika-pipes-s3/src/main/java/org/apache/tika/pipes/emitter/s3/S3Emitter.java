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
package org.apache.tika.pipes.emitter.s3;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractStreamEmitter;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.StringUtils;

/**
 * Emitter to write to an existing S3 bucket.
 *
 * <p>Example JSON configuration:</p>
 * <pre>
 * {
 *   "emitters": {
 *     "s3-emitter": {
 *       "my-s3": {
 *         "region": "us-east-1",
 *         "bucket": "my-bucket",
 *         "credentialsProvider": "profile",
 *         "profile": "my-profile",
 *         "prefix": "output",
 *         "fileExtension": "json",
 *         "spoolToTemp": true
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class S3Emitter extends AbstractStreamEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Emitter.class);

    private final S3EmitterConfig config;
    private final S3Client s3Client;

    public static S3Emitter build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        S3EmitterConfig config = S3EmitterConfig.load(extensionConfig.json());
        config.validate();
        S3Client s3Client = buildS3Client(config);
        return new S3Emitter(extensionConfig, config, s3Client);
    }

    private S3Emitter(ExtensionConfig extensionConfig, S3EmitterConfig config, S3Client s3Client) {
        super(extensionConfig);
        this.config = config;
        this.s3Client = s3Client;
    }

    private static S3Client buildS3Client(S3EmitterConfig config) throws TikaConfigException {
        AwsCredentialsProvider provider = buildCredentialsProvider(config);
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .maxConnections(config.maxConnections())
                .build();
        S3Configuration clientConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(config.pathStyleAccessEnabled())
                .build();

        try {
            S3ClientBuilder s3ClientBuilder = S3Client.builder()
                    .httpClient(httpClient)
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .serviceConfiguration(clientConfig)
                    .credentialsProvider(provider);

            if (!StringUtils.isBlank(config.endpointConfigurationService())) {
                try {
                    s3ClientBuilder.endpointOverride(new URI(config.endpointConfigurationService()))
                            .region(Region.of(config.region()));
                } catch (URISyntaxException ex) {
                    throw new TikaConfigException("bad endpointConfigurationService: " +
                            config.endpointConfigurationService(), ex);
                }
            } else {
                s3ClientBuilder.region(Region.of(config.region()));
            }
            return s3ClientBuilder.build();
        } catch (SdkClientException e) {
            throw new TikaConfigException("can't initialize s3 emitter", e);
        }
    }

    private static AwsCredentialsProvider buildCredentialsProvider(S3EmitterConfig config) throws TikaConfigException {
        switch (config.credentialsProvider()) {
            case "instance":
                return InstanceProfileCredentialsProvider.builder().build();
            case "profile":
                return ProfileCredentialsProvider.builder()
                        .profileName(config.profile())
                        .build();
            case "key_secret":
                AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
                        config.accessKey(), config.secretKey());
                return StaticCredentialsProvider.create(awsCreds);
            default:
                throw new TikaConfigException("credentialsProvider must be 'instance', 'profile' or 'key_secret'");
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            throw new IOException("metadata list must not be null or of size 0");
        }

        boolean spoolToTemp = config.spoolToTemp();
        if (spoolToTemp) {
            spoolToTemp = true; // default from config, but could override from parseContext
        }

        if (!spoolToTemp) {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {
                JsonMetadataList.toJson(metadataList, writer);
            }
            byte[] bytes = bos.toByteArray();
            try (InputStream is = TikaInputStream.get(bytes)) {
                emit(emitKey, is, new Metadata(), parseContext);
            }
        } else {
            try (TemporaryResources tmp = new TemporaryResources()) {
                Path tmpPath = tmp.createTempFile();
                try (Writer writer = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE)) {
                    JsonMetadataList.toJson(metadataList, writer);
                }
                try (InputStream is = TikaInputStream.get(tmpPath)) {
                    emit(emitKey, is, new Metadata(), parseContext);
                }
            }
        }
    }

    @Override
    public void emit(String path, InputStream is, Metadata userMetadata, ParseContext parseContext) throws IOException {
        String prefix = config.normalizedPrefix();
        if (!StringUtils.isBlank(prefix)) {
            path = prefix + "/" + path;
        }

        String fileExtension = config.fileExtension();
        if (!StringUtils.isBlank(fileExtension)) {
            path += "." + fileExtension;
        }

        LOGGER.debug("about to emit to target bucket: ({}) path:({})", config.bucket(), path);

        Map<String, String> metadataMap = new HashMap<>();
        for (String n : userMetadata.names()) {
            String[] vals = userMetadata.getValues(n);
            if (vals.length > 1) {
                LOGGER.warn("Can only write the first value for key {}. I see {} values.", n, vals.length);
            }
            metadataMap.put(n, vals[0]);
        }

        // In practice, sending a file is more robust
        // We ran into stream reset issues during digesting, and aws doesn't
        // like putObjects for streams without lengths
        if (is instanceof TikaInputStream) {
            if (((TikaInputStream) is).hasFile()) {
                try {
                    PutObjectRequest request = PutObjectRequest.builder()
                            .bucket(config.bucket())
                            .key(path)
                            .metadata(metadataMap)
                            .build();
                    RequestBody requestBody = RequestBody.fromFile(((TikaInputStream) is).getFile());
                    s3Client.putObject(request, requestBody);
                } catch (IOException e) {
                    throw new IOException("exception sending underlying file", e);
                }
                return;
            }
        }
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(path)
                    .metadata(metadataMap)
                    .build();
            RequestBody requestBody = RequestBody.fromBytes(is.readAllBytes());
            s3Client.putObject(request, requestBody);
        } catch (S3Exception e) {
            throw new IOException("problem writing s3object", e);
        }
    }
}
