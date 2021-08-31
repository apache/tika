package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;

public class IntermediateNodeObject extends NodeObject {
    /// <summary>
    /// Initializes a new instance of the IntermediateNodeObject class.
    /// </summary>
    public IntermediateNodeObject() {
        super(StreamObjectTypeHeaderStart.IntermediateNodeObject);
        this.IntermediateNodeObjectList = new ArrayList<>();
    }

    /// <summary>
    /// Get all the content which is represented by the root node object.
    /// </summary>
    /// <returns>Return the byte list of root node object content.</returns>
    @Override
    public List<Byte> GetContent() {
        List<Byte> content = new ArrayList<Byte>();

        for (LeafNodeObject intermediateNode : this.IntermediateNodeObjectList) {
            content.addAll(intermediateNode.GetContent());
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
            throw new StreamObjectParseErrorException(currentIndex.get(), "IntermediateNodeObject",
                    "Stream Object over-parse error", null);
        }

        this.Signature = StreamObject.GetCurrent(byteArray, index, SignatureObject.class);
        this.DataSize = StreamObject.GetCurrent(byteArray, index, DataSizeObject.class);

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The Byte list</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        byteList.addAll(this.Signature.SerializeToByteList());
        byteList.addAll(this.DataSize.SerializeToByteList());
        return 0;
    }

    /// <summary>
    /// The class is used to build a root node object.
    /// </summary>
    public static class RootNodeObjectBuilder {
        /// <summary>
        /// This method is used to build a root node object from an data element list with the specified storage index extended GUID.
        /// </summary>
        /// <param name="dataElements">Specify the data element list.</param>
        /// <param name="storageIndexExGuid">Specify the storage index extended GUID.</param>
        /// <returns>Return a root node object build from the data element list.</returns>
        public IntermediateNodeObject BuildFromListOfDataElements(List<DataElement> dataElements,
                                                                  ExGuid storageIndexExGuid) {
            if (DataElementUtils.TryAnalyzeWhetherFullDataElementList(dataElements, storageIndexExGuid)
                    && DataElementUtils.TryAnalyzeWhetherConfirmSchema(dataElements, storageIndexExGuid)) {
                AtomicReference<ExGuid> rootObjectExGUID = new AtomicReference<>();
                List<ObjectGroupDataElementData> objectGroupList =
                        DataElementUtils.GetDataObjectDataElementData(dataElements, storageIndexExGuid,
                                rootObjectExGUID);

                // If the root object extend GUID can be found, then the root node can be build.
                if (rootObjectExGUID != null) {
                    // If can analyze for here, then can directly capture all the GUID values related requirements
//                        if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                        {
//                            MsfsshttpdCapture.VerifyDefinedGUID(SharedContext.Current.Site);
//                        }

                    return this.BuildFromListOfObjectGroupDataElementData(objectGroupList, rootObjectExGUID.get());
                } else {
                    throw new InvalidOperationException(String.format("There is no root extended GUID value %s",
                            DataElementUtils.RootExGuid.toString()));
                }
            }

            return null;
        }

        /// <summary>
        /// This method is used to build a root node object from a byte array.
        /// </summary>
        /// <param name="fileContent">Specify the byte array.</param>
        /// <returns>Return a root node object build from the byte array.</returns>
        public IntermediateNodeObject Build(byte[] fileContent) {
            IntermediateNodeObject rootNode = new IntermediateNodeObject();
            rootNode.Signature = new SignatureObject();
            rootNode.DataSize = new DataSizeObject();
            rootNode.DataSize.DataSize = (long) fileContent.length;
            rootNode.ExGuid = new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());
            rootNode.IntermediateNodeObjectList = ChunkingFactory.CreateChunkingInstance(fileContent).Chunking();
            return rootNode;
        }

