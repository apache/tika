package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/**
 * This class specifies the base class for file chunking
 */
public abstract class AbstractChunking {
    /**
     * Initializes a new instance of the AbstractChunking class.
     *
     * @param fileContent The content of the file.
     */
    protected AbstractChunking(byte[] fileContent) {
        this.FileContent = fileContent;
    }

    /**
     * Gets or sets the file content.
     */
    protected byte[] FileContent;

    /**
     * This method is used to chunk the file data.
     *
     * @return A list of LeafNodeObjectData.
     */
    public abstract List<LeafNodeObject> Chunking();
}