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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellIDArray;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataNodeObjectData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGUIDArray;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class ObjectGroupDataElementData extends DataElementData {
    public org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElementHash
            dataElementHash;
    public ObjectGroupDeclarations objectGroupDeclarations;
    public ObjectGroupMetadataDeclarations objectMetadataDeclaration;
    public ObjectGroupData objectGroupData;

    /**
     * Initializes a new instance of the ObjectGroupDataElementData class.
     */
    public ObjectGroupDataElementData() {
        this.objectGroupDeclarations = new ObjectGroupDeclarations();

        // The ObjectMetadataDeclaration is only present for MOSS2013, so leave null for default value.
        this.objectMetadataDeclaration = null;

        // The DataElementHash is only present for MOSS2013, so leave null for default value.
        this.dataElementHash = null;
        this.objectGroupData = new ObjectGroupData();
    }

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
    @Override
    public List<Byte> serializeToByteList() throws TikaException, IOException {
        List<Byte> result = new ArrayList<>();

        if (this.dataElementHash != null) {
            result.addAll(this.dataElementHash.serializeToByteList());
        }

        result.addAll(this.objectGroupDeclarations.serializeToByteList());
        if (this.objectMetadataDeclaration != null) {
            result.addAll(this.objectMetadataDeclaration.serializeToByteList());
        }

        result.addAll(this.objectGroupData.serializeToByteList());
        return result;
    }

    /**
     * Used to return the length of this element.
     *
     * @param byteArray  A Byte array
     * @param startIndex Start position
     * @return The length of the element
     */
    @Override
    public int deserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
            throws TikaException, IOException {
        AtomicInteger index = new AtomicInteger(startIndex);

        AtomicReference<DataElementHash> dataElementHash = new AtomicReference<>();
        if (StreamObject.tryGetCurrent(byteArray, index, dataElementHash, DataElementHash.class)) {
            this.dataElementHash = dataElementHash.get();
        }

        this.objectGroupDeclarations =
                StreamObject.getCurrent(byteArray, index, ObjectGroupDeclarations.class);

        AtomicReference<ObjectGroupMetadataDeclarations> objectMetadataDeclaration =
                new AtomicReference<>(new ObjectGroupMetadataDeclarations());
        if (StreamObject.tryGetCurrent(byteArray, index, objectMetadataDeclaration,
                ObjectGroupMetadataDeclarations.class)) {
            this.objectMetadataDeclaration = objectMetadataDeclaration.get();
        }

        this.objectGroupData = StreamObject.getCurrent(byteArray, index, ObjectGroupData.class);

        return index.get() - startIndex;
    }

    /**
     * The internal class for build a list of DataElement from an node object.
     */
    public static class Builder {
        /**
         * This method is used to build  a list of DataElement from an node object
         *
         * @param node Specify the node object.
         * @return Return the list of data elements build from the specified node object.
         */
        public List<DataElement> build(NodeObject node) throws TikaException, IOException {
            List<DataElement> dataElements = new ArrayList<>();
            this.travelNodeObject(node, dataElements);
            return dataElements;
        }

        /**
         * This method is used to travel the node tree and build the ObjectGroupDataElementData
         * and the extra data element list
         *
         * @param node         Specify the object node.
         * @param dataElements Specify the list of data elements.
         */
        private void travelNodeObject(NodeObject node, List<DataElement> dataElements)
                throws TikaException, IOException {
            if (node instanceof IntermediateNodeObject) {
                IntermediateNodeObject intermediateNodeObject = (IntermediateNodeObject) node;
                ObjectGroupDataElementData data = new ObjectGroupDataElementData();
                data.objectGroupDeclarations.objectDeclarationList.add(
                        this.createObjectDeclare(node));
                data.objectGroupData.objectGroupObjectDataList.add(
                        this.createObjectData((IntermediateNodeObject) node));

                dataElements.add(new DataElement(DataElementType.ObjectGroupDataElementData, data));

                for (LeafNodeObject child : intermediateNodeObject.intermediateNodeObjectList) {
                    this.travelNodeObject(child, dataElements);
                }
            } else if (node instanceof LeafNodeObject) {
                LeafNodeObject intermediateNode = (LeafNodeObject) node;

                ObjectGroupDataElementData data = new ObjectGroupDataElementData();
                data.objectGroupDeclarations.objectDeclarationList.add(
                        this.createObjectDeclare(node));
                data.objectGroupData.objectGroupObjectDataList.add(
                        this.createObjectData(intermediateNode));

                if (intermediateNode.dataNodeObjectData != null) {
                    data.objectGroupDeclarations.objectDeclarationList.add(
                            this.createObjectDeclare(intermediateNode.dataNodeObjectData));
                    data.objectGroupData.objectGroupObjectDataList.add(
                            this.createObjectData(intermediateNode.dataNodeObjectData));
                    dataElements.add(
                            new DataElement(DataElementType.ObjectGroupDataElementData, data));
                    return;
                }

                if (intermediateNode.dataNodeObjectData == null &&
                        intermediateNode.intermediateNodeObjectList != null) {
                    dataElements.add(
                            new DataElement(DataElementType.ObjectGroupDataElementData, data));

                    for (LeafNodeObject child : intermediateNode.intermediateNodeObjectList) {
                        this.travelNodeObject(child, dataElements);
                    }

                    return;
                }

                throw new TikaException(
                        "The DataNodeObjectData and IntermediateNodeObjectList properties in " +
                                "LeafNodeObjectData type cannot be null in the same time.");
            }
        }

        /**
         * This method is used to create ObjectGroupObjectDeclare instance from a node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectDeclare instance.
         */
        private ObjectGroupObjectDeclare createObjectDeclare(NodeObject node) throws TikaException {
            ObjectGroupObjectDeclare objectGroupObjectDeclare = new ObjectGroupObjectDeclare();

            objectGroupObjectDeclare.objectExtendedGUID = node.exGuid;
            objectGroupObjectDeclare.objectPartitionID = new Compact64bitInt(1);
            objectGroupObjectDeclare.cellReferencesCount = new Compact64bitInt(0);
            objectGroupObjectDeclare.objectReferencesCount = new Compact64bitInt(0);
            objectGroupObjectDeclare.objectDataSize = new Compact64bitInt(node.getContent().size());

            return objectGroupObjectDeclare;
        }

        /**
         * This method is used to create ObjectGroupObjectDeclare instance from a data node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectDeclare instance.
         */
        private ObjectGroupObjectDeclare createObjectDeclare(DataNodeObjectData node) {
            ObjectGroupObjectDeclare objectGroupObjectDeclare = new ObjectGroupObjectDeclare();

            objectGroupObjectDeclare.objectExtendedGUID = node.exGuid;
            objectGroupObjectDeclare.objectPartitionID = new Compact64bitInt(1);
            objectGroupObjectDeclare.cellReferencesCount = new Compact64bitInt(0);
            objectGroupObjectDeclare.objectReferencesCount = new Compact64bitInt(1);
            objectGroupObjectDeclare.objectDataSize = new Compact64bitInt(node.objectData.length);

            return objectGroupObjectDeclare;
        }

        /**
         * This method is used to create ObjectGroupObjectData instance from a root node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectData instance.
         */
        private ObjectGroupObjectData createObjectData(IntermediateNodeObject node)
                throws TikaException, IOException {
            ObjectGroupObjectData objectData = new ObjectGroupObjectData();

            objectData.cellIDArray = new CellIDArray(0, null);

            List<ExGuid> extendedGuidList = new ArrayList<ExGuid>();
            for (LeafNodeObject child : node.intermediateNodeObjectList) {
                extendedGuidList.add(child.exGuid);
            }

            objectData.objectExGUIDArray = new ExGUIDArray(extendedGuidList);
            objectData.data = new BinaryItem(node.serializeToByteList());

            return objectData;
        }

        /**
         * This method is used to create ObjectGroupObjectData instance from a intermediate node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectData instance.
         */
        private ObjectGroupObjectData createObjectData(LeafNodeObject node)
                throws TikaException, IOException {
            ObjectGroupObjectData objectData = new ObjectGroupObjectData();

            objectData.cellIDArray = new CellIDArray(0, null);
            List<ExGuid> extendedGuidList = new ArrayList<ExGuid>();

            if (node.dataNodeObjectData != null) {
                extendedGuidList.add(node.dataNodeObjectData.exGuid);
            } else if (node.intermediateNodeObjectList != null) {
                for (LeafNodeObject child : node.intermediateNodeObjectList) {
                    extendedGuidList.add(child.exGuid);
                }
            }

            objectData.objectExGUIDArray = new ExGUIDArray(extendedGuidList);
            objectData.data = new BinaryItem(node.serializeToByteList());

            return objectData;
        }

        /**
         * This method is used to create ObjectGroupObjectData instance from a data node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectData instance.
         */
        private ObjectGroupObjectData createObjectData(DataNodeObjectData node) {
            ObjectGroupObjectData objectData = new ObjectGroupObjectData();
            objectData.cellIDArray = new CellIDArray(0, null);
            objectData.objectExGUIDArray = new ExGUIDArray(new ArrayList<>());
            objectData.data = new BinaryItem(ByteUtil.toListOfByte(node.objectData));
            return objectData;
        }
    }
}
