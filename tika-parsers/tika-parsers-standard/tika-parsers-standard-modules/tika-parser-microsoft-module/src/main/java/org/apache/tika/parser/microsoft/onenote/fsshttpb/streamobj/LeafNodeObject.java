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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataNodeObjectData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.SequenceNumberGenerator;

public class LeafNodeObject extends NodeObject {
    public DataNodeObjectData dataNodeObjectData;
    public DataHashObject dataHash;

    /**
     * Initializes a new instance of the LeafNodeObjectData class.
     */
    public LeafNodeObject() {
        super(StreamObjectTypeHeaderStart.LeafNodeObject);
    }

    /**
     * Get all the content which is represented by the intermediate node object.
     *
     * @return Return the byte list of intermediate node object content.
     */
    @Override
    public List<Byte> getContent() throws TikaException {
        List<Byte> content = new ArrayList<Byte>();

        if (this.dataNodeObjectData != null) {
            ByteUtil.appendByteArrayToListOfByte(content, this.dataNodeObjectData.objectData);
        } else if (this.intermediateNodeObjectList != null) {
            for (LeafNodeObject intermediateNode : this.intermediateNodeObjectList) {
                content.addAll(intermediateNode.getContent());
            }
        } else {
            throw new TikaException(
                    "The DataNodeObjectData and IntermediateNodeObjectList properties in " +
                            "LeafNodeObjectData cannot be null at the same time.");
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
            throw new StreamObjectParseErrorException(currentIndex.get(), "LeafNodeObjectData",
                    "Stream Object over-parse error", null);
        }

        this.signature = StreamObject.getCurrent(byteArray, index, SignatureObject.class);
        this.dataSize = StreamObject.getCurrent(byteArray, index, DataSizeObject.class);

        // Try to read StreamObjectHeaderStart to see there is data hash object or not
        AtomicReference<StreamObjectHeaderStart> streamObjectHeader = new AtomicReference<>();
        if ((StreamObjectHeaderStart.tryParse(byteArray, index.get(), streamObjectHeader)) != 0) {
            if (streamObjectHeader.get().type == StreamObjectTypeHeaderStart.DataHashObject) {
                this.dataHash = StreamObject.getCurrent(byteArray, index, DataHashObject.class);
            }
        }

        currentIndex.set(index.get());
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return A constant value
     */
    @Override
    protected int serializeItemsToByteList(List<Byte> byteList) throws TikaException, IOException {
        byteList.addAll(this.signature.serializeToByteList());
        byteList.addAll(this.dataSize.serializeToByteList());
        return 0;
    }

    /**
     * The class is used to build a intermediate node object.
     */
    public static class IntermediateNodeObjectBuilder {
        /**
         * This method is used to build intermediate node object from an list of object group data element
         *
         * @param objectGroupList  Specify the list of object group data elements.
         * @param dataObj          Specify the object group object.
         * @param intermediateGuid Specify the intermediate extended GUID.
         * @return Return the intermediate node object.
         */
        public LeafNodeObject build(List<ObjectGroupDataElementData> objectGroupList,
                                    ObjectGroupObjectData dataObj,
                                    ExGuid intermediateGuid) throws TikaException, IOException {
            AtomicReference<LeafNodeObject> node = new AtomicReference<>();
            AtomicReference<IntermediateNodeObject> rootNode = new AtomicReference<>();

            AtomicInteger index = new AtomicInteger(0);
            if (StreamObject.tryGetCurrent(ByteUtil.toByteArray(dataObj.data.content), index, node,
                    LeafNodeObject.class)) {
                if (dataObj.objectExGUIDArray == null) {
                    throw new TikaException(
                            "Failed to build intermediate node because the object extend GUID array does not exist.");
                }

                node.get().exGuid = intermediateGuid;

                // Contain a single Data Node Object.
                if (dataObj.objectExGUIDArray.count.getDecodedValue() == 1) {
                    AtomicReference<ObjectGroupObjectDeclare> dataNodeDeclare =
                            new AtomicReference<>();
                    ObjectGroupObjectData dataNodeData = this.FindByExGuid(objectGroupList,
                            dataObj.objectExGUIDArray.content.get(0), dataNodeDeclare);
                    BinaryItem data = dataNodeData.data;

                    node.get().dataNodeObjectData =
                            new DataNodeObjectData(ByteUtil.toByteArray(data.content), 0,
                                    (int) data.length.getDecodedValue());
                    node.get().dataNodeObjectData.exGuid = dataObj.objectExGUIDArray.content.get(0);
                    node.get().intermediateNodeObjectList = null;
                } else {
                    // Contain a list of LeafNodeObjectData
                    node.get().intermediateNodeObjectList = new ArrayList<LeafNodeObject>();
                    node.get().dataNodeObjectData = null;
                    for (ExGuid extGuid : dataObj.objectExGUIDArray.content) {
                        AtomicReference<ObjectGroupObjectDeclare> intermediateDeclare =
                                new AtomicReference<>();
                        ObjectGroupObjectData intermediateData =
                                this.FindByExGuid(objectGroupList, extGuid, intermediateDeclare);
                        node.get().intermediateNodeObjectList.add(
                                new IntermediateNodeObjectBuilder().build(objectGroupList,
                                        intermediateData, extGuid));
                    }
                }
            } else if (StreamObject.tryGetCurrent(ByteUtil.toByteArray(dataObj.data.content), index,
                    rootNode, IntermediateNodeObject.class)) {
                // In Sub chunking for larger than 1MB zip file, MOSS2010 could return IntermediateNodeObject.
                // For easy further process, the rootNode will be replaced by intermediate node instead.
                node.set(new LeafNodeObject());
                node.get().intermediateNodeObjectList = new ArrayList<LeafNodeObject>();
                node.get().dataSize = rootNode.get().dataSize;
                node.get().exGuid = rootNode.get().exGuid;
                node.get().signature = rootNode.get().signature;
                node.get().dataNodeObjectData = null;
                for (ExGuid extGuid : dataObj.objectExGUIDArray.content) {
                    AtomicReference<ObjectGroupObjectDeclare> intermediateDeclare =
                            new AtomicReference<>();
                    ObjectGroupObjectData intermediateData =
                            this.FindByExGuid(objectGroupList, extGuid, intermediateDeclare);
                    node.get().intermediateNodeObjectList.add(
                            new IntermediateNodeObjectBuilder().build(objectGroupList,
                                    intermediateData, extGuid));
                }
            } else {
                throw new TikaException(
                        "In the ObjectGroupDataElement cannot only contain the " +
                                "IntermediateNodeObject or IntermediateNodeObject.");
            }

            return node.get();
        }

        /**
         * This method is used to build intermediate node object from a byte array with a signature
         *
         * @param array     Specify the byte array.
         * @param signature Specify the signature.
         * @return Return the intermediate node object.
         */
        public LeafNodeObject build(byte[] array, SignatureObject signature) {
            LeafNodeObject nodeObject = new LeafNodeObject();
            nodeObject.dataSize = new DataSizeObject();
            nodeObject.dataSize.dataSize = array.length;

            nodeObject.signature = signature;
            nodeObject.exGuid =
                    new ExGuid(SequenceNumberGenerator.getCurrentSerialNumber(), UUID.randomUUID());

            nodeObject.dataNodeObjectData = new DataNodeObjectData(array, 0, array.length);
            nodeObject.intermediateNodeObjectList = null;

            // Now in the current implementation, one intermediate node only contain one single data object node.
            return nodeObject;
        }

        /**
         * This method is used to find the object group data element using the specified extended GUID
         *
         * @param objectGroupList Specify the object group data element list.
         * @param extendedGuid    Specify the extended GUID.
         * @param declare         Specify the output of ObjectGroupObjectDeclare.
         * @return Return the object group data element if found.
         */

        private ObjectGroupObjectData FindByExGuid(List<ObjectGroupDataElementData> objectGroupList,
                                                   ExGuid extendedGuid,
                                                   AtomicReference<ObjectGroupObjectDeclare> declare)
                throws TikaException {
            for (ObjectGroupDataElementData objectGroup : objectGroupList) {

                int findIndex = -1;
                for (int i = 0;
                        i < objectGroup.objectGroupDeclarations.objectDeclarationList.size(); ++i) {
                    ObjectGroupObjectDeclare objDeclare =
                            objectGroup.objectGroupDeclarations.objectDeclarationList.get(i);
                    if (objDeclare.objectExtendedGUID.equals(extendedGuid)) {
                        findIndex = i;
                        break;
                    }
                }

                if (findIndex < 0) {
                    continue;
                }

                declare.set(
                        objectGroup.objectGroupDeclarations.objectDeclarationList.get(findIndex));
                return objectGroup.objectGroupData.objectGroupObjectDataList.get(findIndex);
            }

            throw new TikaException("Cannot find the " + extendedGuid.guid.toString());
        }
    }
}
