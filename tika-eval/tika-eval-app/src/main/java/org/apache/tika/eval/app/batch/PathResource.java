package org.apache.tika.eval.app.batch;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.metadata.Metadata;

public class PathResource implements FileResource {

    private final Path path;
    private final String resourceId;
    public PathResource(Path path, String resourceId) {
        this.path = path;
        this.resourceId = resourceId;
    }
    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public Metadata getMetadata() {
        return new Metadata();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return Files.newInputStream(path);
    }
}
