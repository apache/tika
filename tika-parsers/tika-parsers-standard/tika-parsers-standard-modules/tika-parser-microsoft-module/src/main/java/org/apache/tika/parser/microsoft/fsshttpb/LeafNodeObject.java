package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LeafNodeObject extends NodeObject {
    /// <summary>
    /// Initializes a new instance of the LeafNodeObjectData class.
    /// </summary>
    public LeafNodeObject() {
        super(StreamObjectTypeHeaderStart.LeafNodeObject);
    }

    /// <summary>
    /// Gets or sets the data node object.
    /// </summary>
    public DataNodeObjectData DataNodeObjectData;

    /// <summary>
    /// Gets or sets the data hash.
    /// </summary>
    public DataHashObject DataHash;

    /// <summary>
    /// Get all the content which is represented by the intermediate node object.
    /// </summary>
    /// <returns>Return the byte list of intermediate node object content.</returns>
    @Override
    public List<Byte> GetContent() {
        List<Byte> content = new ArrayList<Byte>();

        if (this.DataNodeObjectData != null) {
            for (byte b : this.DataNodeObjectData.ObjectData) {
                content.add(b);
            }
        } else if (this.IntermediateNodeObjectList != null) {
            for (LeafNodeObject intermediateNode : this.IntermediateNodeObjectList) {
                content.addAll(intermediateNode.GetContent());
            }
        } else {
            throw new RuntimeException(
                    "The DataNodeObjectData and IntermediateNodeObjectList properties in LeafNodeObjectData cannot be null at the same time.");
        }

        return content;
    }

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());
        if (lengthOfItems != 0) {
            throw new StreamObjectParseErrorException(currentIndex.get(), "LeafNodeObjectData",
                    "Stream Object over-parse error", null);
        }

        this.Signature = StreamObject.GetCurrent(byteArray, index, SignatureObject.class);
        this.DataSize = StreamObject.GetCurrent(byteArray, index, DataSizeObject.class);

        // Try to read StreamObjectHeaderStart to see there is data hash object or not
        AtomicReference<StreamObjectHeaderStart> streamObjectHeader = new AtomicReference<>();
        if ((StreamObjectHeaderStart.TryParse(byteArray, index.get(), streamObjectHeader)) != 0) {
            if (streamObjectHeader.get().type == StreamObjectTypeHeaderStart.DataHashObject) {
                this.DataHash = StreamObject.GetCurrent(byteArray, index, DataHashObject.class);
            }
        }

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>A constant value</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        byteList.addAll(this.Signature.SerializeToByteList());
        byteList.addAll(this.DataSize.SerializeToByteList());
        return 0;
    }

    /// <summary>
    /// The class is used to build a intermediate node object.
    /// </summary>
    public static class IntermediateNodeObjectBuilder {
        /// <summary>
        /// This method is used to build intermediate node object from an list of object group data element.
        /// </summary>
        /// <param name="objectGroupList">Specify the list of object group data elements.</param>
        /// <param name="dataObj">Specify the object group object.</param>
        /// <param name="intermediateGuid">Specify the intermediate extended GUID.</param>
        /// <returns>Return the intermediate node object.</returns>
        public LeafNodeObject Build(List<ObjectGroupDataElementData> objectGroupList, ObjectGroupObjectData dataObj,
                                    ExGuid intermediateGuid) {
            AtomicReference<LeafNodeObject> node = new AtomicReference<>();
            AtomicReference<IntermediateNodeObject> rootNode = new AtomicReference<>();

            AtomicInteger index = new AtomicInteger(0);
            if (StreamObject.TryGetCurrent(ByteUtil.toByteArray(dataObj.Data.Content), index, node,
                    LeafNodeObject.class)) {
                if (dataObj.ObjectExGUIDArray == null) {
                    throw new RuntimeException(
                            "Failed to build intermediate node because the object extend GUID array does not exist.");
                }

                node.get().ExGuid = intermediateGuid;

                // Contain a single Data Node Object.
                if (dataObj.ObjectExGUIDArray.Count.getDecodedValue() == 1) {
                    AtomicReference<ObjectGroupObjectDeclare> dataNodeDeclare = new AtomicReference<>();
                    ObjectGroupObjectData dataNodeData =
                            this.FindByExGuid(objectGroupList, dataObj.ObjectExGUIDArray.Content.get(0),
                                    dataNodeDeclare);
                    BinaryItem data = dataNodeData.Data;

                    node.get().DataNodeObjectData = new DataNodeObjectData(ByteUtil.toByteArray(data.Content), 0,
                            (int) data.Length.getDecodedValue());
                    node.get().DataNodeObjectData.ExGuid = dataObj.ObjectExGUIDArray.Content.get(0);
                    node.get().IntermediateNodeObjectList = null;

//                        if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                        {
//                            MsfsshttpdCapture.VerifyObjectGroupObjectDataForDataNodeObject(dataNodeData, dataNodeDeclare, objectGroupList, SharedContext.Current.Site);
//                        }
                } else {
                    // Contain a list of LeafNodeObjectData
                    node.get().IntermediateNodeObjectList = new ArrayList<LeafNodeObject>();
                    node.get().DataNodeObjectData = null;
                    for (ExGuid extGuid : dataObj.ObjectExGUIDArray.Content) {
                        AtomicReference<ObjectGroupObjectDeclare> intermediateDeclare = new AtomicReference<>();
                        ObjectGroupObjectData intermediateData =
                                this.FindByExGuid(objectGroupList, extGuid, intermediateDeclare);
                        node.get().IntermediateNodeObjectList.add(
                                new IntermediateNodeObjectBuilder().Build(objectGroupList, intermediateData, extGuid));

//                            if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                            {
//                                MsfsshttpdCapture.VerifyObjectGroupObjectDataForIntermediateNode(intermediateData, intermediateDeclare, objectGroupList, SharedContext.Current.Site);
//                            }
                    }
                }
            } else if (StreamObject.TryGetCurrent(ByteUtil.toByteArray(dataObj.Data.Content), index, rootNode,
                    IntermediateNodeObject.class)) {
                // In Sub chunking for larger than 1MB zip file, MOSS2010 could return IntermediateNodeObject.
                // For easy further process, the rootNode will be replaced by intermediate node instead.
                node.set(new LeafNodeObject());
                node.get().IntermediateNodeObjectList = new ArrayList<LeafNodeObject>();
                node.get().DataSize = rootNode.get().DataSize;
                node.get().ExGuid = rootNode.get().ExGuid;
                node.get().Signature = rootNode.get().Signature;
                node.get().DataNodeObjectData = null;
                for (ExGuid extGuid : dataObj.ObjectExGUIDArray.Content) {
                    AtomicReference<ObjectGroupObjectDeclare> intermediateDeclare = new AtomicReference<>();
                    ObjectGroupObjectData intermediateData =
                            this.FindByExGuid(objectGroupList, extGuid, intermediateDeclare);
                    node.get().IntermediateNodeObjectList.add(
                            new IntermediateNodeObjectBuilder().Build(objectGroupList, intermediateData, extGuid));

//                        if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                        {
//                            MsfsshttpdCapture.VerifyObjectGroupObjectDataForIntermediateNode(intermediateData, intermediateDeclare, objectGroupList, SharedContext.Current.Site);
//                        }
                }
            } else {
                throw new RuntimeException(
                        "In the ObjectGroupDataElement cannot only contain the IntermediateNodeObject or IntermediateNodeOBject.");
            }

            return node.get();
        }

        /// <summary>
        /// This method is used to build intermediate node object from a byte array with a signature.
        /// </summary>
        /// <param name="array">Specify the byte array.</param>
        /// <param name="signature">Specify the signature.</param>
        /// <returns>Return the intermediate node object.</returns>
        public LeafNodeObject Build(byte[] array, SignatureObject signature) {
            LeafNodeObject nodeObject = new LeafNodeObject();
            nodeObject.DataSize = new DataSizeObject();
            nodeObject.DataSize.DataSize = (long) array.length;

            nodeObject.Signature = signature;
            nodeObject.ExGuid = new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());

            nodeObject.DataNodeObjectData = new DataNodeObjectData(array, 0, array.length);
            nodeObject.IntermediateNodeObjectList = null;

            // Now in the current implementation, one intermediate node only contain one single data object node.
            return nodeObject;
        }

        /// <summary>
        /// This method is used to find the object group data element using the specified extended GUID.
        /// </summary>
        /// <param name="objectGroupList">Specify the object group data element list.</param>
        /// <param name="extendedGuid">Specify the extended GUID.</param>
        /// <param name="declare">Specify the output of ObjectGroupObjectDeclare.</param>
        /// <returns>Return the object group data element if found.</returns>
        /// <exception cref="RuntimeException">If not found, throw the RuntimeException exception.</exception>
        private ObjectGroupObjectData FindByExGuid(List<ObjectGroupDataElementData> objectGroupList,
                                                   ExGuid extendedGuid,
                                                   AtomicReference<ObjectGroupObjectDeclare> declare) {
            for (ObjectGroupDataElementData objectGroup : objectGroupList) {

                int findIndex = -1;
                for (int i = 0; i < objectGroup.ObjectGroupDeclarations.ObjectDeclarationList.size(); ++i) {
                    ObjectGroupObjectDeclare objDeclare =
                            objectGroup.ObjectGroupDeclarations.ObjectDeclarationList.get(i);
                    if (objDeclare.ObjectExtendedGUID.equals(extendedGuid)) {
                        findIndex = i;
                        break;
                    }
                }

                if (findIndex < 0) {
                    continue;
                }

                declare.set(objectGroup.ObjectGroupDeclarations.ObjectDeclarationList.get(findIndex));
                return objectGroup.ObjectGroupData.ObjectGroupObjectDataList.get(findIndex);
            }

            throw new RuntimeException("Cannot find the " + extendedGuid.guid.toString());
        }
    }
}