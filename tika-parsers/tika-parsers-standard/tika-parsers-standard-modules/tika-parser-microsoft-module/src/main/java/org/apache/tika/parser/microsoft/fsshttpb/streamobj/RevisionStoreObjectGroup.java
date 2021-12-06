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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.fsshttpb.util.ByteUtil;

public class RevisionStoreObjectGroup {
    public RevisionStoreObjectGroup(ExGuid objectGroupId) {
        this.Objects = new ArrayList<>();
        this.EncryptionObjects = new ArrayList<>();
        this.ObjectGroupID = objectGroupId;
    }

    public ExGuid ObjectGroupID;
    public List<RevisionStoreObject> Objects;
    public List<EncryptionObject> EncryptionObjects;

    public static RevisionStoreObjectGroup CreateInstance(ExGuid objectGroupId, ObjectGroupDataElementData dataObject,
                                                          boolean isEncryption) {
        RevisionStoreObjectGroup objectGroup = new RevisionStoreObjectGroup(objectGroupId);
        Map<ExGuid, RevisionStoreObject> objectDict = new HashMap<>();
        if (!isEncryption) {
            RevisionStoreObject revisionObject = null;
            for (int i = 0; i < dataObject.ObjectGroupDeclarations.ObjectDeclarationList.size(); i++) {
                ObjectGroupObjectDeclare objectDeclaration =
                        dataObject.ObjectGroupDeclarations.ObjectDeclarationList.get(i);
                ObjectGroupObjectData objectData = dataObject.ObjectGroupData.ObjectGroupObjectDataList.get(i);

                if (!objectDict.containsKey(objectDeclaration.ObjectExtendedGUID)) {
                    revisionObject = new RevisionStoreObject();
                    revisionObject.ObjectGroupID = objectGroupId;
                    revisionObject.ObjectID = objectDeclaration.ObjectExtendedGUID;
                    objectDict.put(objectDeclaration.ObjectExtendedGUID, revisionObject);
                } else {
                    revisionObject = objectDict.get(objectDeclaration.ObjectExtendedGUID);
                }
                if (objectDeclaration.ObjectPartitionID.getDecodedValue() == 4) {
                    revisionObject.JCID = new JCIDObject(objectDeclaration, objectData);
                } else if (objectDeclaration.ObjectPartitionID.getDecodedValue() == 1) {
                    revisionObject.PropertySet = new PropertySetObject(objectDeclaration, objectData);
                    if (revisionObject.JCID.JCID.IsFileData != 0) {
                        revisionObject.ReferencedObjectID = objectData.ObjectExGUIDArray;
                        revisionObject.ReferencedObjectSpacesID = objectData.cellIDArray;
                    }
                }
            }

            for (int i = 0; i < dataObject.ObjectGroupDeclarations.ObjectGroupObjectBLOBDataDeclarationList.size();
                 i++) {
                ObjectGroupObjectBLOBDataDeclaration objectGroupObjectBLOBDataDeclaration =
                        dataObject.ObjectGroupDeclarations.ObjectGroupObjectBLOBDataDeclarationList.get(i);
                ObjectGroupObjectDataBLOBReference objectGroupObjectDataBLOBReference =
                        dataObject.ObjectGroupData.ObjectGroupObjectDataBLOBReferenceList.get(i);
                if (!objectDict.containsKey(objectGroupObjectBLOBDataDeclaration.ObjectExGUID)) {
                    revisionObject = new RevisionStoreObject();
                    objectDict.put(objectGroupObjectBLOBDataDeclaration.ObjectExGUID, revisionObject);
                } else {
                    revisionObject = objectDict.get(objectGroupObjectBLOBDataDeclaration.ObjectExGUID);
                }
                if (objectGroupObjectBLOBDataDeclaration.ObjectPartitionID.getDecodedValue() == 2) {
                    revisionObject.FileDataObject = new FileDataObject();
                    revisionObject.FileDataObject.ObjectDataBLOBDeclaration = objectGroupObjectBLOBDataDeclaration;
                    revisionObject.FileDataObject.ObjectDataBLOBReference = objectGroupObjectDataBLOBReference;
                }
            }
            objectGroup.Objects.addAll(objectDict.values());
        } else {
            for (int i = 0; i < dataObject.ObjectGroupDeclarations.ObjectDeclarationList.size(); i++) {
                ObjectGroupObjectDeclare objectDeclaration =
                        dataObject.ObjectGroupDeclarations.ObjectDeclarationList.get(i);
                ObjectGroupObjectData objectData = dataObject.ObjectGroupData.ObjectGroupObjectDataList.get(i);

                if (objectDeclaration.ObjectPartitionID.getDecodedValue() == 1) {
                    EncryptionObject encrypObject = new EncryptionObject();
                    encrypObject.ObjectDeclaration = objectDeclaration;
                    encrypObject.ObjectData = ByteUtil.toByteArray(objectData.Data.Content);
                    objectGroup.EncryptionObjects.add(encrypObject);
                }
            }
        }

        return objectGroup;
    }
}
