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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.s3.S3Fetcher;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.SuppressForbidden;

// jdk.httpserver stands in for STS, the container credentials endpoint and S3
@SuppressForbidden
public class S3CredentialsTest {

    private static final String ISSUED_KEY_ID = "ASIAISSUEDKEY";
    private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/tika";
    private static final String CONTAINER_PATH = "/v1/credentials";
    private static final String CONTAINER_TOKEN = "pod-identity-token";
    private static final List<String> SYS_PROPS = List.of("aws.webIdentityTokenFile", "aws.roleArn",
            "aws.region", "aws.endpointUrlSts", "aws.containerCredentialsFullUri",
            "aws.containerAuthorizationTokenFile", "aws.sharedCredentialsFile", "aws.configFile");

    private HttpServer server;
    private String endpoint;
    private final List<String> stsBodies = new CopyOnWriteArrayList<>();
    private final List<String> containerAuthorizations = new CopyOnWriteArrayList<>();
    private final List<String> s3Authorizations = new CopyOnWriteArrayList<>();

    @BeforeEach
    public void setUp(@TempDir Path tmp) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        // keep the default chain off the developer's own ~/.aws files
        System.setProperty("aws.sharedCredentialsFile", tmp.resolve("no-credentials").toString());
        System.setProperty("aws.configFile", tmp.resolve("no-config").toString());
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
        SYS_PROPS.forEach(System::clearProperty);
    }

    @Test
    public void testProviderPerName() throws Exception {
        assertInstanceOf(InstanceProfileCredentialsProvider.class, build(S3Credentials.INSTANCE));
        assertInstanceOf(WebIdentityTokenFileCredentialsProvider.class, build(S3Credentials.WEB_IDENTITY));
        assertInstanceOf(ProfileCredentialsProvider.class, build(S3Credentials.PROFILE));
        assertInstanceOf(StaticCredentialsProvider.class, build(S3Credentials.KEY_SECRET));
        assertInstanceOf(DefaultCredentialsProvider.class, build(S3Credentials.DEFAULT));
    }

    @Test
    public void testUnknownProvider() {
        TikaConfigException e = assertThrows(TikaConfigException.class, () -> build("irsa"));
        assertTrue(e.getMessage().contains("'default'"), e.getMessage());
        assertThrows(TikaConfigException.class, () -> build(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {S3Credentials.WEB_IDENTITY, S3Credentials.DEFAULT})
    public void testFetcherSignsWithWebIdentityCredentials(String credentialsProvider,
                                                           @TempDir Path tmp) throws Exception {
        if (S3Credentials.DEFAULT.equals(credentialsProvider)) {
            // env credentials come before web identity in the default chain
            assumeTrue(System.getenv("AWS_ACCESS_KEY_ID") == null);
        }
        Path token = tmp.resolve("token");
        Files.writeString(token, "eyJ.test.token", StandardCharsets.UTF_8);
        System.setProperty("aws.webIdentityTokenFile", token.toString());
        System.setProperty("aws.roleArn", ROLE_ARN);
        System.setProperty("aws.region", "us-east-1");
        System.setProperty("aws.endpointUrlSts", endpoint);

        assertFetch(credentialsProvider);
        assertEquals(1, stsBodies.size());
        assertTrue(stsBodies.get(0).contains("Action=AssumeRoleWithWebIdentity"), stsBodies.get(0));
        assertTrue(stsBodies.get(0).contains("WebIdentityToken=eyJ.test.token"), stsBodies.get(0));
    }

    @Test
    public void testDefaultSignsWithContainerCredentials(@TempDir Path tmp) throws Exception {
        // EKS Pod Identity and ECS task roles: an earlier link in the chain would win
        assumeTrue(System.getenv("AWS_ACCESS_KEY_ID") == null);
        assumeTrue(System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE") == null);
        assumeTrue(System.getenv("AWS_PROFILE") == null);
        Path token = tmp.resolve("token");
        Files.writeString(token, CONTAINER_TOKEN, StandardCharsets.UTF_8);
        System.setProperty("aws.containerCredentialsFullUri", endpoint + CONTAINER_PATH);
        System.setProperty("aws.containerAuthorizationTokenFile", token.toString());

        assertFetch(S3Credentials.DEFAULT);
        assertEquals(List.of(CONTAINER_TOKEN), containerAuthorizations);
        assertTrue(stsBodies.isEmpty());
    }

    private void assertFetch(String credentialsProvider) throws Exception {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.put("bucket", "bucket");
        json.put("region", "us-east-1");
        json.put("credentialsProvider", credentialsProvider);
        json.put("endpointConfigurationService", endpoint);
        json.put("pathStyleAccessEnabled", true);
        json.put("spoolToTemp", false);
        S3Fetcher fetcher = S3Fetcher.build(new ExtensionConfig("s3f", "s3-fetcher", json.toString()));

        try (TikaInputStream tis = fetcher.fetch("key", new Metadata(), new ParseContext())) {
            assertEquals("hello", new String(tis.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(1, s3Authorizations.size());
        assertTrue(s3Authorizations.get(0).contains("Credential=" + ISSUED_KEY_ID + "/"),
                s3Authorizations.get(0));
    }

    private static Object build(String name) throws TikaConfigException {
        return S3Credentials.build(name, "my-profile", "access", "secret");
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body;
        if ("POST".equals(exchange.getRequestMethod())) {
            try (InputStream is = exchange.getRequestBody()) {
                stsBodies.add(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            body = ("<AssumeRoleWithWebIdentityResponse xmlns=\"https://sts.amazonaws.com/doc/2011-06-15/\">"
                    + "<AssumeRoleWithWebIdentityResult><Credentials>"
                    + "<AccessKeyId>" + ISSUED_KEY_ID + "</AccessKeyId>"
                    + "<SecretAccessKey>secret</SecretAccessKey>"
                    + "<SessionToken>session</SessionToken>"
                    + "<Expiration>2099-01-01T00:00:00Z</Expiration>"
                    + "</Credentials></AssumeRoleWithWebIdentityResult>"
                    + "</AssumeRoleWithWebIdentityResponse>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml");
        } else if (CONTAINER_PATH.equals(exchange.getRequestURI().getPath())) {
            containerAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            body = ("{\"AccessKeyId\":\"" + ISSUED_KEY_ID + "\",\"SecretAccessKey\":\"secret\","
                    + "\"Token\":\"session\",\"Expiration\":\"2099-01-01T00:00:00Z\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
        } else {
            s3Authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            body = "hello".getBytes(StandardCharsets.UTF_8);
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
