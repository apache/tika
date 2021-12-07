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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class RevisionStoreObjectGroup {
    public ExGuid objectGroupID;
    public List<RevisionStoreObject> objects;
    public List<EncryptionObject> encryptionObjects;

    public RevisionStoreObjectGroup(ExGuid objectGroupId) {
        this.objects = new ArrayList<>();
        this.encryptionObjects = new ArrayList<>();
        this.objectGroupID = objectGroupId;
    }

    public static RevisionStoreObjectGroup createInstance(ExGuid objectGroupId,
                                                          ObjectGroupDataElementData dataObject,
                                                          boolean isEncryption) throws IOException {
        RevisionStoreObjectGroup objectGroup = new RevisionStoreObjectGroup(objectGroupId);
        Map<ExGuid, RevisionStoreObject> objectDict = new HashMap<>();
        if (!isEncryption) {
            RevisionStoreObject revisionObject = null;
            for (int i = 0; i < dataObject.objectGroupDeclarations.objectDeclarationList.size();
                    i++) {
                ObjectGroupObjectDeclare objectDeclaration =
                        dataObject.objectGroupDeclarations.objectDeclarationList.get(i);
                ObjectGroupObjectData objectData =
                        dataObject.objectGroupData.objectGroupObjectDataList.get(i);

                if (!objectDict.containsKey(objectDeclaration.objectExtendedGUID)) {
                    revisionObject = new RevisionStoreObject();
                    revisionObject.objectGroupID = objectGroupId;
                    revisionObject.objectID = objectDeclaration.objectExtendedGUID;
                    objectDict.put(objectDeclaration.objectExtendedGUID, revisionObject);
                } else {
                    revisionObject = objectDict.get(objectDeclaration.objectExtendedGUID);
                }
                if (objectDeclaration.objectPartitionID.getDecodedValue() == 4) {
                    revisionObject.jcid = new JCIDObject(objectDeclaration, objectData);
                } else if (objectDeclaration.objectPartitionID.getDecodedValue() == 1) {
                    revisionObject.propertySet =
                            new PropertySetObject(objectDeclaration, objectData);
                    if (revisionObject.jcid.jcid.isFileData != 0) {
                        revisionObject.referencedObjectID = objectData.objectExGUIDArray;
                        revisionObject.referencedObjectSpacesID = objectData.cellIDArray;
                    }
                }
            }

            for (int i = 0; i <
                    dataObject.objectGroupDeclarations.objectGroupObjectBLOBDataDeclarationList.size();
                    i++) {
                ObjectGroupObjectBLOBDataDeclaration objectGroupObjectBLOBDataDeclaration =
                        dataObject.objectGroupDeclarations.objectGroupObjectBLOBDataDeclarationList.get(
                                i);
                ObjectGroupObjectDataBLOBReference objectGroupObjectDataBLOBReference =
                        dataObject.objectGroupData.objectGroupObjectDataBLOBReferenceList.get(i);
                if (!objectDict.containsKey(objectGroupObjectBLOBDataDeclaration.objectExGUID)) {
                    revisionObject = new RevisionStoreObject();
                    objectDict.put(objectGroupObjectBLOBDataDeclaration.objectExGUID,
                            revisionObject);
                } else {
                    revisionObject =
                            objectDict.get(objectGroupObjectBLOBDataDeclaration.objectExGUID);
                }
                if (objectGroupObjectBLOBDataDeclaration.objectPartitionID.getDecodedValue() == 2) {
                    revisionObject.fileDataObject = new FileDataObject();
                    revisionObject.fileDataObject.objectDataBLOBDeclaration =
                            objectGroupObjectBLOBDataDeclaration;
                    revisionObject.fileDataObject.objectDataBLOBReference =
                            objectGroupObjectDataBLOBReference;
                }
            }
            objectGroup.objects.addAll(objectDict.values());
        } else {
            for (int i = 0; i < dataObject.objectGroupDeclarations.objectDeclarationList.size();
                    i++) {
                ObjectGroupObjectDeclare objectDeclaration =
                        dataObject.objectGroupDeclarations.objectDeclarationList.get(i);
                ObjectGroupObjectData objectData =
                        dataObject.objectGroupData.objectGroupObjectDataList.get(i);

                if (objectDeclaration.objectPartitionID.getDecodedValue() == 1) {
                    EncryptionObject encrypObject = new EncryptionObject();
                    encrypObject.objectDeclaration = objectDeclaration;
                    encrypObject.objectData = ByteUtil.toByteArray(objectData.data.content);
                    objectGroup.encryptionObjects.add(encrypObject);
                }
            }
        }

        return objectGroup;
    }
}
