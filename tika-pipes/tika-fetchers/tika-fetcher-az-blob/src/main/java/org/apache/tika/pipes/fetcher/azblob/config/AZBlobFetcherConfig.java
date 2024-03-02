package org.apache.tika.pipes.fetcher.azblob.config;

import org.apache.tika.pipes.fetcher.config.AbstractConfig;

public class AZBlobFetcherConfig extends AbstractConfig {
    private boolean spoolToTemp;
    private String sasToken;
    private String endpoint;
    private String container;
    private boolean extractUserMetadata;

    public boolean isSpoolToTemp() {
        return spoolToTemp;
    }

    public AZBlobFetcherConfig setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
        return this;
    }

    public String getSasToken() {
        return sasToken;
    }

    public AZBlobFetcherConfig setSasToken(String sasToken) {
        this.sasToken = sasToken;
        return this;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public AZBlobFetcherConfig setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public String getContainer() {
        return container;
    }

    public AZBlobFetcherConfig setContainer(String container) {
        this.container = container;
        return this;
    }

    public boolean isExtractUserMetadata() {
        return extractUserMetadata;
    }

    public AZBlobFetcherConfig setExtractUserMetadata(boolean extractUserMetadata) {
        this.extractUserMetadata = extractUserMetadata;
        return this;
    }
}
