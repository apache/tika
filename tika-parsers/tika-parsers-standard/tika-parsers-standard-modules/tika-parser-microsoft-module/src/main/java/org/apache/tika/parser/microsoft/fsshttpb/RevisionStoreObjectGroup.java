package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevisionStoreObjectGroup {
    public RevisionStoreObjectGroup(ExGuid objectGroupId)
    {
        this.Objects = new ArrayList<>();
        this.EncryptionObjects = new ArrayList<>();
        this.ObjectGroupID = objectGroupId;
    }
    /// <summary>
    /// Gets or sets the revision store object group identifier.
    /// </summary>
    public ExGuid ObjectGroupID;
    /// <summary>
    /// Gets or sets the Objects in object group.
    /// </summary>
    public List<RevisionStoreObject> Objects;
    /// <summary>
    /// Gets or sets the encryption objects.
    /// </summary>
    public List<EncryptionObject> EncryptionObjects;

    public static RevisionStoreObjectGroup CreateInstance(ExGuid objectGroupId, ObjectGroupDataElementData dataObject, boolean isEncryption)
    {
        RevisionStoreObjectGroup objectGroup = new RevisionStoreObjectGroup(objectGroupId);
        Map<ExGuid, RevisionStoreObject> objectDict = new HashMap<>();
        if (!isEncryption)
        {
            RevisionStoreObject revisionObject = null;
            for (int i = 0; i < dataObject.ObjectGroupDeclarations.ObjectDeclarationList.size(); i++)
            {
                ObjectGroupObjectDeclare objectDeclaration = dataObject.ObjectGroupDeclarations.ObjectDeclarationList.get(i);
                ObjectGroupObjectData objectData = dataObject.ObjectGroupData.ObjectGroupObjectDataList.get(i);

                if (!objectDict.containsKey(objectDeclaration.ObjectExtendedGUID))
                {
                    revisionObject = new RevisionStoreObject();
                    revisionObject.ObjectGroupID = objectGroupId;
                    revisionObject.ObjectID = objectDeclaration.ObjectExtendedGUID;
                    objectDict.put(objectDeclaration.ObjectExtendedGUID, revisionObject);
                }
                else
                {
                    revisionObject = objectDict.get(objectDeclaration.ObjectExtendedGUID);
                }
                if (objectDeclaration.ObjectPartitionID.getDecodedValue() == 4)
                {
                    revisionObject.JCID = new JCIDObject(objectDeclaration, objectData);
                }
                else if (objectDeclaration.ObjectPartitionID.getDecodedValue() == 1)
                {
                    revisionObject.PropertySet = new PropertySetObject(objectDeclaration, objectData);
                    if (revisionObject.JCID.JCID.IsFileData != 0)
                    {
                        revisionObject.ReferencedObjectID = objectData.ObjectExGUIDArray;
                        revisionObject.ReferencedObjectSpacesID = objectData.cellIDArray;
                    }
                }
            }

            for (int i = 0; i < dataObject.ObjectGroupDeclarations.ObjectGroupObjectBLOBDataDeclarationList.size(); i++)
            {
                ObjectGroupObjectBLOBDataDeclaration objectGroupObjectBLOBDataDeclaration = dataObject.ObjectGroupDeclarations.ObjectGroupObjectBLOBDataDeclarationList.get(i);
                ObjectGroupObjectDataBLOBReference objectGroupObjectDataBLOBReference = dataObject.ObjectGroupData.ObjectGroupObjectDataBLOBReferenceList.get(i);
                if (!objectDict.containsKey(objectGroupObjectBLOBDataDeclaration.ObjectExGUID))
                {
                    revisionObject = new RevisionStoreObject();
                    objectDict.put(objectGroupObjectBLOBDataDeclaration.ObjectExGUID, revisionObject);
                }
                else
                {
                    revisionObject = objectDict.get(objectGroupObjectBLOBDataDeclaration.ObjectExGUID);
                }
                if (objectGroupObjectBLOBDataDeclaration.ObjectPartitionID.getDecodedValue() == 2)
                {
                    revisionObject.FileDataObject = new FileDataObject();
                    revisionObject.FileDataObject.ObjectDataBLOBDeclaration = objectGroupObjectBLOBDataDeclaration;
                    revisionObject.FileDataObject.ObjectDataBLOBReference = objectGroupObjectDataBLOBReference;
                }
            }
            objectGroup.Objects.addAll(objectDict.values());
        }
        else
        {
            for (int i = 0; i < dataObject.ObjectGroupDeclarations.ObjectDeclarationList.size(); i++)
            {
                ObjectGroupObjectDeclare objectDeclaration = dataObject.ObjectGroupDeclarations.ObjectDeclarationList.get(i);
                ObjectGroupObjectData objectData = dataObject.ObjectGroupData.ObjectGroupObjectDataList.get(i);

                if(objectDeclaration.ObjectPartitionID.getDecodedValue()==1)
                {
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
