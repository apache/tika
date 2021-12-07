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

package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.chunking.ChunkingFactory;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.SequenceNumberGenerator;

public class IntermediateNodeObject extends NodeObject {
    /**
     * Initializes a new instance of the IntermediateNodeObject class.
     */
    public IntermediateNodeObject() {
        super(StreamObjectTypeHeaderStart.IntermediateNodeObject);
        this.intermediateNodeObjectList = new ArrayList<>();
    }

    /**
     * Get all the content which is represented by the root node object.
     *
     * @return Return the byte list of root node object content.
     */
    @Override
    public List<Byte> getContent() throws TikaException {
        List<Byte> content = new ArrayList<>();

        for (LeafNodeObject intermediateNode : this.intermediateNodeObjectList) {
            content.addAll(intermediateNode.getContent());
        }

        return content;
    }

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A Byte array
     * @param currentIndex  Start position
     * @param lengthOfItems The length of the items
     */
    @Override
    protected void deserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                 int lengthOfItems)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        if (lengthOfItems != 0) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "IntermediateNodeObject",
                    "Stream Object over-parse error", null);
        }

        this.signature = StreamObject.getCurrent(byteArray, index, SignatureObject.class);
        this.dataSize = StreamObject.getCurrent(byteArray, index, DataSizeObject.class);

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The Byte list
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) throws TikaException, IOException {
        byteList.addAll(this.signature.serializeToByteList());
        byteList.addAll(this.dataSize.serializeToByteList());
        return 0;
    }

    /**
     * The class is used to build a root node object.
     */
    public static class RootNodeObjectBuilder {
        /**
         * This method is used to build a root node object from a byte array
         *
         * @param fileContent Specify the byte array.
         * @return Return a root node object build from the byte array.
         */
        public IntermediateNodeObject Build(byte[] fileContent) throws TikaException, IOException {
            IntermediateNodeObject rootNode = new IntermediateNodeObject();
            rootNode.signature = new SignatureObject();
            rootNode.dataSize = new DataSizeObject();
            rootNode.dataSize.dataSize = fileContent.length;
            rootNode.exGuid =
                    new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());
            rootNode.intermediateNodeObjectList =
                    ChunkingFactory.createChunkingInstance(fileContent).chunking();
            return rootNode;
        }
    }
}
