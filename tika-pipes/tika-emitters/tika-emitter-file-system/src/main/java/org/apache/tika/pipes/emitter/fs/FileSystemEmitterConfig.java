package org.apache.tika.pipes.emitter.fs;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

enum ON_EXISTS {
    SKIP, EXCEPTION, REPLACE
}

public record FileSystemEmitterConfig(String basePath, String fileExtension, ON_EXISTS onExists, boolean prettyPrint) {


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static FileSystemEmitterConfig load(String json) throws IOException {
        return OBJECT_MAPPER.readValue(json, FileSystemEmitterConfig.class);
    }

}
