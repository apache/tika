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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElement;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElementPackage;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestObjectGroupReferences;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestRootDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreCell;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObjectGroup;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexCellMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexRevisionMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.HeaderCell;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.GuidUtil;

public class MSOneStoreParser {
    private static final Logger LOG = LoggerFactory.getLogger(MSOneStoreParser.class);
    /**
     * The root role declaration used for the encryption key of encrypted sections.
     */
    private static final ExGuid ENCRYPTION_KEY_ROOT_EXGUID =
            new ExGuid(3, UUID.fromString("4A3717F8-1C14-49E7-9526-81D942DE1741"));
    private final Set<CellID> storageIndexHashTab = new HashSet<>();
    // The DataElements of Storage Index
    private List<DataElement> storageIndexDataElements;
    // The DataElements of Storage Manifest
    private List<DataElement> storageManifestDataElements;
    // The DataElements of Cell Manifest
    private List<DataElement> cellManifestDataElements;
    private Map<ExGuid, DataElement> cellManifestDataElementsById = new HashMap<>();
    // The DataElements of Revision Manifest
    private List<DataElement> revisionManifestDataElements;
    private Map<ExGuid, DataElement> revisionManifestDataElementsById = new HashMap<>();
    // The DataElements of Object Group Data
    private List<DataElement> objectGroupDataElements;
    // The DataElements of Object Group Data, keyed by their data element extended GUID
    private Map<ExGuid, DataElement> objectGroupDataElementsById;
    private final Map<ObjectGroupCacheKey, RevisionStoreObjectGroup> objectGroupsByDataElementId =
            new HashMap<>();
    // The DataElements of Object BLOB, keyed by their data element extended GUID
    private Map<ExGuid, DataElement> objectBlobElementsById;

    private static final class ObjectGroupCacheKey {
        private final ExGuid dataElementId;
        private final boolean encryption;

