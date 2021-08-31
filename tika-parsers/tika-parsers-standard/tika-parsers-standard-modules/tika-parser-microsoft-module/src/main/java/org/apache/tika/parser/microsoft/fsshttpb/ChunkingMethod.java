package org.apache.tika.parser.microsoft.fsshttpb;

public enum ChunkingMethod {
    /// <summary>
    /// File data is passed to the Zip algorithm chunking method.
    /// </summary>
    ZipAlgorithm,

    /// <summary>
    /// File data is passed to the RDC Analysis chunking method.
    /// </summary>
    RDCAnalysis,

    /// <summary>
    /// File data is passed to the Simple algorithm chunking method.
    /// </summary>
    SimpleAlgorithm
}