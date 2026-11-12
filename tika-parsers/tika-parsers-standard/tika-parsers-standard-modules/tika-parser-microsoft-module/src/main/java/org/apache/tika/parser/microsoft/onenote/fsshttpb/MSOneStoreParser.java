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
    /** Root role declaring the encryption key of encrypted sections. */
    private static final ExGuid ENCRYPTION_KEY_ROOT_EXGUID =
            new ExGuid(3, UUID.fromString("4A3717F8-1C14-49E7-9526-81D942DE1741"));
    private final Set<CellID> storageIndexHashTab = new HashSet<>();
    // a cell manifest is walked once, however many cells map to it
    private final Map<ExGuid, CellID> parsedCellManifests = new HashMap<>();
    // likewise a current revision: another cell naming it would only repeat its content
    private final Map<ExGuid, CellID> parsedCurrentRevisions = new HashMap<>();
    private List<DataElement> storageIndexDataElements;
    private List<DataElement> storageManifestDataElements;
    private List<DataElement> cellManifestDataElements;
    private Map<ExGuid, DataElement> cellManifestDataElementsById = new HashMap<>();
    private List<DataElement> revisionManifestDataElements;
    private Map<ExGuid, DataElement> revisionManifestDataElementsById = new HashMap<>();
    private List<DataElement> objectGroupDataElements;
    private Map<ExGuid, DataElement> objectGroupDataElementsById;
    private final Map<ObjectGroupCacheKey, RevisionStoreObjectGroup> objectGroupsByDataElementId =
            new HashMap<>();
    private Map<ExGuid, DataElement> objectBlobElementsById;
    // Cells may share revisions and object groups; without a bound, N tiny cells naming one big
    // revision chain cost N times its content to build, walk and emit.
    static final long CELL_WORK_PER_UNIT = 64;
    static final long MIN_CELL_WORK = 1_000_000;
    private long cellWorkCharged;
    private long distinctCellWork;
    private boolean cellWorkExhausted;

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
        storageIndexHashTab.clear();
        parsedCellManifests.clear();
        parsedCurrentRevisions.clear();
        MSOneStorePackage msOneStorePackage = new MSOneStorePackage();
        objectGroupsByDataElementId.clear();
        cellWorkCharged = 0;
        distinctCellWork = 0;
        cellWorkExhausted = false;

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
            if (revisionManifestDataElementsById.putIfAbsent(element.dataElementExGuid,
                    element) == null) {
                distinctCellWork += manifestWork((RevisionManifestDataElementData) element.data);
            }
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

        // independent of the header cell, so a malformed one cannot block valid section cells
        CellID dataRootCellID =
                msOneStorePackage.storageManifest.storageManifestRootDeclareList.get(1).cellID;
        storageIndexHashTab.add(dataRootCellID);
        RevisionStoreCell dataRootCell = this.parseCell(dataRootCellID, msOneStorePackage);
        if (dataRootCell == null) {
            throw new IOException("Could not parse the OneNote data root cell");
        }
        msOneStorePackage.dataRootCell = dataRootCell;
        List<StorageIndexCellMapping> mappings =
                msOneStorePackage.storageIndex.storageIndexCellMappingList;
        RevisionStoreCell[] parsed = new RevisionStoreCell[mappings.size()];
        // current-context cells first, so the work budget drops old page versions, not pages
        ExGuid currentContext = dataRootCellID == null ? null : dataRootCellID.extendGUID1;
        for (boolean current : new boolean[]{true, false}) {
            for (int i = 0; i < mappings.size(); i++) {
                CellID cellID = mappings.get(i).cellID;
                boolean inCurrent = cellID != null &&
                        Objects.equals(currentContext, cellID.extendGUID1);
                if (storageIndexHashTab.contains(cellID) ||
                        current != inCurrent) {
                    continue;
                }
                // null for a deleted version context the storage index still maps (no cell
                // manifest, often an all-zero mapping GUID): no current content
                parsed[i] = this.parseCell(cellID, msOneStorePackage);
                storageIndexHashTab.add(cellID);
            }
        }
        for (RevisionStoreCell cell : parsed) {
            if (cell != null) {
                msOneStorePackage.OtherFileNodeList.addAll(cell.objectGroups);
                msOneStorePackage.cells.add(cell);
            }
        }
        return msOneStorePackage;
    }

    private CellManifestDataElementData findCellManifest(ExGuid cellMappingExtendedGUID) {
        DataElement element = cellManifestDataElementsById.get(cellMappingExtendedGUID);
        return element == null ? null : (CellManifestDataElementData) element.data;
    }

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
            String warning = "Skipping OneNote cell " + objectGroupCellID +
                    ": no storage-index cell mapping";
            msOneStorePackage.recordParseWarning(warning);
            return null;
        }
        ExGuid cellMapping = storageIndexCellMapping.cellMappingExGuid;
        if (cellMapping == null || cellMapping.guid == null ||
                GuidUtil.emptyGuid().equals(cellMapping.guid)) {
            // a deleted version context: nothing to parse, nothing wrong
            return null;
        }
        CellID owner = parsedCellManifests.putIfAbsent(cellMapping, objectGroupCellID);
        if (owner != null && !owner.equals(objectGroupCellID)) {
            msOneStorePackage.recordParseWarning("Skipping OneNote cell " + objectGroupCellID +
                    ": its cell manifest belongs to another cell");
            return null;
        }
        CellManifestDataElementData cellManifest = this.findCellManifest(cellMapping);
        if (cellManifest == null || cellManifest.cellManifestCurrentRevision == null) {
            String warning = "Skipping OneNote cell " + objectGroupCellID +
                    ": no current cell manifest";
            msOneStorePackage.recordParseWarning(warning);
            return null;
        }
        List<RevisionStoreObjectGroup> objectGroups = new ArrayList<>();
        msOneStorePackage.cellManifests.add(cellManifest);
        StorageIndexRevisionMapping revisionMapping =
                msOneStorePackage.findStorageIndexRevisionMapping(
                        cellManifest.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid);
        if (revisionMapping == null) {
            String warning = "Skipping OneNote cell " + objectGroupCellID +
                    ": no revision mapping";
            msOneStorePackage.recordParseWarning(warning);
            return null;
        }
        RevisionManifestDataElementData revisionManifest =
                findRevisionManifestDataElement(revisionMapping.revisionMappingExGuid);
        if (revisionManifest == null || revisionManifest.revisionManifest == null) {
            String warning = "Skipping OneNote cell " + objectGroupCellID +
                    ": no revision manifest";
            msOneStorePackage.recordParseWarning(warning);
            return null;
        }
        CellID revisionOwner = parsedCurrentRevisions.putIfAbsent(
                revisionMapping.revisionMappingExGuid, objectGroupCellID);
        if (revisionOwner != null && !revisionOwner.equals(objectGroupCellID)) {
            msOneStorePackage.recordParseWarning("Skipping OneNote cell " + objectGroupCellID +
                    ": its current revision belongs to another cell");
            return null;
        }

        // A revision manifest may only reference the object groups that were created or
        // modified in that revision. The remaining object groups belong to the chain of
        // base revisions (MS-FSSHTTPB "Base Revision ID"). Follow that chain and collect
        // the object groups of every revision, oldest revision first, so no content is lost.
        Deque<RevisionManifestDataElementData> revisionChain = new ArrayDeque<>();
        Set<ExGuid> seenRevisionIds = new HashSet<>();
        while (revisionManifest != null) {
            if (!chargeCellWork(1, msOneStorePackage)) {
                return null;
            }
            ExGuid revisionId = revisionManifest.revisionManifest.revisionID;
            if (revisionId != null && !seenRevisionIds.add(revisionId)) {
                // cycle guard
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
            if (!chargeCellWork(manifestWork(manifest), msOneStorePackage)) {
                return null;
            }
            RevisionManifestRootDeclare encryptionKeyRoot =
                    manifest.revisionManifestRootDeclareList.stream()
                            .filter(r -> r.rootExGuid.equals(ENCRYPTION_KEY_ROOT_EXGUID))
                            .findFirst().orElse(null);
            boolean isEncryption = encryptionKeyRoot != null;
            msOneStorePackage.encrypted |= isEncryption;
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
                    msOneStorePackage.recordParseWarning(
                            "OneNote object group " + objRef.objectGroupExtendedGUID +
                                    " could not be resolved");
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
                    distinctCellWork += cachedObjectGroup.weight;
                }
                if (!chargeCellWork(cachedObjectGroup.weight, msOneStorePackage)) {
                    return null;
                }
                objectGroups.add(cachedObjectGroup.copy());
            }
        }

        removeSupersededObjects(objectGroups);
        msOneStorePackage.revisionManifests.addAll(revisionChain);

        RevisionStoreCell cell = new RevisionStoreCell();
        cell.cellID = objectGroupCellID;
        cell.objectGroups = objectGroups;
        cell.rootDeclares = new ArrayList<>(effectiveRootDeclares.values());
        return cell;
    }

    /** One per manifest, root declare and object-group reference: the loops parseCell runs. */
    private static long manifestWork(RevisionManifestDataElementData manifest) {
        if (manifest == null) {
            return 1;
        }
        return 1L + manifest.revisionManifestRootDeclareList.size() +
                manifest.revisionManifestObjectGroupReferences.size();
    }

    private boolean chargeCellWork(long work, MSOneStorePackage msOneStorePackage) {
        if (cellWorkExhausted) {
            return false;
        }
        cellWorkCharged += work;
        if (cellWorkCharged > MIN_CELL_WORK + CELL_WORK_PER_UNIT * distinctCellWork) {
            cellWorkExhausted = true;
            msOneStorePackage.recordParseWarning(
                    "OneNote cells exceeded the shared-revision work budget; skipping the rest");
            return false;
        }
        return true;
    }

    /**
     * An object modified in a later revision reappears, with the same object ID, in that
     * revision's object group. Keeps only the newest version, replacing the older one in place
     * to preserve ordering. objectGroups must be ordered oldest revision first.
     */
    private void removeSupersededObjects(List<RevisionStoreObjectGroup> objectGroups) {
        Map<ExGuid, List<RevisionStoreObject>> containingList = new HashMap<>();
        Map<ExGuid, Integer> indexInList = new HashMap<>();
        List<List<RevisionStoreObject>> keptLists = new ArrayList<>(objectGroups.size());
        for (RevisionStoreObjectGroup objectGroup : objectGroups) {
            List<RevisionStoreObject> kept = new ArrayList<>(objectGroup.objects.size());
            keptLists.add(kept);
            for (RevisionStoreObject object : objectGroup.objects) {
                ExGuid objectId = object.objectID;
                if (objectId != null && containingList.containsKey(objectId)) {
                    containingList.get(objectId).set(indexInList.get(objectId), object);
                } else {
                    if (objectId != null) {
                        containingList.put(objectId, kept);
                        indexInList.put(objectId, kept.size());
                    }
                    kept.add(object);
                }
            }
        }
        for (int g = 0; g < objectGroups.size(); g++) {
            objectGroups.get(g).objects.clear();
            objectGroups.get(g).objects.addAll(keptLists.get(g));
        }
    }
}