        private ObjectGroupCacheKey(ExGuid dataElementId, boolean encryption) {
            this.dataElementId = dataElementId;
            this.encryption = encryption;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ObjectGroupCacheKey)) {
                return false;
            }
            ObjectGroupCacheKey that = (ObjectGroupCacheKey) other;
            return encryption == that.encryption && Objects.equals(dataElementId, that.dataElementId);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(dataElementId) + Boolean.hashCode(encryption);
        }
    }

    public MSOneStorePackage parse(DataElementPackage dataElementPackage) throws IOException {
        MSOneStorePackage msOneStorePackage = new MSOneStorePackage();
        objectGroupsByDataElementId.clear();

        storageIndexDataElements = dataElementPackage.dataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.StorageIndexDataElementData)
                .collect(Collectors.toList());
        storageManifestDataElements = dataElementPackage.dataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.StorageManifestDataElementData)
                .collect(Collectors.toList());
        cellManifestDataElements = dataElementPackage.dataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.CellManifestDataElementData)
                .collect(Collectors.toList());
        cellManifestDataElementsById.clear();
        for (DataElement element : cellManifestDataElements) {
            cellManifestDataElementsById.putIfAbsent(element.dataElementExGuid, element);
        }
        revisionManifestDataElements = dataElementPackage.dataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.RevisionManifestDataElementData)
                .collect(Collectors.toList());
        revisionManifestDataElementsById.clear();
        for (DataElement element : revisionManifestDataElements) {
            revisionManifestDataElementsById.putIfAbsent(element.dataElementExGuid, element);
        }
        objectGroupDataElements = dataElementPackage.dataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.ObjectGroupDataElementData)
                .collect(Collectors.toList());
        objectGroupDataElementsById = new HashMap<>();
        for (DataElement objectGroupElement : objectGroupDataElements) {
            objectGroupDataElementsById.putIfAbsent(objectGroupElement.dataElementExGuid,
                    objectGroupElement);
        }
        List<DataElement> objectBlobElements = dataElementPackage.dataElements.stream()
                .filter(d -> d.dataElementType == DataElementType.ObjectDataBLOBDataElementData)
                .collect(Collectors.toList());
        objectBlobElementsById = new HashMap<>();
        for (DataElement blobElement : objectBlobElements) {
            objectBlobElementsById.putIfAbsent(blobElement.dataElementExGuid, blobElement);
        }

        if (storageIndexDataElements.isEmpty() || storageManifestDataElements.isEmpty()) {
            throw new IOException("Missing OneNote storage index or storage manifest");
        }
        msOneStorePackage.storageIndex =
                (StorageIndexDataElementData) storageIndexDataElements.get(0).data;
        msOneStorePackage.storageManifest =
                (StorageManifestDataElementData) storageManifestDataElements.get(0).data;
        if (msOneStorePackage.storageIndex == null ||
                msOneStorePackage.storageManifest == null) {
            throw new IOException("Invalid OneNote storage index or storage manifest");
        }
        if (msOneStorePackage.storageManifest.storageManifestRootDeclareList == null ||
                msOneStorePackage.storageManifest.storageManifestRootDeclareList.size() < 2) {
            throw new IOException("Missing OneNote header or data-root declaration");
        }

        // Parse Header Cell
        CellID headerCellID =
                msOneStorePackage.storageManifest.storageManifestRootDeclareList.get(0).cellID;
        StorageIndexCellMapping headerCellStorageIndexCellMapping =
                msOneStorePackage.findStorageIndexCellMapping(headerCellID);
        storageIndexHashTab.add(headerCellID);

        if (headerCellStorageIndexCellMapping != null) {
            msOneStorePackage.headerCellCellManifest =
                    this.findCellManifest(headerCellStorageIndexCellMapping.cellMappingExGuid);
            if (msOneStorePackage.headerCellCellManifest != null &&
                    msOneStorePackage.headerCellCellManifest.cellManifestCurrentRevision != null) {
                StorageIndexRevisionMapping headerCellRevisionManifestMapping =
                        msOneStorePackage.findStorageIndexRevisionMapping(
                                msOneStorePackage.headerCellCellManifest.cellManifestCurrentRevision
                                        .cellManifestCurrentRevisionExGuid);
                if (headerCellRevisionManifestMapping != null) {
                    msOneStorePackage.headerCellRevisionManifest =
                            this.findRevisionManifestDataElement(
                                    headerCellRevisionManifestMapping.revisionMappingExGuid);
                    if (msOneStorePackage.headerCellRevisionManifest != null) {
                        msOneStorePackage.headerCell =
                                this.parseHeaderCell(msOneStorePackage.headerCellRevisionManifest);
                    }
                }
            }
        }

        // Parse Data root independently of the header-cell metadata. A malformed header cell
        // should not prevent valid section cells from being parsed.
        CellID dataRootCellID =
                msOneStorePackage.storageManifest.storageManifestRootDeclareList.get(1).cellID;
        storageIndexHashTab.add(dataRootCellID);
        RevisionStoreCell dataRootCell = this.parseCell(dataRootCellID, msOneStorePackage);
        if (dataRootCell == null) {
            throw new IOException("Could not parse the OneNote data root cell");
        }
        msOneStorePackage.dataRootCell = dataRootCell;
        // Parse other data
        for (StorageIndexCellMapping storageIndexCellMapping : msOneStorePackage.storageIndex
                .storageIndexCellMappingList) {
            if (!storageIndexHashTab.contains(storageIndexCellMapping.cellID)) {
                RevisionStoreCell cell =
                        this.parseCell(storageIndexCellMapping.cellID, msOneStorePackage);
                // The storage index can retain a mapping for a deleted version context.
                // Such an entry has no CellManifestDataElementData (often its mapping GUID
                // is all zero) and therefore cannot contain current document content.
                if (cell != null) {
                    msOneStorePackage.OtherFileNodeList.addAll(cell.objectGroups);
                    msOneStorePackage.cells.add(cell);
                }
                storageIndexHashTab.add(storageIndexCellMapping.cellID);
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
        DataElement element = cellManifestDataElementsById.get(cellMappingExtendedGUID);
        return element == null ? null : (CellManifestDataElementData) element.data;
    }

    /**
     * Find the Revision Manifest from Data Elements.
     *
     * @param revisionMappingExtendedGUID The Revision Mapping Extended GUID.
     * @return Returns the instance of RevisionManifestDataElementData
     */
    private RevisionManifestDataElementData findRevisionManifestDataElement(
            ExGuid revisionMappingExtendedGUID) {
        DataElement element = revisionManifestDataElementsById.get(revisionMappingExtendedGUID);
        return element == null ? null : (RevisionManifestDataElementData) element.data;
    }

    private HeaderCell parseHeaderCell(RevisionManifestDataElementData headerCellRevisionManifest)
            throws IOException {
        if (headerCellRevisionManifest == null ||
                headerCellRevisionManifest.revisionManifestObjectGroupReferences == null ||
                headerCellRevisionManifest.revisionManifestObjectGroupReferences.isEmpty()) {
            return null;
        }
        ExGuid rootObjectId =
                headerCellRevisionManifest.revisionManifestObjectGroupReferences.get(
                        0).objectGroupExtendedGUID;

        DataElement element = objectGroupDataElementsById.get(rootObjectId);
        if (element == null) {
            return null;
        }
        return HeaderCell.createInstance((ObjectGroupDataElementData) element.data);
    }

    private RevisionStoreCell parseCell(CellID objectGroupCellID,
                                        MSOneStorePackage msOneStorePackage)
            throws IOException {
        StorageIndexCellMapping storageIndexCellMapping =
                msOneStorePackage.findStorageIndexCellMapping(objectGroupCellID);
        if (storageIndexCellMapping == null) {
            LOG.debug("Skipping OneNote cell {}: no storage-index cell mapping", objectGroupCellID);
            return null;
        }
        CellManifestDataElementData cellManifest =
                this.findCellManifest(storageIndexCellMapping.cellMappingExGuid);
        if (cellManifest == null || cellManifest.cellManifestCurrentRevision == null) {
            LOG.debug("Skipping OneNote cell {}: no current cell manifest", objectGroupCellID);
            return null;
        }
        List<RevisionStoreObjectGroup> objectGroups = new ArrayList<>();
        msOneStorePackage.cellManifests.add(cellManifest);
        StorageIndexRevisionMapping revisionMapping =
                msOneStorePackage.findStorageIndexRevisionMapping(
                        cellManifest.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid);
        if (revisionMapping == null) {
            LOG.debug("Skipping OneNote cell {}: no revision mapping", objectGroupCellID);
            return null;
        }
        RevisionManifestDataElementData revisionManifest =
                findRevisionManifestDataElement(revisionMapping.revisionMappingExGuid);
        if (revisionManifest == null || revisionManifest.revisionManifest == null) {
            LOG.debug("Skipping OneNote cell {}: no revision manifest", objectGroupCellID);
            return null;
        }

        // A revision manifest may only reference the object groups that were created or
        // modified in that revision. The remaining object groups belong to the chain of
        // base revisions (MS-FSSHTTPB "Base Revision ID"). Follow that chain and collect
        // the object groups of every revision, oldest revision first, so no content is lost.
        Deque<RevisionManifestDataElementData> revisionChain = new ArrayDeque<>();
        Set<ExGuid> seenRevisionIds = new HashSet<>();
        while (revisionManifest != null) {
            ExGuid revisionId = revisionManifest.revisionManifest.revisionID;
            if (revisionId != null && !seenRevisionIds.add(revisionId)) {
                // cycle guard - stop if we have already visited this revision
                break;
            }
            revisionChain.addFirst(revisionManifest);
            ExGuid baseRevisionId = revisionManifest.revisionManifest.baseRevisionID;
            if (baseRevisionId == null || baseRevisionId.guid == null ||
                    GuidUtil.emptyGuid().equals(baseRevisionId.guid)) {
                break;
            }
            StorageIndexRevisionMapping baseRevisionMapping =
                    msOneStorePackage.findStorageIndexRevisionMapping(baseRevisionId);
            revisionManifest = baseRevisionMapping == null ? null :
                    findRevisionManifestDataElement(baseRevisionMapping.revisionMappingExGuid);
        }

        Set<ExGuid> seenObjectGroupIds = new HashSet<>();
        // for each root role, the declaration made by the most recent revision wins
        Map<ExGuid, RevisionManifestRootDeclare> effectiveRootDeclares = new LinkedHashMap<>();
        for (RevisionManifestDataElementData manifest : revisionChain) {
            msOneStorePackage.revisionManifests.add(manifest);
            RevisionManifestRootDeclare encryptionKeyRoot =
                    manifest.revisionManifestRootDeclareList.stream()
                            .filter(r -> r.rootExGuid.equals(ENCRYPTION_KEY_ROOT_EXGUID))
                            .findFirst().orElse(null);
            boolean isEncryption = encryptionKeyRoot != null;
            for (RevisionManifestRootDeclare rootDeclare :
                    manifest.revisionManifestRootDeclareList) {
                if (!rootDeclare.rootExGuid.equals(ENCRYPTION_KEY_ROOT_EXGUID)) {
                    effectiveRootDeclares.put(rootDeclare.rootExGuid, rootDeclare);
                }
            }
            for (RevisionManifestObjectGroupReferences objRef :
                    manifest.revisionManifestObjectGroupReferences) {
                if (!seenObjectGroupIds.add(objRef.objectGroupExtendedGUID)) {
                    continue;
                }
                DataElement dataElement =
                        objectGroupDataElementsById.get(objRef.objectGroupExtendedGUID);
                if (dataElement == null) {
                    continue;
                }
                ObjectGroupDataElementData dataObject =
                        (ObjectGroupDataElementData) dataElement.data;

                ObjectGroupCacheKey cacheKey =
                        new ObjectGroupCacheKey(dataElement.dataElementExGuid, isEncryption);
                RevisionStoreObjectGroup cachedObjectGroup = objectGroupsByDataElementId.get(cacheKey);
                if (cachedObjectGroup == null) {
                    cachedObjectGroup = RevisionStoreObjectGroup.createInstance(
                            objRef.objectGroupExtendedGUID, dataObject, isEncryption,
                            objectBlobElementsById);
                    objectGroupsByDataElementId.put(cacheKey, cachedObjectGroup);
                }
                objectGroups.add(cachedObjectGroup.copy());
            }
        }

        removeSupersededObjects(objectGroups);

        RevisionStoreCell cell = new RevisionStoreCell();
        cell.cellID = objectGroupCellID;
        cell.objectGroups = objectGroups;
        cell.rootDeclares = new ArrayList<>(effectiveRootDeclares.values());
        return cell;
    }

    /**
     * An object that is modified in a later revision appears again, with the same object ID,
     * in that revision's object group. Keep only the newest version of each object, replacing
     * the older version in place so the original object ordering is preserved.
     *
     * @param objectGroups The object groups ordered from the oldest revision to the newest.
     */
    private void removeSupersededObjects(List<RevisionStoreObjectGroup> objectGroups) {
        Map<ExGuid, List<RevisionStoreObject>> containingList = new HashMap<>();
        Map<ExGuid, Integer> indexInList = new HashMap<>();
        for (RevisionStoreObjectGroup objectGroup : objectGroups) {
            List<RevisionStoreObject> objects = objectGroup.objects;
            for (int i = 0; i < objects.size(); ) {
                RevisionStoreObject object = objects.get(i);
                ExGuid objectId = object.objectID;
                if (objectId != null && containingList.containsKey(objectId)) {
                    // newer version of an already seen object - replace the older one in place
                    containingList.get(objectId).set(indexInList.get(objectId), object);
                    objects.remove(i);
                } else {
                    if (objectId != null) {
                        containingList.put(objectId, objects);
                        indexInList.put(objectId, i);
                    }
                    ++i;
                }
            }
        }
    }
}