        /// <summary>
        /// This method is used to build a root node object from an object group data element list with the specified root extended GUID.
        /// </summary>
        /// <param name="objectGroupList">Specify the object group data element list.</param>
        /// <param name="rootExGuid">Specify the root extended GUID.</param>
        /// <returns>Return a root node object build from the object group data element list.</returns>
        private IntermediateNodeObject BuildFromListOfObjectGroupDataElementData(
                List<ObjectGroupDataElementData> objectGroupList, ExGuid rootExGuid) {
            AtomicReference<ObjectGroupObjectDeclare> rootDeclare = new AtomicReference<>();
            ObjectGroupObjectData root = this.FindByExGuid(objectGroupList, rootExGuid, rootDeclare);

//                if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                {
//                    MsfsshttpdCapture.VerifyObjectCount(root, SharedContext.Current.Site);
//                }

            AtomicInteger index = new AtomicInteger(0);
            AtomicReference<IntermediateNodeObject> rootNode = new AtomicReference<>();

            if (StreamObject.TryGetCurrent(ByteUtil.toByteArray(root.Data.Content), index, rootNode,
                    IntermediateNodeObject.class)) {
                rootNode.get().ExGuid = rootExGuid;

                for (ExGuid extGuid : root.ObjectExGUIDArray.Content) {
                    AtomicReference<ObjectGroupObjectDeclare> intermediateDeclare = new AtomicReference<>();
                    ObjectGroupObjectData intermediateData =
                            this.FindByExGuid(objectGroupList, extGuid, intermediateDeclare);
                    rootNode.get().IntermediateNodeObjectList.add(
                            new LeafNodeObject.IntermediateNodeObjectBuilder().Build(objectGroupList, intermediateData,
                                    extGuid));

                    // Capture the intermediate related requirements
//                        if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                        {
//                            MsfsshttpdCapture.VerifyObjectGroupObjectDataForIntermediateNode(intermediateData, intermediateDeclare, objectGroupList, SharedContext.Current.Site);
//                            MsfsshttpdCapture.VerifyLeafNodeObject(rootNode.IntermediateNodeObjectList.Last(), SharedContext.Current.Site);
//                        }
                }

//                    if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                    {
//                        // Capture the root node related requirements.
//                        MsfsshttpdCapture.VerifyObjectGroupObjectDataForRootNode(root, rootDeclare, objectGroupList, SharedContext.Current.Site);
//                        MsfsshttpdCapture.VerifyIntermediateNodeObject(rootNode, SharedContext.Current.Site);
//                    }
            } else {
                // If there is only one object in the file, SharePoint Server 2010 does not return the Root Node Object, but an Intermediate Node Object at the beginning.
                // At this case, we will add the root node object for the further parsing.
                rootNode = new AtomicReference<>(new IntermediateNodeObject());
                rootNode.get().ExGuid = rootExGuid;

                rootNode.get().IntermediateNodeObjectList.add(
                        new LeafNodeObject.IntermediateNodeObjectBuilder().Build(objectGroupList, root, rootExGuid));
                rootNode.get().DataSize = new DataSizeObject();
                rootNode.get().DataSize.DataSize = (long) rootNode.get().IntermediateNodeObjectList.stream()
                        .mapToDouble(o -> (double) o.DataSize.DataSize).sum();
            }

            // Capture all the signature related requirements.
//                if (SharedContext.Current.IsMsFsshttpRequirementsCaptured)
//                {
//                    AbstractChunking chunking = ChunkingFactory.CreateChunkingInstance(rootNode);
//
//                    if (chunking != null)
//                    {
//                        chunking.AnalyzeChunking(rootNode, SharedContext.Current.Site);
//                    }
//                }

            return rootNode.get();
        }

        /// <summary>
        /// This method is used to find the object group data element using the specified extended GUID.
        /// </summary>
        /// <param name="objectGroupList">Specify the object group data element list.</param>
        /// <param name="extendedGuid">Specify the extended GUID.</param>
        /// <param name="declare">Specify the output of ObjectGroupObjectDeclare.</param>
        /// <returns>Return the object group data element if found.</returns>
        /// <exception cref="InvalidOperationException">If not found, throw the InvalidOperationException exception.</exception>
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

            throw new InvalidOperationException("Cannot find the " + extendedGuid.guid.toString());
        }
    }
}