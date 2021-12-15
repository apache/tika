/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.chunking;

import java.io.IOException;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.IntermediateNodeObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.LeafNodeObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ZipHeader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

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
    public static AbstractChunking createChunkingInstance(byte[] fileContent) {
        if (ZipHeader.isFileHeader(fileContent, 0)) {
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

    public static AbstractChunking createChunkingInstance(IntermediateNodeObject nodeObject)
            throws TikaException, IOException {
        byte[] fileContent = ByteUtil.toByteArray(nodeObject.getContent());
        if (ZipHeader.isFileHeader(fileContent, 0)) {
            return new ZipFilesChunking(fileContent);
        } else {
            // For SharePoint Server 2013 compatible SUTs, always using the RDC Chunking method in
            // the current test suite involved file resources.
            AbstractChunking returnChunking = new SimpleChunking(fileContent);

            List<LeafNodeObject> nodes = returnChunking.chunking();
            if (nodeObject.intermediateNodeObjectList.size() == nodes.size()) {
                boolean isDataSizeMatching = true;
                for (int i = 0; i < nodes.size(); i++) {
                    if (nodeObject.intermediateNodeObjectList.get(i).dataSize.dataSize !=
                            nodes.get(i).dataSize.dataSize) {
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
    public static AbstractChunking createChunkingInstance(byte[] fileContent,
                                                          ChunkingMethod chunkingMethod) {
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
                throw new InvalidOperationException(
                        "Cannot support the chunking type" + chunkingMethod);
        }

        return chunking;
    }
}
