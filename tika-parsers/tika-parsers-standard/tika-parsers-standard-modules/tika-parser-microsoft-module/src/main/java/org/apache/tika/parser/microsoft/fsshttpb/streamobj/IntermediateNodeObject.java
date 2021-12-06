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
package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.chunking.ChunkingFactory;
import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;
import org.apache.tika.parser.microsoft.fsshttpb.util.DataElementUtils;
import org.apache.tika.parser.microsoft.fsshttpb.util.SequenceNumberGenerator;

public class IntermediateNodeObject extends NodeObject {
    /**
     * Initializes a new instance of the IntermediateNodeObject class.
     */
    public IntermediateNodeObject() {
        super(StreamObjectTypeHeaderStart.IntermediateNodeObject);
        this.IntermediateNodeObjectList = new ArrayList<>();
    }

    /**
     * Get all the content which is represented by the root node object.
     *
     * @return Return the byte list of root node object content.
     */
    @Override
    public List<Byte> GetContent() {
        List<Byte> content = new ArrayList<Byte>();

        for (LeafNodeObject intermediateNode : this.IntermediateNodeObjectList) {
            content.addAll(intermediateNode.GetContent());
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

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The Byte list
     */
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        byteList.addAll(this.Signature.SerializeToByteList());
        byteList.addAll(this.DataSize.SerializeToByteList());
        return 0;
    }

    /**
     * The class is used to build a root node object.
     */
    public static class RootNodeObjectBuilder {
        /**
         * This method is used to build a root node object from an data element list with the specified storage index extended GUID.
         *
         * @param dataElements       Specify the data element list.
         * @param storageIndexExGuid Specify the storage index extended GUID.
         * @return Return a root node object build from the data element list.
         */
        public IntermediateNodeObject BuildFromListOfDataElements(List<DataElement> dataElements,
                                                                  org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid storageIndexExGuid) {
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

        /**
         * This method is used to build a root node object from a byte array
         *
         * @param fileContent Specify the byte array.
         * @return Return a root node object build from the byte array.
         */
        public IntermediateNodeObject Build(byte[] fileContent) {
            IntermediateNodeObject rootNode = new IntermediateNodeObject();
            rootNode.Signature = new SignatureObject();
            rootNode.DataSize = new DataSizeObject();
            rootNode.DataSize.DataSize = (long) fileContent.length;
            rootNode.ExGuid = new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());
            rootNode.IntermediateNodeObjectList = ChunkingFactory.CreateChunkingInstance(fileContent).Chunking();
            return rootNode;
        }

        /**
         * This method is used to build a root node object from an object group data element list with the specified root extended GUID
         *
         * @param objectGroupList Specify the object group data element list.
         * @param rootExGuid      Specify the root extended GUID.
         * @return Return a root node object build from the object group data element list.
         */
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