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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BinaryItem;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellIDArray;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataNodeObjectData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGUIDArray;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class ObjectGroupDataElementData extends DataElementData {
    /**
     * Initializes a new instance of the ObjectGroupDataElementData class.
     */
    public ObjectGroupDataElementData() {
        this.ObjectGroupDeclarations = new ObjectGroupDeclarations();

        // The ObjectMetadataDeclaration is only present for MOSS2013, so leave null for default value.
        this.ObjectMetadataDeclaration = null;

        // The DataElementHash is only present for MOSS2013, so leave null for default value.
        this.DataElementHash = null;
        this.ObjectGroupData = new ObjectGroupData();
    }

    public org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElementHash DataElementHash;

    public ObjectGroupDeclarations ObjectGroupDeclarations;

    public ObjectGroupMetadataDeclarations ObjectMetadataDeclaration;

    public ObjectGroupData ObjectGroupData;

    /**
     * Used to convert the element into a byte List.
     *
     * @return A Byte list
     */
    @Override
    public List<Byte> SerializeToByteList() {
        List<Byte> result = new ArrayList<>();

        if (this.DataElementHash != null) {
            result.addAll(this.DataElementHash.SerializeToByteList());
        }

        result.addAll(this.ObjectGroupDeclarations.SerializeToByteList());
        if (this.ObjectMetadataDeclaration != null) {
            result.addAll(this.ObjectMetadataDeclaration.SerializeToByteList());
        }

        result.addAll(this.ObjectGroupData.SerializeToByteList());
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
    public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex) {
        AtomicInteger index = new AtomicInteger(startIndex);

        AtomicReference<DataElementHash> dataElementHash = new AtomicReference<>();
        if (StreamObject.TryGetCurrent(byteArray, index, dataElementHash, DataElementHash.class)) {
            this.DataElementHash = dataElementHash.get();
        }

        this.ObjectGroupDeclarations = StreamObject.GetCurrent(byteArray, index, ObjectGroupDeclarations.class);

        AtomicReference<ObjectGroupMetadataDeclarations> objectMetadataDeclaration =
                new AtomicReference<>(new ObjectGroupMetadataDeclarations());
        if (StreamObject.TryGetCurrent(byteArray, index, objectMetadataDeclaration,
                ObjectGroupMetadataDeclarations.class)) {
            this.ObjectMetadataDeclaration = objectMetadataDeclaration.get();
        }

        this.ObjectGroupData = StreamObject.GetCurrent(byteArray, index, ObjectGroupData.class);

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
        public List<DataElement> Build(NodeObject node) {
            List<DataElement> dataElements = new ArrayList<>();
            this.TravelNodeObject(node, dataElements);
            return dataElements;
        }

        /**
         * This method is used to travel the node tree and build the ObjectGroupDataElementData and the extra data element list
         *
         * @param node         Specify the object node.
         * @param dataElements Specify the list of data elements.
         */
        private void TravelNodeObject(NodeObject node, List<DataElement> dataElements) {
            if (node instanceof IntermediateNodeObject) {
                IntermediateNodeObject intermediateNodeObject = (IntermediateNodeObject) node;
                ObjectGroupDataElementData data = new ObjectGroupDataElementData();
                data.ObjectGroupDeclarations.ObjectDeclarationList.add(this.CreateObjectDeclare(node));
                data.ObjectGroupData.ObjectGroupObjectDataList.add(
                        this.CreateObjectData((IntermediateNodeObject) node));

                dataElements.add(new DataElement(DataElementType.ObjectGroupDataElementData, data));

                for (LeafNodeObject child : intermediateNodeObject.IntermediateNodeObjectList) {
                    this.TravelNodeObject(child, dataElements);
                }
            } else if (node instanceof LeafNodeObject) {
                LeafNodeObject intermediateNode = (LeafNodeObject) node;

                ObjectGroupDataElementData data = new ObjectGroupDataElementData();
                data.ObjectGroupDeclarations.ObjectDeclarationList.add(this.CreateObjectDeclare(node));
                data.ObjectGroupData.ObjectGroupObjectDataList.add(this.CreateObjectData(intermediateNode));

                if (intermediateNode.DataNodeObjectData != null) {
                    data.ObjectGroupDeclarations.ObjectDeclarationList.add(
                            this.CreateObjectDeclare(intermediateNode.DataNodeObjectData));
                    data.ObjectGroupData.ObjectGroupObjectDataList.add(
                            this.CreateObjectData(intermediateNode.DataNodeObjectData));
                    dataElements.add(new DataElement(DataElementType.ObjectGroupDataElementData, data));
                    return;
                }

                if (intermediateNode.DataNodeObjectData == null &&
                        intermediateNode.IntermediateNodeObjectList != null) {
                    dataElements.add(new DataElement(DataElementType.ObjectGroupDataElementData, data));

                    for (LeafNodeObject child : intermediateNode.IntermediateNodeObjectList) {
                        this.TravelNodeObject(child, dataElements);
                    }

                    return;
                }

                throw new RuntimeException(
                        "The DataNodeObjectData and IntermediateNodeObjectList properties in LeafNodeObjectData type cannot be null in the same time.");
            }
        }

        /**
         * This method is used to create ObjectGroupObjectDeclare instance from a node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectDeclare instance.
         */
        private ObjectGroupObjectDeclare CreateObjectDeclare(NodeObject node) {
            ObjectGroupObjectDeclare objectGroupObjectDeclare = new ObjectGroupObjectDeclare();

            objectGroupObjectDeclare.ObjectExtendedGUID = node.ExGuid;
            objectGroupObjectDeclare.ObjectPartitionID = new Compact64bitInt(1);
            objectGroupObjectDeclare.CellReferencesCount = new Compact64bitInt(0);
            objectGroupObjectDeclare.ObjectReferencesCount = new Compact64bitInt(0);
            objectGroupObjectDeclare.ObjectDataSize = new Compact64bitInt(node.GetContent().size());

            return objectGroupObjectDeclare;
        }

        /**
         * This method is used to create ObjectGroupObjectDeclare instance from a data node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectDeclare instance.
         */
        private ObjectGroupObjectDeclare CreateObjectDeclare(DataNodeObjectData node) {
            ObjectGroupObjectDeclare objectGroupObjectDeclare = new ObjectGroupObjectDeclare();

            objectGroupObjectDeclare.ObjectExtendedGUID = node.ExGuid;
            objectGroupObjectDeclare.ObjectPartitionID = new Compact64bitInt(1);
            objectGroupObjectDeclare.CellReferencesCount = new Compact64bitInt(0);
            objectGroupObjectDeclare.ObjectReferencesCount = new Compact64bitInt(1);
            objectGroupObjectDeclare.ObjectDataSize = new Compact64bitInt(node.ObjectData.length);

            return objectGroupObjectDeclare;
        }

        /**
         * This method is used to create ObjectGroupObjectData instance from a root node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectData instance.
         */
        private ObjectGroupObjectData CreateObjectData(IntermediateNodeObject node) {
            ObjectGroupObjectData objectData = new ObjectGroupObjectData();

            objectData.cellIDArray = new CellIDArray(0, null);

            List<ExGuid> extendedGuidList = new ArrayList<ExGuid>();
            for (LeafNodeObject child : node.IntermediateNodeObjectList) {
                extendedGuidList.add(child.ExGuid);
            }

            objectData.ObjectExGUIDArray = new ExGUIDArray(extendedGuidList);
            objectData.Data = new BinaryItem(node.SerializeToByteList());

            return objectData;
        }

        /**
         * This method is used to create ObjectGroupObjectData instance from a intermediate node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectData instance.
         */
        private ObjectGroupObjectData CreateObjectData(LeafNodeObject node) {
            ObjectGroupObjectData objectData = new ObjectGroupObjectData();

            objectData.cellIDArray = new CellIDArray(0, null);
            List<ExGuid> extendedGuidList = new ArrayList<ExGuid>();

            if (node.DataNodeObjectData != null) {
                extendedGuidList.add(node.DataNodeObjectData.ExGuid);
            } else if (node.IntermediateNodeObjectList != null) {
                for (LeafNodeObject child : node.IntermediateNodeObjectList) {
                    extendedGuidList.add(child.ExGuid);
                }
            }

            objectData.ObjectExGUIDArray = new ExGUIDArray(extendedGuidList);
            objectData.Data = new BinaryItem(node.SerializeToByteList());

            return objectData;
        }

        /**
         * This method is used to create ObjectGroupObjectData instance from a data node object
         *
         * @param node Specify the node object.
         * @return Return the ObjectGroupObjectData instance.
         */
        private ObjectGroupObjectData CreateObjectData(DataNodeObjectData node) {
            ObjectGroupObjectData objectData = new ObjectGroupObjectData();
            objectData.cellIDArray = new CellIDArray(0, null);
            objectData.ObjectExGUIDArray = new ExGUIDArray(new ArrayList<>());
            objectData.Data = new BinaryItem(ByteUtil.toListOfByte(node.ObjectData));
            return objectData;
        }
    }
}