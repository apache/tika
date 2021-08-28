package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ObjectGroupDataElementData extends DataElementData
    {
        /// <summary>
        /// Initializes a new instance of the ObjectGroupDataElementData class.
        /// </summary>
        public ObjectGroupDataElementData()
        {
            this.ObjectGroupDeclarations = new ObjectGroupDeclarations();

            // The ObjectMetadataDeclaration is only present for MOSS2013, so leave null for default value.
            this.ObjectMetadataDeclaration = null;

            // The DataElementHash is only present for MOSS2013, so leave null for default value.
            this.DataElementHash = null;
            this.ObjectGroupData = new ObjectGroupData();
        }

        /// <summary>
        ///  Gets or sets an optional data element hash for the object data group.
        /// </summary>
        public DataElementHash DataElementHash;

        /// <summary>
        /// Gets or sets an optional array of object declarations that specifies the object.
        /// </summary>
        public ObjectGroupDeclarations ObjectGroupDeclarations;

        /// <summary>
        /// Gets or sets an object metadata declaration. If no object metadata exists, this field must be omitted.
        /// </summary>
        public ObjectGroupMetadataDeclarations ObjectMetadataDeclaration;

        /// <summary>
        /// Gets or sets an object group data.
        /// </summary>
        public ObjectGroupData ObjectGroupData;

        /// <summary>
        /// Used to convert the element into a byte List.
        /// </summary>
        /// <returns>A Byte list</returns>
        @Override
        public List<Byte> SerializeToByteList()
        {
            List<Byte> result = new ArrayList<>();

            if (this.DataElementHash != null)
            {
                result.addAll(this.DataElementHash.SerializeToByteList());
            }

            result.addAll(this.ObjectGroupDeclarations.SerializeToByteList());
            if (this.ObjectMetadataDeclaration != null)
            {
                result.addAll(this.ObjectMetadataDeclaration.SerializeToByteList());
            }

            result.addAll(this.ObjectGroupData.SerializeToByteList());
            return result;
        }

        /// <summary>
        /// Used to return the length of this element.
        /// </summary>
        /// <param name="byteArray">A Byte array</param>
        /// <param name="startIndex">Start position</param>
        /// <returns>The length of the element</returns>
        @Override
        public int DeserializeDataElementDataFromByteArray(byte[] byteArray, int startIndex)
        {
            AtomicInteger index = new AtomicInteger(startIndex);

            AtomicReference<DataElementHash> dataElementHash = new AtomicReference<>();
            if (StreamObject.TryGetCurrent(byteArray, index, dataElementHash, DataElementHash.class))
            {
                this.DataElementHash = dataElementHash.get();
            }

            this.ObjectGroupDeclarations = StreamObject.GetCurrent(byteArray, index, ObjectGroupDeclarations.class);

            AtomicReference<ObjectGroupMetadataDeclarations> objectMetadataDeclaration = new AtomicReference<>(new ObjectGroupMetadataDeclarations());
            if (StreamObject.TryGetCurrent(byteArray, index, objectMetadataDeclaration, ObjectGroupMetadataDeclarations.class))
            {
                this.ObjectMetadataDeclaration = objectMetadataDeclaration.get();
            }

            this.ObjectGroupData = StreamObject.GetCurrent(byteArray, index, ObjectGroupData.class);

            return index.get() - startIndex;
        }

        /// <summary>
        /// The internal class for build a list of DataElement from an node object.
        /// </summary>
        public static class Builder
        {
            /// <summary>
            /// This method is used to build  a list of DataElement from an node object.
            /// </summary>
            /// <param name="node">Specify the node object.</param>
            /// <returns>Return the list of data elements build from the specified node object.</returns>
            public List<DataElement> Build(NodeObject node)
            {
                List<DataElement> dataElements = new ArrayList<>();
                this.TravelNodeObject(node, dataElements);
                return dataElements;
            }

            /// <summary>
            /// This method is used to travel the node tree and build the ObjectGroupDataElementData and the extra data element list.
            /// </summary>
            /// <param name="node">Specify the object node.</param>
            /// <param name="dataElements">Specify the list of data elements.</param>
            private void TravelNodeObject(NodeObject node, List<DataElement> dataElements)
            {
                if (node instanceof IntermediateNodeObject)
                {
                    ObjectGroupDataElementData data = new ObjectGroupDataElementData();
                    data.ObjectGroupDeclarations.ObjectDeclarationList.Add(this.CreateObjectDeclare(node));
                    data.ObjectGroupData.ObjectGroupObjectDataList.Add(this.CreateObjectData((IntermediateNodeObject)node));

                    dataElements.Add(new DataElement(DataElementType.ObjectGroupDataElementData, data));

                    foreach (LeafNodeObject child in (node as IntermediateNodeObject).IntermediateNodeObjectList)
                    {
                        this.TravelNodeObject(child, ref dataElements);
                    }
                }
                else if (node is LeafNodeObject)
                {
                    LeafNodeObject intermediateNode = node as LeafNodeObject;

                    ObjectGroupDataElementData data = new ObjectGroupDataElementData();
                    data.ObjectGroupDeclarations.ObjectDeclarationList.Add(this.CreateObjectDeclare(node));
                    data.ObjectGroupData.ObjectGroupObjectDataList.Add(this.CreateObjectData(intermediateNode));

                    if (intermediateNode.DataNodeObjectData != null)
                    {
                        data.ObjectGroupDeclarations.ObjectDeclarationList.Add(this.CreateObjectDeclare(intermediateNode.DataNodeObjectData));
                        data.ObjectGroupData.ObjectGroupObjectDataList.Add(this.CreateObjectData(intermediateNode.DataNodeObjectData));
                        dataElements.Add(new DataElement(DataElementType.ObjectGroupDataElementData, data));
                        return;
                    }

                    if (intermediateNode.DataNodeObjectData == null && intermediateNode.IntermediateNodeObjectList != null)
                    {
                        dataElements.Add(new DataElement(DataElementType.ObjectGroupDataElementData, data));

                        foreach (LeafNodeObject child in intermediateNode.IntermediateNodeObjectList)
                        {
                            this.TravelNodeObject(child, ref dataElements);
                        }

                        return;
                    }
                   
                    throw new System.InvalidOperationException("The DataNodeObjectData and IntermediateNodeObjectList properties in LeafNodeObjectData type cannot be null in the same time.");
                }
            }

            /// <summary>
            /// This method is used to create ObjectGroupObjectDeclare instance from a node object.
            /// </summary>
            /// <param name="node">Specify the node object.</param>
            /// <returns>Return the ObjectGroupObjectDeclare instance.</returns>
            private ObjectGroupObjectDeclare CreateObjectDeclare(NodeObject node)
            {
                ObjectGroupObjectDeclare objectGroupObjectDeclare = new ObjectGroupObjectDeclare();

                objectGroupObjectDeclare.ObjectExtendedGUID = node.ExGuid;
                objectGroupObjectDeclare.ObjectPartitionID = new Compact64bitInt(1u);
                objectGroupObjectDeclare.CellReferencesCount = new Compact64bitInt(0u);
                objectGroupObjectDeclare.ObjectReferencesCount = new Compact64bitInt(0u);
                objectGroupObjectDeclare.ObjectDataSize = new Compact64bitInt((long)node.GetContent().Count);

                return objectGroupObjectDeclare;
            }

            /// <summary>
            /// This method is used to create ObjectGroupObjectDeclare instance from a data node object.
            /// </summary>
            /// <param name="node">Specify the node object.</param>
            /// <returns>Return the ObjectGroupObjectDeclare instance.</returns>
            private ObjectGroupObjectDeclare CreateObjectDeclare(DataNodeObjectData node)
            {
                ObjectGroupObjectDeclare objectGroupObjectDeclare = new ObjectGroupObjectDeclare();

                objectGroupObjectDeclare.ObjectExtendedGUID = node.ExGuid;
                objectGroupObjectDeclare.ObjectPartitionID = new Compact64bitInt(1u);
                objectGroupObjectDeclare.CellReferencesCount = new Compact64bitInt(0u);
                objectGroupObjectDeclare.ObjectReferencesCount = new Compact64bitInt(1u);
                objectGroupObjectDeclare.ObjectDataSize = new Compact64bitInt((long)node.ObjectData.LongLength);

                return objectGroupObjectDeclare;
            }

            /// <summary>
            /// This method is used to create ObjectGroupObjectData instance from a root node object.
            /// </summary>
            /// <param name="node">Specify the node object.</param>
            /// <returns>Return the ObjectGroupObjectData instance.</returns>
            private ObjectGroupObjectData CreateObjectData(IntermediateNodeObject node)
            {
                ObjectGroupObjectData objectData = new ObjectGroupObjectData();

                objectData.CellIDArray = new CellIDArray(0u, null);

                List<ExGuid> extendedGuidList = new ArrayList<ExGuid>();
                foreach (LeafNodeObject child in node.IntermediateNodeObjectList)
                {
                    extendedGuidList.Add(child.ExGuid);
                }

                objectData.ObjectExGUIDArray = new ExGUIDArray(extendedGuidList);
                objectData.Data = new BinaryItem(node.SerializeToByteList());

                return objectData;
            }

            /// <summary>
            /// This method is used to create ObjectGroupObjectData instance from a intermediate node object.
            /// </summary>
            /// <param name="node">Specify the node object.</param>
            /// <returns>Return the ObjectGroupObjectData instance.</returns>
            private ObjectGroupObjectData CreateObjectData(LeafNodeObject node)
            {
                ObjectGroupObjectData objectData = new ObjectGroupObjectData();

                objectData.CellIDArray = new CellIDArray(0u, null);
                List<ExGuid> extendedGuidList = new ArrayList<ExGuid>();

                if (node.DataNodeObjectData != null)
                {
                    extendedGuidList.Add(node.DataNodeObjectData.ExGuid);
                }
                else if (node.IntermediateNodeObjectList != null)
                {
                    foreach (LeafNodeObject child in node.IntermediateNodeObjectList)
                    {
                        extendedGuidList.Add(child.ExGuid);
                    }
                }

                objectData.ObjectExGUIDArray = new ExGUIDArray(extendedGuidList);
                objectData.Data = new BinaryItem(node.SerializeToByteList());

                return objectData;
            }

            /// <summary>
            /// This method is used to create ObjectGroupObjectData instance from a data node object.
            /// </summary>
            /// <param name="node">Specify the node object.</param>
            /// <returns>Return the ObjectGroupObjectData instance.</returns>
            private ObjectGroupObjectData CreateObjectData(DataNodeObjectData node)
            {
                ObjectGroupObjectData objectData = new ObjectGroupObjectData();
                objectData.CellIDArray = new CellIDArray(0u, null);
                objectData.ObjectExGUIDArray = new ExGUIDArray(new ArrayList<ExGuid>());
                objectData.Data = new BinaryItem(node.ObjectData);
                return objectData;
            }
        }
    }






}