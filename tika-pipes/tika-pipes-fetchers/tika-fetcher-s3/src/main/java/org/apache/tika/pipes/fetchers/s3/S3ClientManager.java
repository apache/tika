package org.apache.tika.pipes.fetchers.s3;

import java.net.Socket;
import java.net.URI;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import org.apache.tika.pipes.fetchers.s3.config.S3FetcherConfig;

@Slf4j
public class S3ClientManager {

    private final ThreadLocal<S3Client> s3ClientThreadLocal;
    private final S3FetcherConfig s3FetcherConfig;

    public S3ClientManager(S3FetcherConfig s3FetcherConfig) {
        this.s3FetcherConfig = s3FetcherConfig;
        this.s3ClientThreadLocal = ThreadLocal.withInitial(this::initialize);
    }

    private S3Client initialize() {
        String credentialsProvider = s3FetcherConfig.getCredentialsProvider();
        AwsCredentialsProvider provider;
        if (StringUtils.equals(credentialsProvider, "instance")) {
            provider = InstanceProfileCredentialsProvider.create();
        } else if (StringUtils.equals(credentialsProvider, "identity")) {
            provider = WebIdentityTokenFileCredentialsProvider.create();
        } else if (StringUtils.equals(credentialsProvider, "profile")) {
            provider = ProfileCredentialsProvider.create(s3FetcherConfig.getProfile());
        } else if (StringUtils.equals(credentialsProvider, "key_secret")) {
            if (StringUtils.isNotBlank(s3FetcherConfig.getSessionToken())) {
                provider = StaticCredentialsProvider.create(AwsSessionCredentials.create(s3FetcherConfig.getAccessKey(), s3FetcherConfig.getSecretKey(), s3FetcherConfig.getSessionToken()));
            } else {
                provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(s3FetcherConfig.getAccessKey(), s3FetcherConfig.getSecretKey()));
            }
        } else {
            throw new IllegalArgumentException("credentialsProvider must be set and must be either 'instance', 'identity', 'profile' or 'key_secret'");
        }

        ClientOverrideConfiguration clientConfiguration = ClientOverrideConfiguration.builder().build();
        S3ClientBuilder s3ClientBuilder = S3Client.builder()
                .overrideConfiguration(clientConfiguration)
                .credentialsProvider(provider)
                .region(Region.of(s3FetcherConfig.getRegion()));

        if (!StringUtils.isBlank(s3FetcherConfig.getEndpointOverride())) {
           if (!testEndpointConnectivity()) {
                throw new IllegalArgumentException("Failed to connect to the specified S3 endpoint: " + s3FetcherConfig.getEndpointOverride());
            }
            s3ClientBuilder.endpointOverride(URI.create(s3FetcherConfig.getEndpointOverride()));
        }

        return s3ClientBuilder.build();
    }

    public boolean testEndpointConnectivity() {
        String endpoint = s3FetcherConfig.getEndpointOverride();
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80);
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 3000);
                log.info("Successfully connected to {}:{}", host, port);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to connect to endpoint: {}", endpoint, e);
            return false;
        }
    }

    public S3Client getS3Client() {
        return s3ClientThreadLocal.get();
    }
}
