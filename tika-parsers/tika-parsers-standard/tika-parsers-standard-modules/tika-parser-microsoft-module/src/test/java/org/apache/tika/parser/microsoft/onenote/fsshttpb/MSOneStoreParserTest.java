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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.onenote.OneNoteTreeWalkerOptions;
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
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class MSOneStoreParserTest {

    @Test
    public void testStorageMappingIndexes() {
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.storageIndex = new StorageIndexDataElementData();
        CellID cellID = cell(700);
        StorageIndexCellMapping cellMapping = new StorageIndexCellMapping();
        cellMapping.cellID = cellID;
        pkg.storageIndex.storageIndexCellMappingList.add(cellMapping);

        ExGuid revisionID = id(701);
        StorageIndexRevisionMapping revisionMapping = new StorageIndexRevisionMapping();
        revisionMapping.revisionExGuid = revisionID;
        pkg.storageIndex.storageIndexRevisionMappingList.add(revisionMapping);

        assertSame(cellMapping, pkg.findStorageIndexCellMapping(cellID));
        assertSame(revisionMapping, pkg.findStorageIndexRevisionMapping(revisionID));

        StorageIndexCellMapping addedAfterIndexing = new StorageIndexCellMapping();
        addedAfterIndexing.cellID = cell(702);
        pkg.storageIndex.storageIndexCellMappingList.add(addedAfterIndexing);
        assertNull(pkg.findStorageIndexCellMapping(addedAfterIndexing.cellID));
    }

    @Test
    public void testMissingRootsAndRevisionMappingsReturnNoCell() throws Exception {
        CellID cellID = cell(1);
        ExGuid cellMappingID = id(2);
        ExGuid missingRevisionID = id(3);
        ExGuid revisionMappingID = id(4);

        MSOneStoreParser noMappingParser = parserWithEmptyIndexes();
        assertParseWarning(noMappingParser, new MSOneStorePackage(), cellID,
                "no storage-index cell mapping");

        MSOneStoreParser noManifestParser = parserWithEmptyIndexes();
        MSOneStorePackage noManifestPackage = packageWithCellMapping(cellID, cellMappingID);
        assertParseWarning(noManifestParser, noManifestPackage, cellID,
                "no current cell manifest");

        MSOneStoreParser noCurrentParser = parserWithEmptyIndexes();
        DataElement currentlessManifest = cellManifest(cellMappingID, null);
        set(noCurrentParser, "cellManifestDataElements", Arrays.asList(currentlessManifest));
        set(noCurrentParser, "cellManifestDataElementsById",
                Collections.singletonMap(cellMappingID, currentlessManifest));
        assertParseWarning(noCurrentParser,
                packageWithCellMapping(cellID, cellMappingID), cellID,
                "no current cell manifest");

        MSOneStoreParser noRevisionMappingParser = parserWithEmptyIndexes();
        DataElement manifest = cellManifest(cellMappingID, missingRevisionID);
        set(noRevisionMappingParser, "cellManifestDataElements", Arrays.asList(manifest));
        set(noRevisionMappingParser, "cellManifestDataElementsById",
                Collections.singletonMap(cellMappingID, manifest));
        assertParseWarning(noRevisionMappingParser,
                packageWithCellMapping(cellID, cellMappingID), cellID, "no revision mapping");

        MSOneStoreParser noRevisionManifestParser = parserWithEmptyIndexes();
        set(noRevisionManifestParser, "cellManifestDataElements", Arrays.asList(manifest));
        set(noRevisionManifestParser, "cellManifestDataElementsById",
                Collections.singletonMap(cellMappingID, manifest));
        MSOneStorePackage noRevisionManifestPackage = packageWithCellMapping(cellID,
                cellMappingID);
        StorageIndexRevisionMapping revisionMapping = new StorageIndexRevisionMapping();
        revisionMapping.revisionExGuid = missingRevisionID;
        revisionMapping.revisionMappingExGuid = revisionMappingID;
        noRevisionManifestPackage.storageIndex.storageIndexRevisionMappingList.add(revisionMapping);
        assertParseWarning(noRevisionManifestParser, noRevisionManifestPackage, cellID,
                "no revision manifest");
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

    private static MSOneStoreParser parserWithEmptyIndexes() throws Exception {
        MSOneStoreParser parser = new MSOneStoreParser();
        set(parser, "cellManifestDataElements", new java.util.ArrayList<>());
        set(parser, "revisionManifestDataElements", new java.util.ArrayList<>());
        set(parser, "objectGroupDataElements", new java.util.ArrayList<>());
        set(parser, "objectGroupDataElementsById", new java.util.HashMap<>());
        set(parser, "objectBlobElementsById", new java.util.HashMap<>());
        return parser;
    }

    private static MSOneStorePackage packageWithCellMapping(CellID cellID, ExGuid mappingID) {
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.storageIndex = new StorageIndexDataElementData();
        StorageIndexCellMapping mapping = new StorageIndexCellMapping();
        mapping.cellID = cellID;
        mapping.cellMappingExGuid = mappingID;
        pkg.storageIndex.storageIndexCellMappingList.add(mapping);
        return pkg;
    }

    private static DataElement cellManifest(ExGuid mappingID, ExGuid revisionID) {
        DataElement element = new DataElement();
        element.dataElementExGuid = mappingID;
        CellManifestDataElementData data = new CellManifestDataElementData();
        data.cellManifestCurrentRevision = null;
        if (revisionID != null) {
            data.cellManifestCurrentRevision = new CellManifestCurrentRevision();
            data.cellManifestCurrentRevision.cellManifestCurrentRevisionExGuid = revisionID;
        }
        element.data = data;
        return element;
    }

    private static void assertParseWarning(MSOneStoreParser parser, MSOneStorePackage pkg,
                                           CellID cellID, String expected) throws Exception {
        assertNull(parseCell(parser, cellID, pkg));
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new ToTextContentHandler(), metadata);
        xhtml.startDocument();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata, xhtml, context);
        xhtml.endDocument();
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains(expected)), expected);
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
