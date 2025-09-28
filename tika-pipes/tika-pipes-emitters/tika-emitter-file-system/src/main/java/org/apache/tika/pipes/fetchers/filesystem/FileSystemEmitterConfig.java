package org.apache.tika.pipes.fetchers.filesystem;

import org.pf4j.Extension;

import org.apache.tika.pipes.core.emitter.DefaultEmitterConfig;

@Extension
public class FileSystemEmitterConfig extends DefaultEmitterConfig {
    private String outputDir;
    private String addFileExtension = "json";
    private String onExists = "exception";
    private boolean prettyPrint = false;

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getAddFileExtension() {
        return addFileExtension;
    }

    public void setAddFileExtension(String addFileExtension) {
        this.addFileExtension = addFileExtension;
    }

    public String getOnExists() {
        return onExists;
    }

    public void setOnExists(String onExists) {
        this.onExists = onExists;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}
