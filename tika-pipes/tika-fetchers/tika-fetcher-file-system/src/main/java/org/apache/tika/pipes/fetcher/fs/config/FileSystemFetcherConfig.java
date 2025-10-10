package org.apache.tika.pipes.fetcher.fs.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FileSystemFetcherConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FileSystemFetcherConfig load(InputStream is) throws IOException  {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return OBJECT_MAPPER.readValue(reader, FileSystemFetcherConfig.class);
        }
    }

    private String basePath;
    private boolean extractFileSystemMetadata = false;

    public String getBasePath() {
        return basePath;
    }

    public boolean isExtractFileSystemMetadata() {
        return extractFileSystemMetadata;
    }
}
