package org.apache.tika.parser.microsoft.fsshttpb.streamobj.chunking;

public enum ChunkingMethod {
    /**
     * File data is passed to the Zip algorithm chunking method.
     */
    ZipAlgorithm,

    /**
     * File data is passed to the RDC Analysis chunking method.
     */
    RDCAnalysis,

    /**
     * File data is passed to the Simple algorithm chunking method.
     */
    SimpleAlgorithm
}