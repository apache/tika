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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestCurrentRevision;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElement;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestObjectGroupReferences;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreCell;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexCellMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexRevisionMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;

public class MSOneStoreParserTest {

    @Test
    public void testStorageMappingIndexesSeePublicListUpdates() {
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.storageIndex = new StorageIndexDataElementData();
        CellID cellID = cell(700);
        assertNull(pkg.findStorageIndexCellMapping(cellID));
        StorageIndexCellMapping cellMapping = new StorageIndexCellMapping();
        cellMapping.cellID = cellID;
        pkg.storageIndex.storageIndexCellMappingList.add(cellMapping);
        assertSame(cellMapping, pkg.findStorageIndexCellMapping(cellID));
        StorageIndexCellMapping replacement = new StorageIndexCellMapping();
        replacement.cellID = cellID;
        replacement.cellMappingExGuid = id(702);
        pkg.storageIndex.storageIndexCellMappingList.set(0, replacement);
        assertSame(replacement, pkg.findStorageIndexCellMapping(cellID));

        ExGuid revisionID = id(701);
        assertNull(pkg.findStorageIndexRevisionMapping(revisionID));
        StorageIndexRevisionMapping revisionMapping = new StorageIndexRevisionMapping();
        revisionMapping.revisionExGuid = revisionID;
        pkg.storageIndex.storageIndexRevisionMappingList.add(revisionMapping);
        assertSame(revisionMapping, pkg.findStorageIndexRevisionMapping(revisionID));
    }

    @Test
    public void testMissingRootsAndRevisionMappingsReturnNoCell() throws Exception {
        MSOneStoreParser parser = new MSOneStoreParser();
        set(parser, "cellManifestDataElements", new java.util.ArrayList<>());
        set(parser, "revisionManifestDataElements", new java.util.ArrayList<>());
        set(parser, "objectGroupDataElements", new java.util.ArrayList<>());
        set(parser, "objectGroupDataElementsById", new java.util.HashMap<>());
        set(parser, "objectBlobElementsById", new java.util.HashMap<>());
        MSOneStorePackage pkg = new MSOneStorePackage();
        CellID cellID = cell(1);

        assertNull(parseCell(parser, cellID, pkg));

        pkg.storageIndex = new StorageIndexDataElementData();
        StorageIndexCellMapping cellMapping = new StorageIndexCellMapping();
        cellMapping.cellID = cellID;
        cellMapping.cellMappingExGuid = id(2);
        pkg.storageIndex.storageIndexCellMappingList.add(cellMapping);
        assertNull(parseCell(parser, cellID, pkg));

        DataElement cellManifestElement = new DataElement();
        cellManifestElement.dataElementExGuid = cellMapping.cellMappingExGuid;
        cellManifestElement.data = new CellManifestDataElementData();
        set(parser, "cellManifestDataElements", Arrays.asList(cellManifestElement));
        set(parser, "cellManifestDataElementsById",
                Collections.singletonMap(cellManifestElement.dataElementExGuid,
                        cellManifestElement));
        assertNull(parseCell(parser, cellID, pkg));

        CellManifestDataElementData cellManifest = (CellManifestDataElementData)
                cellManifestElement.data;
        cellManifest.cellManifestCurrentRevision = null;
        assertNull(parseCell(parser, cellID, pkg));
        cellManifest.cellManifestCurrentRevision = new CellManifestCurrentRevision();
        ExGuid missingRevisionID = id(3);
        cellManifest.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid =
                missingRevisionID;
        StorageIndexRevisionMapping missingManifestMapping = new StorageIndexRevisionMapping();
        missingManifestMapping.revisionExGuid = missingRevisionID;
        missingManifestMapping.revisionMappingExGuid = id(4);
        pkg.storageIndex.storageIndexRevisionMappingList.add(missingManifestMapping);
        assertNull(parseCell(parser, cellID, pkg));
    }

