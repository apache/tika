package org.apache.tika.pipes.fetcher.s3.config;

import org.apache.tika.pipes.fetcher.config.AbstractConfig;

public class S3FetcherConfig extends AbstractConfig {
    private boolean spoolToTemp;
    private String region;
    private String profile;
    private String bucket;
    private String commaDelimitedLongs;
    private String prefix;
    private boolean extractUserMetadata;
    private int maxConnections;
    private String credentialsProvider;
    private long maxLength;
    private String accessKey;
    private String secretKey;
    private String endpointConfigurationService;
    private boolean pathStyleAccessEnabled;
    private long[] throttleSeconds;

    public boolean isSpoolToTemp() {
        return spoolToTemp;
    }

    public S3FetcherConfig setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
        return this;
    }

    public String getRegion() {
        return region;
    }

    public S3FetcherConfig setRegion(String region) {
        this.region = region;
        return this;
    }

    public String getProfile() {
        return profile;
    }

    public S3FetcherConfig setProfile(String profile) {
        this.profile = profile;
        return this;
    }

    public String getBucket() {
        return bucket;
    }

    public S3FetcherConfig setBucket(String bucket) {
        this.bucket = bucket;
        return this;
    }

    public String getCommaDelimitedLongs() {
        return commaDelimitedLongs;
    }

    public S3FetcherConfig setCommaDelimitedLongs(String commaDelimitedLongs) {
        this.commaDelimitedLongs = commaDelimitedLongs;
        return this;
    }

    public String getPrefix() {
        return prefix;
    }

    public S3FetcherConfig setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public boolean isExtractUserMetadata() {
        return extractUserMetadata;
    }

    public S3FetcherConfig setExtractUserMetadata(boolean extractUserMetadata) {
        this.extractUserMetadata = extractUserMetadata;
        return this;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public S3FetcherConfig setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public String getCredentialsProvider() {
        return credentialsProvider;
    }

    public S3FetcherConfig setCredentialsProvider(String credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    public long getMaxLength() {
        return maxLength;
    }

    public S3FetcherConfig setMaxLength(long maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public S3FetcherConfig setAccessKey(String accessKey) {
        this.accessKey = accessKey;
        return this;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public S3FetcherConfig setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        return this;
    }

    public String getEndpointConfigurationService() {
        return endpointConfigurationService;
    }

    public S3FetcherConfig setEndpointConfigurationService(String endpointConfigurationService) {
        this.endpointConfigurationService = endpointConfigurationService;
        return this;
    }

    public boolean isPathStyleAccessEnabled() {
        return pathStyleAccessEnabled;
    }

    public S3FetcherConfig setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
        this.pathStyleAccessEnabled = pathStyleAccessEnabled;
        return this;
    }

    public long[] getThrottleSeconds() {
        return throttleSeconds;
    }

    public S3FetcherConfig setThrottleSeconds(long[] throttleSeconds) {
        this.throttleSeconds = throttleSeconds;
        return this;
    }
}
