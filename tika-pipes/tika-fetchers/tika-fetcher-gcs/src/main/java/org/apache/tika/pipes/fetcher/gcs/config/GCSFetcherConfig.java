package org.apache.tika.pipes.fetcher.gcs.config;

import org.apache.tika.pipes.fetcher.config.AbstractConfig;

public class GCSFetcherConfig extends AbstractConfig {
    private boolean spoolToTemp;
    private String projectId;
    private String bucket;
    private boolean extractUserMetadata;

    public boolean isSpoolToTemp() {
        return spoolToTemp;
    }

    public GCSFetcherConfig setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
        return this;
    }

    public String getProjectId() {
        return projectId;
    }

    public GCSFetcherConfig setProjectId(String projectId) {
        this.projectId = projectId;
        return this;
    }

    public String getBucket() {
        return bucket;
    }

    public GCSFetcherConfig setBucket(String bucket) {
        this.bucket = bucket;
        return this;
    }

    public boolean isExtractUserMetadata() {
        return extractUserMetadata;
    }

    public GCSFetcherConfig setExtractUserMetadata(boolean extractUserMetadata) {
        this.extractUserMetadata = extractUserMetadata;
        return this;
    }
}