    @Test
    public void testRevisionChainStopsCyclesAndDeduplicatesObjectGroups() throws Exception {
        MSOneStoreParser parser = new MSOneStoreParser();
        set(parser, "cellManifestDataElements", new java.util.ArrayList<>());
        set(parser, "revisionManifestDataElements", new java.util.ArrayList<>());
        set(parser, "objectGroupDataElements", new java.util.ArrayList<>());
        set(parser, "objectGroupDataElementsById", new java.util.HashMap<>());
        set(parser, "objectBlobElementsById", new java.util.HashMap<>());

        CellID cellID = cell(20);
        ExGuid cellMappingID = id(21);
        ExGuid currentMappingID = id(22);
        ExGuid oldMappingID = id(23);
        ExGuid currentRevisionID = id(24);
        ExGuid oldRevisionID = id(25);
        ExGuid currentGroupID = id(26);
        ExGuid oldGroupID = id(27);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.storageIndex = new StorageIndexDataElementData();
        StorageIndexCellMapping cellMapping = new StorageIndexCellMapping();
        cellMapping.cellID = cellID;
        cellMapping.cellMappingExGuid = cellMappingID;
        pkg.storageIndex.storageIndexCellMappingList.add(cellMapping);
        StorageIndexRevisionMapping currentMapping = new StorageIndexRevisionMapping();
        currentMapping.revisionMappingExGuid = currentMappingID;
        currentMapping.revisionExGuid = currentRevisionID;
        StorageIndexRevisionMapping oldMapping = new StorageIndexRevisionMapping();
        oldMapping.revisionMappingExGuid = oldMappingID;
        oldMapping.revisionExGuid = oldRevisionID;
        pkg.storageIndex.storageIndexRevisionMappingList.add(currentMapping);
        pkg.storageIndex.storageIndexRevisionMappingList.add(oldMapping);

        DataElement cellManifestElement = new DataElement();
        cellManifestElement.dataElementExGuid = cellMappingID;
        CellManifestDataElementData cellManifest = new CellManifestDataElementData();
        cellManifest.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid = currentRevisionID;
        cellManifestElement.data = cellManifest;
        set(parser, "cellManifestDataElements", Arrays.asList(cellManifestElement));
        set(parser, "cellManifestDataElementsById",
                Collections.singletonMap(cellManifestElement.dataElementExGuid,
                        cellManifestElement));

        RevisionManifestDataElementData current = revision(currentRevisionID, oldRevisionID,
                currentGroupID, currentGroupID, id(99));
        RevisionManifestDataElementData old = revision(oldRevisionID, currentRevisionID,
                oldGroupID);
        DataElement currentElement = new DataElement();
        currentElement.dataElementExGuid = currentMappingID;
        currentElement.data = current;
        DataElement oldElement = new DataElement();
        oldElement.dataElementExGuid = oldMappingID;
        oldElement.data = old;
        set(parser, "revisionManifestDataElements", Arrays.asList(currentElement, oldElement));
        Map<ExGuid, DataElement> revisionManifests = new HashMap<>();
        revisionManifests.put(currentElement.dataElementExGuid, currentElement);
        revisionManifests.put(oldElement.dataElementExGuid, oldElement);
        set(parser, "revisionManifestDataElementsById", revisionManifests);

        DataElement currentGroup = new DataElement();
        currentGroup.dataElementExGuid = currentGroupID;
        currentGroup.data = new ObjectGroupDataElementData();
        DataElement oldGroup = new DataElement();
        oldGroup.dataElementExGuid = oldGroupID;
        oldGroup.data = new ObjectGroupDataElementData();
        set(parser, "objectGroupDataElements", Arrays.asList(currentGroup, oldGroup));
        java.util.Map<ExGuid, DataElement> objectGroups = new java.util.HashMap<>();
        objectGroups.put(currentGroupID, currentGroup);
        objectGroups.put(oldGroupID, oldGroup);
        set(parser, "objectGroupDataElementsById", objectGroups);

        RevisionStoreCell result = parseCell(parser, cellID, pkg);
        assertEquals(2, result.objectGroups.size());

        old.revisionManifest.baseRevisionID = id(1001);
        RevisionStoreCell missingBaseResult = parseCell(parser, cellID, pkg);
        assertEquals(2, missingBaseResult.objectGroups.size());
    }

    private static RevisionManifestDataElementData revision(ExGuid revisionID,
                                                            ExGuid baseRevisionID,
                                                            ExGuid... groups) {
        RevisionManifestDataElementData data = new RevisionManifestDataElementData();
        data.revisionManifest.revisionID = revisionID;
        data.revisionManifest.baseRevisionID = baseRevisionID;
        for (ExGuid group : groups) {
            RevisionManifestObjectGroupReferences reference =
                    new RevisionManifestObjectGroupReferences();
            reference.objectGroupExtendedGUID = group;
            data.revisionManifestObjectGroupReferences.add(reference);
        }
        return data;
    }

    private static RevisionStoreCell parseCell(MSOneStoreParser parser, CellID cellID,
                                               MSOneStorePackage pkg) throws Exception {
        Method method = MSOneStoreParser.class.getDeclaredMethod("parseCell", CellID.class,
                MSOneStorePackage.class);
        method.setAccessible(true);
        return (RevisionStoreCell) method.invoke(parser, cellID, pkg);
    }

    private static void set(MSOneStoreParser parser, String field, Object value) throws Exception {
        java.lang.reflect.Field declared = MSOneStoreParser.class.getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(parser, value);
    }

    private static CellID cell(int value) {
        return new CellID(id(value), id(value + 1));
    }

    private static ExGuid id(int value) {
        return new ExGuid(value, UUID.nameUUIDFromBytes(
                ("parser-" + value).getBytes(StandardCharsets.UTF_8)));
    }
}
