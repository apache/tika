package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/// <summary>
/// This class specifies the base class for file chunking.
/// </summary>
public abstract class AbstractChunking {
    /// <summary>
    /// Initializes a new instance of the AbstractChunking class.
    /// </summary>
    /// <param name="fileContent">The content of the file.</param>
    protected AbstractChunking(byte[] fileContent) {
        this.FileContent = fileContent;
    }

    /// <summary>
    /// Gets or sets the file content.
    /// </summary>
    protected byte[] FileContent;

    /// <summary>
    /// This method is used to chunk the file data.
    /// </summary>
    /// <returns>A list of LeafNodeObjectData.</returns>
    public abstract List<LeafNodeObject> Chunking();
}