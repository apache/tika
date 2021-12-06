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

package org.apache.tika.parser.microsoft.onenote.fsshttpb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElement;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElementPackage;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestObjectGroupReferences;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestRootDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObjectGroup;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexCellMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexRevisionMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.HeaderCell;

public class MSOneStoreParser {
    private final Set<CellID> storageIndexHashTab = new HashSet<>();
    // The DataElements of Storage Index
    private List<DataElement> storageIndexDataElements;
    // The DataElements of Storage Manifest
    private List<DataElement> storageManifestDataElements;
    // The DataElements of Cell Manifest
    private List<DataElement> cellManifestDataElements;
    // The DataElements of Revision Manifest
    private List<DataElement> revisionManifestDataElements;
    // The DataElements of Object Group Data
    private List<DataElement> objectGroupDataElements;
    // The DataElements of Object BLOB
    private List<DataElement> objectBlOBElements;

    public MSOneStorePackage parse(DataElementPackage dataElementPackage) {
        MSOneStorePackage msOneStorePackage = new MSOneStorePackage();

        storageIndexDataElements = dataElementPackage.DataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.StorageIndexDataElementData)
                .collect(Collectors.toList());
        storageManifestDataElements = dataElementPackage.DataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.StorageManifestDataElementData)
                .collect(Collectors.toList());
        cellManifestDataElements = dataElementPackage.DataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.CellManifestDataElementData)
                .collect(Collectors.toList());
        revisionManifestDataElements = dataElementPackage.DataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.RevisionManifestDataElementData)
                .collect(Collectors.toList());
        objectGroupDataElements = dataElementPackage.DataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.ObjectGroupDataElementData)
                .collect(Collectors.toList());
        objectBlOBElements = dataElementPackage.DataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.ObjectDataBLOBDataElementData)
                .collect(Collectors.toList());

        msOneStorePackage.storageIndex =
                (StorageIndexDataElementData) storageIndexDataElements.get(0).data;
        msOneStorePackage.storageManifest =
                (StorageManifestDataElementData) storageManifestDataElements.get(0).data;

        // Parse Header Cell
        CellID headerCellID =
                msOneStorePackage.storageManifest.storageManifestRootDeclareList.get(0).cellID;
        StorageIndexCellMapping headerCellStorageIndexCellMapping =
                msOneStorePackage.findStorageIndexCellMapping(headerCellID);
        storageIndexHashTab.add(headerCellID);

        if (headerCellStorageIndexCellMapping != null) {
            msOneStorePackage.headerCellCellManifest =
                    this.findCellManifest(headerCellStorageIndexCellMapping.CellMappingExGuid);
            StorageIndexRevisionMapping headerCellRevisionManifestMapping =
                    msOneStorePackage.findStorageIndexRevisionMapping(
                            msOneStorePackage.headerCellCellManifest.cellManifestCurrentRevision
                                    .cellManifestCurrentRevisionExGuid);
            msOneStorePackage.headerCellRevisionManifest = this.findRevisionManifestDataElement(
                    headerCellRevisionManifestMapping.RevisionMappingExGuid);
            msOneStorePackage.headerCell =
                    this.parseHeaderCell(msOneStorePackage.headerCellRevisionManifest);

            // Parse Data root
            CellID dataRootCellID =
                    msOneStorePackage.storageManifest.storageManifestRootDeclareList.get(1).cellID;
            storageIndexHashTab.add(dataRootCellID);
            msOneStorePackage.dataRoot = this.parseObjectGroup(dataRootCellID, msOneStorePackage);
            // Parse other data
            for (StorageIndexCellMapping storageIndexCellMapping : msOneStorePackage.storageIndex
                    .storageIndexCellMappingList) {
                if (!storageIndexHashTab.contains(storageIndexCellMapping.CellID)) {
                    msOneStorePackage.OtherFileNodeList.addAll(
                            this.parseObjectGroup(storageIndexCellMapping.CellID,
                                    msOneStorePackage));
                    storageIndexHashTab.add(storageIndexCellMapping.CellID);
                }
            }
        }
        return msOneStorePackage;
    }

    /**
     * Find the CellManifestDataElementData
     *
     * @param cellMappingExtendedGUID The ExGuid of Cell Mapping Extended GUID.
     * @return The CellManifestDataElementData instance.
     */
    private CellManifestDataElementData findCellManifest(ExGuid cellMappingExtendedGUID) {
        return (CellManifestDataElementData) this.cellManifestDataElements.stream()
                .filter(d -> d.dataElementExGuid.equals(cellMappingExtendedGUID)).findFirst()
                .orElse(new DataElement()).data;
    }

    /**
     * Find the Revision Manifest from Data Elements.
     *
     * @param revisionMappingExtendedGUID The Revision Mapping Extended GUID.
     * @return Returns the instance of RevisionManifestDataElementData
     */
    private RevisionManifestDataElementData findRevisionManifestDataElement(
            ExGuid revisionMappingExtendedGUID) {
        return (RevisionManifestDataElementData) this.revisionManifestDataElements.stream()
                .filter(d -> d.dataElementExGuid.equals(revisionMappingExtendedGUID)).findFirst()
                .orElse(new DataElement()).data;
    }

    private HeaderCell parseHeaderCell(RevisionManifestDataElementData headerCellRevisionManifest) {
        ExGuid rootObjectId =
                headerCellRevisionManifest.revisionManifestObjectGroupReferences.get(
                        0).objectGroupExtendedGUID;

        DataElement element = this.objectGroupDataElements.stream()
                .filter(d -> d.dataElementExGuid.equals(rootObjectId)).findFirst()
                .orElse(new DataElement());

        return HeaderCell.createInstance((ObjectGroupDataElementData) element.data);
    }

    private List<RevisionStoreObjectGroup> parseObjectGroup(CellID objectGroupCellID,
                                                            MSOneStorePackage msOneStorePackage) {
        StorageIndexCellMapping storageIndexCellMapping =
                msOneStorePackage.findStorageIndexCellMapping(objectGroupCellID);
        CellManifestDataElementData cellManifest =
                this.findCellManifest(storageIndexCellMapping.CellMappingExGuid);
        List<RevisionStoreObjectGroup> objectGroups = new ArrayList<>();
        msOneStorePackage.cellManifests.add(cellManifest);
        StorageIndexRevisionMapping revisionMapping =
                msOneStorePackage.findStorageIndexRevisionMapping(
                        cellManifest.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid);
        RevisionManifestDataElementData revisionManifest =
                findRevisionManifestDataElement(revisionMapping.RevisionMappingExGuid);
        msOneStorePackage.revisionManifests.add(revisionManifest);
        RevisionManifestRootDeclare encryptionKeyRoot =
                revisionManifest.RevisionManifestRootDeclareList.stream()
                        .filter(r -> r.RootExGuid.equals(new ExGuid(3,
                                UUID.fromString("4A3717F8-1C14-49E7-9526-81D942DE1741"))))
                        .findFirst().orElse(null);
        boolean isEncryption = encryptionKeyRoot != null;
        for (RevisionManifestObjectGroupReferences objRef :
                revisionManifest.revisionManifestObjectGroupReferences) {
            ObjectGroupDataElementData dataObject =
                    (ObjectGroupDataElementData) objectGroupDataElements.stream()
                            .filter(d -> d.dataElementExGuid.equals(objRef.objectGroupExtendedGUID))
                            .findFirst().get().data;

            RevisionStoreObjectGroup objectGroup =
                    RevisionStoreObjectGroup.CreateInstance(objRef.objectGroupExtendedGUID,
                            dataObject, isEncryption);
            objectGroups.add(objectGroup);
        }

        return objectGroups;
    }
}
