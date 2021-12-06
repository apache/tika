package org.apache.tika.parser.microsoft.fsshttpb.streamobj.chunking;

import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.IntermediateNodeObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.LeafNodeObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ZipHeader;
import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;

/**
 * This class is used to create instance of AbstractChunking.
 */
public class ChunkingFactory {
    /**
     * Prevents a default instance of the ChunkingFactory class from being created
     */
    private ChunkingFactory() {
    }

    /**
     * This method is used to create the instance of AbstractChunking.
     *
     * @param fileContent The content of the file.
     * @return The instance of AbstractChunking.
     */
    public static AbstractChunking CreateChunkingInstance(byte[] fileContent) {
        if (ZipHeader.IsFileHeader(fileContent, 0)) {
            return new ZipFilesChunking(fileContent);
        } else {
            return new RDCAnalysisChunking(fileContent);
        }
    }

    /**
     * This method is used to create the instance of AbstractChunking.
     *
     * @param nodeObject Specify the root node object.
     * @return The instance of AbstractChunking.
     */

    public static AbstractChunking CreateChunkingInstance(IntermediateNodeObject nodeObject) {
        byte[] fileContent = ByteUtil.toByteArray(nodeObject.GetContent());

//        if (EditorsTableUtils.IsEditorsTableHeader(fileContent))
//        {
//            return null;
//        }

        if (ZipHeader.IsFileHeader(fileContent, 0)) {
            return new ZipFilesChunking(fileContent);
        } else {
            // For SharePoint Server 2013 compatible SUTs, always using the RDC Chunking method in the current test suite involved file resources.
//            if (SharedContext.Current.CellStorageVersionType.MinorVersion >= 2)
//            {
//                return new RDCAnalysisChunking(fileContent);
//            }

            // For SharePoint Server 2010 SP2 compatible SUTs, chunking method depends on file content and size. So first try using the simple chunking.
            AbstractChunking returnChunking = new SimpleChunking(fileContent);

            List<LeafNodeObject> nodes = returnChunking.Chunking();
            if (nodeObject.IntermediateNodeObjectList.size() == nodes.size()) {
                boolean isDataSizeMatching = true;
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodeObject.IntermediateNodeObjectList.get(i).DataSize.DataSize !=
                            nodes.get(i).DataSize.DataSize) {
                        isDataSizeMatching = false;
                        break;
                    }
                }

                if (isDataSizeMatching) {
                    return returnChunking;
                }
            }

            // If the intermediate count number or data size does not equals, then try to use RDC chunking method.
            return new RDCAnalysisChunking(fileContent);
        }
    }

    /**
     * This method is used to create the instance of AbstractChunking.
     *
     * @param fileContent    The content of the file.
     * @param chunkingMethod The type of chunking methods.
     * @return The instance of AbstractChunking.
     */
    public static AbstractChunking CreateChunkingInstance(byte[] fileContent, ChunkingMethod chunkingMethod) {
        AbstractChunking chunking;
        switch (chunkingMethod) {
            case RDCAnalysis:
                chunking = new RDCAnalysisChunking(fileContent);
                break;
            case SimpleAlgorithm:
                chunking = new SimpleChunking(fileContent);
                break;
            case ZipAlgorithm:
                chunking = new ZipFilesChunking(fileContent);
                break;

            default:
                throw new InvalidOperationException("Cannot support the chunking type" + chunkingMethod);
        }

        return chunking;
    }
}