package org.apache.tika.emitter.fs;

import org.apache.tika.config.Field;
import org.apache.tika.emitter.Emitter;
import org.apache.tika.emitter.TikaEmitterException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FileSystemEmitter implements Emitter {

    private String name = "fs";
    private Path basePath = null;
    private String fileExtension = "json";

    @Override
    public Set<String> getSupported() {
        return Collections.singleton(name);
    }

    @Override
    public void emit(String emitterName, List<Metadata> metadataList) throws IOException, TikaException {
        Path output;
        if (metadataList == null || metadataList.size() == 0) {
            throw new TikaEmitterException("metadata list must not be null or of size 0");
        }

        String relPath = metadataList.get(0)
                .get(TikaCoreProperties.SOURCE_PATH);
        if (relPath == null) {
            throw new TikaEmitterException("Must specify a "+TikaCoreProperties.SOURCE_PATH.getName() +
                    " in the metadata in order for this emitter to generate the output file path.");
        }
        if (fileExtension != null && fileExtension.length() > 0) {
            relPath += "." + fileExtension;
        }
        if (basePath != null) {
            output = basePath.resolve(relPath);
        } else {
            output = Paths.get(relPath);
        }

        if (!Files.isDirectory(output.getParent())) {
            Files.createDirectories(output.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            JsonMetadataList.toJson(metadataList, writer);
        }
    }

    @Field
    public void setBasePath(String basePath) {
        this.basePath = Paths.get(basePath);
    }

    /**
     * If you want to customize the output file's file extension.
     * Do not include the "."
     * @param fileExtension
     */
    @Field
    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    /**
     * Set this so to uniquely identify this emitter if
     * there might be others available. The default is "fs"
     * @param name
     */
    @Field
    public void setName(String name) {
        this.name = name;
    }
}
