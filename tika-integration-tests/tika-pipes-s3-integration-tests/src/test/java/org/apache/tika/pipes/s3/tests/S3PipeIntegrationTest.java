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
package org.apache.tika.pipes.s3.tests;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.hamcrest.MatcherAssert;
import org.testcontainers.shaded.org.hamcrest.Matchers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import org.apache.tika.cli.TikaCLI;
import org.apache.tika.pipes.HandlerConfig;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
class S3PipeIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(S3PipeIntegrationTest.class);

    public static final int MAX_STARTUP_TIMEOUT = 120;
    private static final DockerComposeContainer<?> minioContainer = new DockerComposeContainer<>(
            new File("src/test/resources/docker-compose.yml")).withStartupTimeout(
                    Duration.of(MAX_STARTUP_TIMEOUT, ChronoUnit.SECONDS))
            .withExposedService("minio-service", 9000);
    private static final String MINIO_ENDPOINT = "http://localhost:9000";
    private static final String ACCESS_KEY = "minio";
    private static final String SECRET_KEY = "minio123";
    private static final String FETCH_BUCKET = "fetch-bucket";
    private static final String EMIT_BUCKET = "emit-bucket";

    private static final Region REGION = Region.US_EAST_1;

    private S3Client s3Client;

    private final File testFileFolder = new File("target", "test-files");
    private final Set<String> testFiles = new HashSet<>();

    private void createTestFiles() throws NoSuchAlgorithmException {
        if (testFileFolder.mkdirs()) {
            LOG.info("Created test folder: {}", testFileFolder);
        }
        int numDocs = 42;
        for (int i = 0; i < numDocs; ++i) {
            String nextFileName = "test-" + i + ".html";
            testFiles.add(nextFileName);
            String s = "<html><body>body-of-" + nextFileName + "</body></html>";
            byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
            MessageDigest md = MessageDigest.getInstance("SHA256");
            PutObjectRequest request = PutObjectRequest.builder().bucket(FETCH_BUCKET).key(nextFileName).build();
            RequestBody requestBody = RequestBody.fromBytes(bytes);
            s3Client.putObject(request, requestBody);
        }
    }

    @BeforeAll
    void setupMinio() throws URISyntaxException {
        minioContainer.start();
        initializeS3Client();
    }

    @AfterAll
    void closeMinio() {
        minioContainer.close();
    }

    private void initializeS3Client() throws URISyntaxException {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY);
        // https://github.com/aws/aws-sdk-java-v2/discussions/3536
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(awsCreds);
        S3Configuration s3c = S3Configuration.builder().pathStyleAccessEnabled(true).build(); // SO11228792
        s3Client = S3Client.builder().
                requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED). // https://stackoverflow.com/a/79488850/535646
                serviceConfiguration(s3c).region(REGION).
                credentialsProvider(credentialsProvider).endpointOverride(new URI(MINIO_ENDPOINT)).build();
    }

    @Test
    void s3PipelineIteratorS3FetcherAndS3Emitter() throws Exception {

        // create s3 bucket for fetches and for emits
        s3Client.createBucket(CreateBucketRequest.builder().bucket(FETCH_BUCKET).build());
        s3Client.createBucket(CreateBucketRequest.builder().bucket(EMIT_BUCKET).build());

        // create some test files and insert into fetch bucket
        createTestFiles();

        // Let's fetch it
        File tikaConfigFile = new File("target", "ta.xml");
        File log4jPropFile = new File("target", "tmp-log4j2.xml");
        try (InputStream is = this.getClass()
                .getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            Assertions.assertNotNull(is);
            FileUtils.copyInputStreamToFile(is, log4jPropFile);
        }
        String tikaConfigTemplateXml;
        try (InputStream is = this.getClass()
                .getResourceAsStream("/tika-config-s3-integration-test.xml")) {
            assert is != null;
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }
        try {
            String tikaConfigXml =
                    createTikaConfigXml(tikaConfigFile, log4jPropFile, tikaConfigTemplateXml);

            FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);
            TikaCLI.main(new String[]{"-a", "--config=" + tikaConfigFile.getAbsolutePath()});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (String testFile : testFiles) {
            GetObjectRequest objectRequest = GetObjectRequest.builder().bucket(EMIT_BUCKET).key(testFile + ".json").build();
            ResponseInputStream<GetObjectResponse> object = s3Client.getObject(objectRequest);
            Assertions.assertNotNull(object);
            String data = IOUtils.toString(object, StandardCharsets.UTF_8);
            MatcherAssert.assertThat(
                    "Should be able to read the parsed body of the HTML file as the body of the document",
                    data, Matchers.containsString("body-of-" + testFile));
        }
    }

    @NotNull
    private String createTikaConfigXml(File tikaConfigFile, File log4jPropFile,
                                       String tikaConfigTemplateXml) {
        return tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.getAbsolutePath())
                .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.getAbsolutePath())
                .replace("{PATH_TO_DOCS}", testFileFolder.getAbsolutePath())
                .replace("{PARSE_MODE}", HandlerConfig.PARSE_MODE.RMETA.name())
                .replace("{PIPE_ITERATOR_BUCKET}", FETCH_BUCKET)
                .replace("{EMIT_BUCKET}", EMIT_BUCKET).replace("{FETCH_BUCKET}", FETCH_BUCKET)
                .replace("{ACCESS_KEY}", ACCESS_KEY).replace("{SECRET_KEY}", SECRET_KEY)
                .replace("{ENDPOINT_CONFIGURATION_SERVICE}", MINIO_ENDPOINT)
                .replace("{REGION}", REGION.id());
    }
}
