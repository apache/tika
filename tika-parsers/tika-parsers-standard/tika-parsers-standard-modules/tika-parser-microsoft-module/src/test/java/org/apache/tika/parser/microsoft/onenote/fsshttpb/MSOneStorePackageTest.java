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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OneNote;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.onenote.OneNoteTreeWalkerOptions;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.ArrayNumber;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.FourBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.IProperty;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.NoData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtArrayOfPropertyValues;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElement;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.FileDataObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.JCIDObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectDataBLOB;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectDataBLOBDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.ObjectGroupObjectData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySetObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestRootDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreCell;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObjectGroup;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectPropSet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class MSOneStorePackageTest {

    @Test
    public void testTableAndListObjectsRetainXhtmlStructure() throws Exception {
        ExGuid tableId = id(1100);
        ExGuid rowId = id(1101);
        ExGuid cellId = id(1102);
        ExGuid listId = id(1103);
        RevisionStoreObject tableCell = object(cellId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("table text"))), Collections.emptyList(),
                Collections.emptyList());
        RevisionStoreObject row = object(rowId,
                propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                        arrayNumber(1))), Collections.singletonList(cellId), Collections.emptyList());
        RevisionStoreObject table = object(tableId,
                propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                        arrayNumber(1))), Collections.singletonList(rowId), Collections.emptyList());
        ExGuid bulletListId = id(1104);
        RevisionStoreObject bulletList = numberListNode(bulletListId,
                new byte[] {0x01, 0x00, 0x22, 0x20}, null);
        RevisionStoreObject listItem = listItem(listId, bulletListId, "list text");
        setJcid(table, OneNoteJcid.TABLE_NODE);
        setJcid(row, OneNoteJcid.TABLE_ROW_NODE);
        setJcid(tableCell, OneNoteJcid.TABLE_CELL_NODE);

        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(table, row, tableCell, listItem, bulletList));
        cell.rootDeclares.add(rootDeclare(tableId));
        cell.rootDeclares.add(rootDeclare(listId));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        String xml = walkXml(pkg);
        assertTrue(xml.contains("<table>"));
        assertTrue(xml.contains("<tr>"));
        assertTrue(xml.contains("<td>"));
        assertTrue(xml.contains("table text"));
        assertTrue(xml.contains("<ul>"));
        assertTrue(xml.contains("<li>"));
        assertTrue(xml.contains("list text"));
    }

    @Test
    public void testNumberedListItemsShareOrderedListMarkup() throws Exception {
        ExGuid outlineId = id(1150);
        ExGuid firstItemId = id(1151);
        ExGuid secondItemId = id(1152);
        ExGuid firstNumberListId = id(1153);
        ExGuid secondNumberListId = id(1154);
        byte[] numberFormat = new byte[] {0x02, 0x00, (byte) 0xfd, (byte) 0xff, 0x01, 0x00};

        RevisionStoreObject firstNumberList = numberListNode(firstNumberListId, numberFormat, null);
        RevisionStoreObject secondNumberList = numberListNode(secondNumberListId, numberFormat, 5);
        RevisionStoreObject firstItem = listItem(firstItemId, firstNumberListId,
                "first numbered item");
        RevisionStoreObject secondItem = listItem(secondItemId, secondNumberListId,
                "second numbered item");
        RevisionStoreObject outline = object(outlineId,
                propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                        arrayNumber(2))), Arrays.asList(firstItemId, secondItemId),
                Collections.emptyList());
        setJcid(outline, OneNoteJcid.OUTLINE_NODE);

        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(outline, firstItem, secondItem, firstNumberList,
                secondNumberList));
        cell.rootDeclares.add(rootDeclare(outlineId));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        String xml = walkXml(pkg);
        assertEquals(1, countOccurrences(xml, "<ol type=\"I\">"), xml);
        assertEquals(1, countOccurrences(xml, "<li value=\"5\">"), xml);
        assertEquals(2, countOccurrences(xml, "<li"));
        assertTrue(xml.contains("first numbered item"));
        assertTrue(xml.contains("second numbered item"));
        assertFalse(xml.contains("<ul>"));
    }

    @Test
    public void testNumberListLengthPrefixIsNotNumberingMarker() throws Exception {
        ExGuid listId = id(1160);
        ExGuid numberListId = id(1161);
        byte[] numberFormat = new byte[(0xfffd + 1) * 2];
        numberFormat[0] = (byte) 0xfd;
        numberFormat[1] = (byte) 0xff;
        for (int i = 2; i < numberFormat.length; i += 2) {
            numberFormat[i] = 0x2e;
        }
        RevisionStoreObject numberList = numberListNode(numberListId, numberFormat, null);
        RevisionStoreObject listItem = listItem(listId, numberListId, "bullet item");
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(listItem, numberList));
        cell.rootDeclares.add(rootDeclare(listId));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        String xml = walkXml(pkg);

        assertTrue(xml.contains("<ul>"), xml);
        assertFalse(xml.contains("<ol"), xml);
    }

    @Test
    public void testNumberListFormatsMapHtmlTypesAndRejectMalformedValues() throws Exception {
        String[] types = {"1", "I", "i", "A", "a"};
        for (int code = 0; code < types.length; code++) {
            String xml = singleListXml(1200 + code * 10, numberListFormat(code), null);
            assertTrue(xml.contains("<ol type=\"" + types[code] + "\">"), xml);
        }

        String unknown = singleListXml(1260, numberListFormat(5), null);
        assertTrue(unknown.contains("<ol>"), unknown);
        assertFalse(unknown.contains("<ol type="), unknown);

        byte[][] malformedFormats = {null, {0}, {1, 0, 0}, {3, 0, (byte) 0xfd, (byte) 0xff,
                1, 0}, {1, 0, (byte) 0xfd, (byte) 0xff}, {1, 0, 0x2e, 0}};
        for (int i = 0; i < malformedFormats.length; i++) {
            String xml = singleListXml(1270 + i * 10, malformedFormats[i], null);
            assertTrue(xml.contains("<ul>"), xml);
            assertFalse(xml.contains("<ol"), xml);
        }
    }

    @Test
    public void testMalformedListFormatsDoNotShareUnorderedList() throws Exception {
        ExGuid outlineId = id(1320);
        ExGuid firstItemId = id(1321);
        ExGuid secondItemId = id(1322);
        byte[] firstMalformedFormat = {3, 0, (byte) 0xfd, (byte) 0xff, 1, 0};
        byte[] secondMalformedFormat = {4, 0, (byte) 0xfd, (byte) 0xff, 1, 0};
        RevisionStoreObject firstNumberList = numberListNode(id(1323), firstMalformedFormat,
                null);
        RevisionStoreObject secondNumberList = numberListNode(id(1324), secondMalformedFormat,
                null);
        List<ExGuid> itemIds = Arrays.asList(firstItemId, secondItemId);
        List<RevisionStoreObject> items = Arrays.asList(
                listItem(firstItemId, firstNumberList.objectID, "first malformed item"),
                listItem(secondItemId, secondNumberList.objectID, "second malformed item"));

        String xml = walkListItems(outlineId, itemIds, items,
                Arrays.asList(firstNumberList, secondNumberList), "");

        assertEquals(2, countOccurrences(xml, "<ul>"), xml);
        assertEquals(2, countOccurrences(xml, "<li>"), xml);
        assertFalse(xml.contains("<ol"), xml);
    }

    @Test
    public void testListStylesSplitOnFormatAndIndentAndShareWhenEqual() throws Exception {
        int seed = 1350;
        ExGuid outlineId = id(seed);
        RevisionStoreObject upperRoman = numberListNode(id(seed + 30), numberListFormat(1), null);
        RevisionStoreObject lowerRoman = numberListNode(id(seed + 31), numberListFormat(2), null);
        byte[] lowerRomanWithSuffix = {3, 0, (byte) 0xfd, (byte) 0xff, 2, 0, 0x78, 0};
        RevisionStoreObject lowerRomanSuffix = numberListNode(id(seed + 32),
                lowerRomanWithSuffix, null);
        RevisionStoreObject bullet = numberListNode(id(seed + 33),
                new byte[] {1, 0, 0x22, 0x20}, null);
        RevisionStoreObject unknown = numberListNode(id(seed + 34), numberListFormat(5), null);
        RevisionStoreObject decimal = numberListNode(id(seed + 35), numberListFormat(0), null);

        List<RevisionStoreObject> items = new ArrayList<>();
        List<ExGuid> itemIds = new ArrayList<>();
        RevisionStoreObject[] listNodes = {upperRoman, lowerRoman, lowerRomanSuffix,
                lowerRomanSuffix, lowerRomanSuffix, bullet, bullet, unknown, decimal, unknown,
                unknown};
        for (int i = 0; i < listNodes.length; i++) {
            ExGuid itemId = id(seed + 1 + i);
            byte[] indent = i == 3 ? new byte[] {1, 0, 0, 0} : null;
            RevisionStoreObject item = i == 4 ?
                    listItemWithWrongIndent(itemId, listNodes[i].objectID, "item " + i) :
                    listItem(itemId, listNodes[i].objectID, "item " + i, indent);
            items.add(item);
            itemIds.add(itemId);
        }
        ExGuid plainOutlineId = id(seed + 20);
        RevisionStoreObject plainOutline = object(plainOutlineId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("plain outline text"))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(plainOutline, OneNoteJcid.OUTLINE_ELEMENT_NODE);
        items.add(plainOutline);
        itemIds.add(plainOutlineId);

        List<RevisionStoreObject> nodes = Arrays.asList(upperRoman, lowerRoman,
                lowerRomanSuffix, bullet, unknown, decimal);
        String xml = walkListItems(outlineId, itemIds, items, nodes, "outline caption");

        assertEquals(8, countOccurrences(xml, "<ol"), xml);
        assertEquals(1, countOccurrences(xml, "<ul>"), xml);
        assertEquals(11, countOccurrences(xml, "<li"), xml);
        assertTrue(xml.contains("plain outline text"), xml);
        assertTrue(xml.contains("outline caption"), xml);
    }

    @Test
    public void testInvalidNumberListNodesAndRestartDataFallBackSafely() throws Exception {
        int seed = 1400;
        ExGuid outlineId = id(seed);
        ExGuid missingNodeId = id(seed + 50);
        RevisionStoreObject noProperties = numberListNode(id(seed + 30), null, null);
        noProperties.propertySet = null;
        RevisionStoreObject noObjectSpace = numberListNode(id(seed + 31), numberListFormat(0), null);
        noObjectSpace.propertySet.objectSpaceObjectPropSet = null;
        RevisionStoreObject wrongJcid = numberListNode(id(seed + 32), numberListFormat(0), null);
        setJcid(wrongJcid, OneNoteJcid.OUTLINE_ELEMENT_NODE);
        RevisionStoreObject missingFormat = numberListNode(id(seed + 33), null, null);
        RevisionStoreObject wrongFormatType = numberListNodeWithProperties(id(seed + 34),
                new PropertySpec(PropertyType.FourBytesOfData, 0x1C001C1A, fourBytes(1)));
        RevisionStoreObject wrongRestartType = numberListNodeWithProperties(id(seed + 35),
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData, 0x1C001C1A,
                        bytes(numberListFormat(0))),
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData, 0x14001CB7,
                        bytes(new byte[] {1, 0, 0, 0})));
        RevisionStoreObject nullRestartData = numberListNodeWithProperties(id(seed + 36),
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData, 0x1C001C1A,
                        bytes(numberListFormat(0))),
                new PropertySpec(PropertyType.FourBytesOfData, 0x14001CB7,
                        fourBytes((byte[]) null)));
        RevisionStoreObject shortRestartData = numberListNodeWithProperties(id(seed + 37),
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData, 0x1C001C1A,
                        bytes(numberListFormat(0))),
                new PropertySpec(PropertyType.FourBytesOfData, 0x14001CB7,
                        fourBytes(new byte[] {1, 0, 0})));

        RevisionStoreObject[] listNodes = {null, null, noProperties, noObjectSpace, wrongJcid,
                missingFormat, missingFormat, wrongFormatType, wrongRestartType, nullRestartData,
                shortRestartData};
        List<RevisionStoreObject> items = new ArrayList<>();
        List<ExGuid> itemIds = new ArrayList<>();
        for (int i = 0; i < listNodes.length; i++) {
            ExGuid itemId = id(seed + 1 + i);
            ExGuid numberListId = listNodes[i] == null ?
                    (i == 0 ? missingNodeId : null) : listNodes[i].objectID;
            items.add(listItem(itemId, numberListId, "invalid item " + i));
            itemIds.add(itemId);
        }
        ExGuid plainItemId = id(seed + 20);
        RevisionStoreObject plainItem = object(plainItemId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("unstyled outline item"))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(plainItem, OneNoteJcid.OUTLINE_ELEMENT_NODE);
        items.add(plainItem);
        itemIds.add(plainItemId);
        itemIds.add(id(seed + 21));

        List<RevisionStoreObject> nodes = new ArrayList<>();
        for (RevisionStoreObject node : listNodes) {
            if (node != null && !nodes.contains(node)) {
                nodes.add(node);
            }
        }
        String xml = walkListItems(outlineId, itemIds, items, nodes, "outline text");

        assertEquals(1, countOccurrences(xml, "<ul>"), xml);
        assertEquals(1, countOccurrences(xml, "<ol type=\"1\">"), xml);
        assertEquals(0, countOccurrences(xml, "<li value="), xml);
        assertTrue(xml.contains("unstyled outline item"), xml);
    }

    @Test
    public void testTableFallbackHandlesCyclesAndBrokenObjects() throws Exception {
        ExGuid outerTableId = id(1500);
        ExGuid nestedTableId = id(1501);
        ExGuid rowId = id(1502);
        ExGuid cellId = id(1503);
        ExGuid noPropertiesId = id(1504);
        ExGuid noObjectSpaceId = id(1505);
        ExGuid missingId = id(1506);

        RevisionStoreObject outerTable = object(outerTableId,
                propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                                arrayNumber(5)),
                        new PropertySpec(PropertyType.ObjectID, 0x20001D00, new NoData()),
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("outer table text"))),
                Arrays.asList(nestedTableId, rowId, noPropertiesId, noObjectSpaceId, missingId),
                Collections.emptyList());
        RevisionStoreObject nestedTable = object(nestedTableId,
                propertySet(new PropertySpec(PropertyType.ObjectID, 0x20001D00, new NoData())),
                Collections.singletonList(outerTableId), Collections.emptyList());
        RevisionStoreObject row = object(rowId,
                propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                        arrayNumber(2))), Arrays.asList(cellId, outerTableId), Collections.emptyList());
        RevisionStoreObject cell = object(cellId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("cycle cell text"))), Collections.emptyList(),
                Collections.emptyList());
        RevisionStoreObject noProperties = new RevisionStoreObject();
        noProperties.objectID = noPropertiesId;
        RevisionStoreObject noObjectSpace = object(noObjectSpaceId, propertySet(),
                Collections.emptyList(), Collections.emptyList());
        noObjectSpace.propertySet.objectSpaceObjectPropSet = null;
        RevisionStoreObject withoutId = new RevisionStoreObject();
        RevisionStoreObject rootNoObjectSpace = object(id(1507), propertySet(),
                Collections.emptyList(), Collections.emptyList());
        rootNoObjectSpace.propertySet.objectSpaceObjectPropSet = null;
        setJcid(outerTable, OneNoteJcid.TABLE_NODE);
        setJcid(nestedTable, OneNoteJcid.TABLE_NODE);
        setJcid(row, OneNoteJcid.TABLE_ROW_NODE);
        setJcid(cell, OneNoteJcid.TABLE_CELL_NODE);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.OtherFileNodeList.add(group(null, withoutId, rootNoObjectSpace, row, cell,
                nestedTable, noProperties, noObjectSpace, outerTable));
        String xml = walkXml(pkg);

        assertEquals(2, countOccurrences(xml, "<table>"), xml);
        assertEquals(1, countOccurrences(xml, "<tr>"), xml);
        assertEquals(1, countOccurrences(xml, "<td>"), xml);
        assertTrue(xml.contains("outer table text"), xml);
        assertTrue(xml.contains("cycle cell text"), xml);
    }

    @Test
    public void testTableStructureSurvivesFallbackRootOrder() throws Exception {
        for (boolean useCellFallback : new boolean[] {false, true}) {
            ExGuid tableId = id(1170);
            ExGuid rowId = id(1171);
            ExGuid cellId = id(1172);
            RevisionStoreObject tableCell = object(cellId,
                    propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                            0x1C003498, text("table text"))), Collections.emptyList(),
                    Collections.emptyList());
            RevisionStoreObject row = object(rowId,
                    propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                            arrayNumber(1))), Collections.singletonList(cellId),
                    Collections.emptyList());
            RevisionStoreObject table = object(tableId,
                    propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                            arrayNumber(1))), Collections.singletonList(rowId),
                    Collections.emptyList());
            setJcid(tableCell, OneNoteJcid.TABLE_CELL_NODE);
            setJcid(row, OneNoteJcid.TABLE_ROW_NODE);
            setJcid(table, OneNoteJcid.TABLE_NODE);

            MSOneStorePackage pkg = new MSOneStorePackage();
            RevisionStoreObjectGroup outOfOrder = group(row, tableCell, table);
            if (useCellFallback) {
                RevisionStoreCell cell = new RevisionStoreCell();
                cell.objectGroups.add(outOfOrder);
                pkg.cells.add(cell);
            } else {
                pkg.OtherFileNodeList.add(outOfOrder);
            }

            String xml = walkXml(pkg);
            int tableStart = xml.indexOf("<table>");
            int rowStart = xml.indexOf("<tr>", tableStart);
            int cellStart = xml.indexOf("<td>", rowStart);
            int textStart = xml.indexOf("table text", cellStart);
            int cellEnd = xml.indexOf("</td>", textStart);
            int rowEnd = xml.indexOf("</tr>", cellEnd);
            int tableEnd = xml.indexOf("</table>", rowEnd);
            assertTrue(tableStart >= 0 && rowStart > tableStart && cellStart > rowStart &&
                    textStart > cellStart && cellEnd > textStart && rowEnd > cellEnd &&
                    tableEnd > rowEnd, xml);
        }
    }

    @Test
    public void testParseWarningsAreBoundedAcrossParserAndWalkPhases() throws Exception {
        MSOneStorePackage pkg = new MSOneStorePackage();
        for (int i = 0; i < 99; i++) {
            pkg.recordParseWarning("warning " + i);
        }
        pkg.recordParseWarning("duplicate warning");
        pkg.recordParseWarning("duplicate warning");
        RevisionStoreCell damagedCell = new RevisionStoreCell();
        RevisionManifestRootDeclare missingRoot = new RevisionManifestRootDeclare();
        missingRoot.objectExGuid = id(1006);
        damagedCell.rootDeclares.add(missingRoot);
        pkg.cells.add(damagedCell);

        Metadata metadata = new Metadata();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata,
                new XHTMLContentHandler(new ToTextContentHandler(), metadata, new ParseContext()),
                new ParseContext());

        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertEquals(101, warnings.length);
        assertEquals(1, Arrays.stream(warnings)
                .filter(warning -> warning.equals("duplicate warning")).count());
        assertTrue(Arrays.stream(warnings).anyMatch(warning -> warning.contains("suppressed")));
    }

    @Test
    public void testReferenceArrayReportsUnavailableAndCappedWarnings() throws Exception {
        ExGuid rootId = id(2000);
        List<ExGuid> references = new ArrayList<>(100_001);
        for (int i = 0; i < 100_001; i++) {
            references.add(rootId);
        }
        RevisionStoreObject root = object(rootId,
                propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001D5F,
                        arrayNumber(100_002))), references, Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = rootId;
        cell.rootDeclares.add(rootDeclare);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        Metadata metadata = new Metadata();
        walk(pkg, metadata);
        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertTrue(Arrays.stream(warnings)
                .anyMatch(warning -> warning.contains("declared 100002 entries")));
        assertTrue(Arrays.stream(warnings)
                .anyMatch(warning -> warning.contains("Capping OneNote object reference array")));
    }

    @Test
    public void testPagesFollowSectionOrderAndDropOlderCellVersions() throws Exception {
        ExGuid sectionRootId = id(1);
        CellID pageOne = cell(10, 100);
        CellID pageTwo = cell(20, 200);
        CellID oldPageOne = cell(11, 100);
        RevisionStoreCell pageTwoCell = cellWithText(pageTwo, "page two");
        RevisionStoreCell oldPageOneCell = cellWithText(oldPageOne, "old page one");
        RevisionStoreCell pageOneCell = cellWithText(pageOne, "page one");
        RevisionStoreCell unrelatedCell = cellWithText(cell(30, 300), "unrelated");

        RevisionStoreObject sectionRoot = object(sectionRootId,
                propertySet(new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D78,
                                new NoData()),
                        new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D79,
                                new NoData())),
                Collections.emptyList(), Arrays.asList(pageOne, pageTwo));
        RevisionStoreCell section = new RevisionStoreCell();
        section.objectGroups.add(group(sectionRoot));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = sectionRootId;
        section.rootDeclares.add(rootDeclare);
        RevisionManifestRootDeclare missingRoot = new RevisionManifestRootDeclare();
        missingRoot.objectExGuid = id(1000);
        section.rootDeclares.add(missingRoot);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.dataRootCell = section;
        pkg.cells.addAll(Arrays.asList(pageTwoCell, oldPageOneCell, pageOneCell, unrelatedCell));

        Metadata metadata = new Metadata();
        String text = walk(pkg, metadata);
        assertTrue(text.indexOf("page one") >= 0);
        assertTrue(text.indexOf("page two") >= 0);
        assertTrue(text.indexOf("page one") < text.indexOf("page two"));
        assertFalse(text.contains("old page one"));
        assertTrue(text.contains("unrelated"));
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains("could not be resolved")));
    }

    @Test
    public void testUnresolvedRootsFallBackToAllObjects() throws Exception {
        RevisionStoreCell cell = cellWithText(cell(1, 1), "fallback content");
        cell.rootDeclares.clear();
        RevisionManifestRootDeclare missingRoot = new RevisionManifestRootDeclare();
        missingRoot.objectExGuid = id(999);
        cell.rootDeclares.add(missingRoot);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();

        assertTrue(walk(pkg, metadata).contains("fallback content"));
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains(id(999).toString())));
    }

    @Test
    public void testExhaustedObjectReferenceTriggersFallbackWarning() throws Exception {
        RevisionStoreObject root = object(id(1004), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData())),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreObject fallback = object(id(1005), propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("fallback after exhausted reference"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, fallback));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = root.objectID;
        cell.rootDeclares.add(rootDeclare);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();
        String text = walk(pkg, metadata);

        assertFalse(text.contains("fallback after exhausted reference"));
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains("reference slot was exhausted")));
    }

    @Test
    public void testExhaustedObjectSpaceReferenceWarns() throws Exception {
        ExGuid sectionRootID = id(1007);
        RevisionStoreCell section = new RevisionStoreCell();
        section.objectGroups.add(group(object(sectionRootID,
                propertySet(new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D78,
                        new NoData())), Collections.emptyList(), Collections.emptyList())));
        RevisionManifestRootDeclare sectionRoot = new RevisionManifestRootDeclare();
        sectionRoot.objectExGuid = sectionRootID;
        section.rootDeclares.add(sectionRoot);
        RevisionStoreCell page = cellWithText(cell(1008, 1009), "page after missing space");

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.dataRootCell = section;
        pkg.cells.add(page);
        Metadata metadata = new Metadata();
        String text = walk(pkg, metadata);

        assertTrue(text.contains("page after missing space"));
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains("object-space reference slot was exhausted")));
    }

    @Test
    public void testMixedRootsDoNotTriggerAllObjectFallback() throws Exception {
        RevisionStoreObject root = object(id(1001), propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("resolved root"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreObject unrelated = object(id(1002), propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("unrelated object"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, unrelated));
        RevisionManifestRootDeclare resolvedRoot = new RevisionManifestRootDeclare();
        resolvedRoot.objectExGuid = root.objectID;
        cell.rootDeclares.add(resolvedRoot);
        RevisionManifestRootDeclare missingRoot = new RevisionManifestRootDeclare();
        missingRoot.objectExGuid = id(1003);
        cell.rootDeclares.add(missingRoot);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();
        String text = walk(pkg, metadata);

        assertTrue(text.contains("resolved root"));
        assertFalse(text.contains("unrelated object"));
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains(id(1003).toString())));
    }

    @Test
    public void testOriginalAuthorBecomesCreator() throws Exception {
        ExGuid authorId = id(701);
        RevisionStoreObject root = object(id(700), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData())),
                Collections.singletonList(authorId), Collections.emptyList());
        RevisionStoreObject author = object(authorId, propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C001D75, utf16Text("Иван Петров"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, author));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = root.objectID;
        cell.rootDeclares.add(rootDeclare);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();
        walk(pkg, metadata);
        assertEquals("Иван Петров", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Иван Петров", metadata.get(OneNote.ORIGINAL_AUTHORS));
    }

    @Test
    public void testDualRoleAuthorRecordedForBothRoles() throws Exception {
        ExGuid authorId = id(710);
        // the same author object referenced as both AuthorOriginal and AuthorMostRecent
        RevisionStoreObject root = object(id(711), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData()),
                        new PropertySpec(PropertyType.ObjectID, 0x20001D79, new NoData())),
                Arrays.asList(authorId, authorId), Collections.emptyList());
        RevisionStoreObject author = object(authorId, propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C001D75, utf16Text("Single Author"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, author));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = root.objectID;
        cell.rootDeclares.add(rootDeclare);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();
        walk(pkg, metadata);

        assertEquals("Single Author", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Single Author", metadata.get(OneNote.ORIGINAL_AUTHORS));
        assertEquals("Single Author", metadata.get(OneNote.MOST_RECENT_AUTHORS));
    }

    @Test
    public void testBlobOnlyRootWithDanglingContentRootWalksAllObjects() throws Exception {
        RevisionStoreObject blobRoot = object(id(620), propertySet(),
                Collections.emptyList(), Collections.emptyList());
        blobRoot.propertySet = null;
        blobRoot.fileDataObject = fileData("blob root");
        RevisionStoreObject textObject = object(id(621), propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("page body text"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(blobRoot, textObject));
        RevisionManifestRootDeclare blobDeclare = new RevisionManifestRootDeclare();
        blobDeclare.objectExGuid = blobRoot.objectID;
        cell.rootDeclares.add(blobDeclare);
        RevisionManifestRootDeclare danglingContentRoot = new RevisionManifestRootDeclare();
        danglingContentRoot.objectExGuid = id(622);
        cell.rootDeclares.add(danglingContentRoot);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();

        // the blob root alone cannot reach the page body; the dangling content root must
        // trigger the walk-everything fallback so the body is not lost
        assertTrue(walk(pkg, metadata).contains("page body text"));
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains("walking all objects")));
    }

    @Test
    public void testHasEmittedContentTracksTextAndEmptyWalks() throws Exception {
        MSOneStorePackage withText = new MSOneStorePackage();
        withText.cells.add(cellWithText(cell(70, 71), "some text"));
        walk(withText);
        assertTrue(withText.hasEmittedContent());

        MSOneStorePackage empty = new MSOneStorePackage();
        RevisionStoreCell danglingCell = new RevisionStoreCell();
        RevisionManifestRootDeclare missingRoot = new RevisionManifestRootDeclare();
        missingRoot.objectExGuid = id(72);
        danglingCell.rootDeclares.add(missingRoot);
        empty.cells.add(danglingCell);
        walk(empty);
        assertFalse(empty.hasEmittedContent());
    }

    @Test
    public void testSanitizeResourceNameKeepsBasenameOfPathShapedNames() {
        assertEquals("pic 1.png",
                MSOneStorePackage.sanitizeResourceName("D:\\images\\pic 1.png"));
        assertEquals("pic.png", MSOneStorePackage.sanitizeResourceName("C:pic.png"));
        assertEquals("a.png", MSOneStorePackage.sanitizeResourceName("/tmp/a.png"));
        assertEquals("plain.png", MSOneStorePackage.sanitizeResourceName("plain.png"));
        assertEquals("", MSOneStorePackage.sanitizeResourceName(".."));
        assertEquals("", MSOneStorePackage.sanitizeResourceName("D:\\images\\.."));
    }

    @Test
    public void testBlobOnlyRootDoesNotFallBackToOtherObjects() throws Exception {
        RevisionStoreObject blobRoot = object(id(610), propertySet(),
                Collections.emptyList(), Collections.emptyList());
        blobRoot.propertySet = null;
        blobRoot.fileDataObject = fileData("blob root");
        RevisionStoreObject textObject = object(id(611), propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("fallback content"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(blobRoot, textObject));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = blobRoot.objectID;
        cell.rootDeclares.add(rootDeclare);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        assertFalse(walk(pkg).contains("fallback content"));
    }

    @Test
    public void testPageMarkupIsBalancedAndPinned() throws Exception {
        CellID pageID = cell(90, 91);
        RevisionStoreCell page = cellWithText(pageID, "page text");
        ExGuid sectionRootID = id(92);
        RevisionStoreCell section = new RevisionStoreCell();
        section.objectGroups.add(group(object(sectionRootID,
                propertySet(new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D78,
                        new NoData())), Collections.emptyList(),
                Collections.singletonList(pageID))));
        RevisionManifestRootDeclare sectionRoot = new RevisionManifestRootDeclare();
        sectionRoot.objectExGuid = sectionRootID;
        section.rootDeclares.add(sectionRoot);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.dataRootCell = section;
        pkg.cells.add(page);

        String xml = walkXml(pkg);
        assertEquals(1, count(xml, "<div class=\"page\">"));
        assertEquals(1, count(xml, "</div>"));
        assertTrue(xml.contains("page text"));
    }

    @Test
    public void testPageMarkupClosesWhenWalkThrows() throws Exception {
        CellID pageID = cell(95, 96);
        RevisionStoreCell page = cellWithText(pageID, "page text");
        ExGuid sectionRootID = id(97);
        RevisionStoreCell section = new RevisionStoreCell();
        section.objectGroups.add(group(object(sectionRootID,
                propertySet(new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D78,
                        new NoData())), Collections.emptyList(),
                Collections.singletonList(pageID))));
        RevisionManifestRootDeclare sectionRoot = new RevisionManifestRootDeclare();
        sectionRoot.objectExGuid = sectionRootID;
        section.rootDeclares.add(sectionRoot);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.dataRootCell = section;
        pkg.cells.add(page);
        Metadata metadata = new Metadata();
        List<String> elements = new ArrayList<>();
        DefaultHandler recordingHandler = new DefaultHandler() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public void startElement(String uri, String localName, String qName,
                                     org.xml.sax.Attributes atts) {
                elements.add(qName);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                elements.add("/" + qName);
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                text.append(ch, start, length);
                if (text.indexOf("page text") >= 0) {
                    throw new SAXException("intentional test failure");
                }
            }
        };
        XHTMLContentHandler xhtml = new XHTMLContentHandler(recordingHandler, metadata,
                new ParseContext());
        xhtml.startDocument();

        assertThrows(SAXException.class, () -> pkg.walkTree(new OneNoteTreeWalkerOptions(),
                metadata, xhtml, new ParseContext()));
        assertTrue(elements.contains("p"));
        assertTrue(elements.contains("/p"));
        assertTrue(elements.contains("div"));
        assertTrue(elements.contains("/div"));
        assertTrue(elements.indexOf("p") < elements.indexOf("/p"));
        assertTrue(elements.indexOf("div") < elements.indexOf("/div"));
        assertTrue(elements.indexOf("/p") < elements.indexOf("/div"));
    }

    @Test
    public void testRemoveSupersededObjectsKeepsNewestVersion() throws Exception {
        ExGuid objectID = id(950);
        RevisionStoreObject oldObject = object(objectID, propertySet(
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("old"))), Collections.emptyList(),
                Collections.emptyList());
        RevisionStoreObject newObject = object(objectID, propertySet(
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("new"))), Collections.emptyList(),
                Collections.emptyList());
        List<RevisionStoreObjectGroup> groups = new ArrayList<>(Arrays.asList(
                group(oldObject), group(newObject)));
        Method removeSupersededObjects = MSOneStoreParser.class.getDeclaredMethod(
                "removeSupersededObjects", List.class);
        removeSupersededObjects.setAccessible(true);

        removeSupersededObjects.invoke(new MSOneStoreParser(), groups);

        assertEquals(1, groups.get(0).objects.size());
        assertSame(newObject, groups.get(0).objects.get(0));
        assertTrue(groups.get(1).objects.isEmpty());
    }

    @Test
    public void testArrayCountsAndRecursionDepthAreBounded() throws Exception {
        RevisionStoreObject hugeArrayRoot = object(id(600), propertySet(
                        new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001D5F,
                                arrayNumber(Integer.MAX_VALUE)),
                        new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001D5F,
                                new NoData())),
                Collections.emptyList(), Collections.emptyList());
        Method collectActions = MSOneStorePackage.class.getDeclaredMethod("collectActions",
                PropertySet.class, List.class, int[].class, List.class, int[].class, List.class,
                int.class);
        collectActions.setAccessible(true);
        List<Object> actions = new ArrayList<>();
        collectActions.invoke(new MSOneStorePackage(),
                hugeArrayRoot.propertySet.objectSpaceObjectPropSet.body,
                Collections.emptyList(), new int[]{0}, Collections.emptyList(), new int[]{0},
                actions, 0);
        assertTrue(actions.isEmpty());
        actions.clear();
        collectActions.invoke(new MSOneStorePackage(),
                hugeArrayRoot.propertySet.objectSpaceObjectPropSet.body,
                Collections.singletonList(id(601)), new int[]{0}, Collections.emptyList(),
                new int[]{0}, actions, 1000);
        assertTrue(actions.isEmpty());

        Method collectReferencedCells = MSOneStorePackage.class.getDeclaredMethod(
                "collectReferencedCells", RevisionStoreObject.class, Map.class, Set.class,
                List.class, int.class);
        collectReferencedCells.setAccessible(true);
        RevisionStoreObject referencedRoot = object(id(602), propertySet(
                        new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D78, new NoData())),
                Collections.emptyList(), Collections.singletonList(cell(603, 604)));
        List<CellID> orderedCellIds = new ArrayList<>();
        collectReferencedCells.invoke(new MSOneStorePackage(), referencedRoot,
                new java.util.HashMap<>(), new java.util.HashSet<>(), orderedCellIds, 1000);
        assertTrue(orderedCellIds.isEmpty());

        PropertySet alignedSet = propertySet(
                new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001D5F,
                        arrayNumber(100001)),
                new PropertySpec(PropertyType.ObjectID, 0x24001D5F, new NoData()));
        actions.clear();
        collectActions.invoke(new MSOneStorePackage(), alignedSet,
                Collections.nCopies(100001, id(601)), new int[]{0}, Collections.emptyList(),
                new int[]{0}, actions, 0);
        assertEquals(100001, actions.size());
        java.lang.reflect.Field childReference = actions.get(actions.size() - 1).getClass()
                .getDeclaredField("childReference");
        childReference.setAccessible(true);
        assertNull(childReference.get(actions.get(actions.size() - 1)));

        Method walkObject = MSOneStorePackage.class.getDeclaredMethod("walkObject",
                RevisionStoreObject.class, Map.class, Set.class,
                Class.forName(MSOneStorePackage.class.getName() + "$AuthorRole"),
                OneNoteTreeWalkerOptions.class, Metadata.class, XHTMLContentHandler.class,
                int.class);
        walkObject.setAccessible(true);
        // an object that emits text when walked, so the depth-capped walk's blank
        // output proves the cap fired rather than the setup having nothing to emit
        RevisionStoreObject textRoot = object(id(605), propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("depth capped text"))),
                Collections.emptyList(), Collections.emptyList());
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new ToTextContentHandler(writer), metadata, new ParseContext());
        xhtml.startDocument();
        walkObject.invoke(new MSOneStorePackage(), textRoot,
                new java.util.HashMap<>(), new java.util.HashSet<>(), null,
                new OneNoteTreeWalkerOptions(), metadata, xhtml, 1000);
        assertTrue(writer.toString().isBlank());
        walkObject.invoke(new MSOneStorePackage(), textRoot,
                new java.util.HashMap<>(), new java.util.HashSet<>(), null,
                new OneNoteTreeWalkerOptions(), metadata, xhtml, 0);
        xhtml.endDocument();
        assertTrue(writer.toString().contains("depth capped text"));
    }

    @Test
    public void testDanglingPrimaryPictureDoesNotSuppressWebPicture() throws Exception {
        ExGuid missingPictureID = id(50);
        ExGuid webPictureID = id(51);
        RevisionStoreObject root = object(id(52), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001C3F, new NoData()),
                        new PropertySpec(PropertyType.ObjectID, 0x200034C8, new NoData())),
                Arrays.asList(missingPictureID, webPictureID), Collections.emptyList());
        RevisionStoreObject webPicture = object(webPictureID, propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("derived picture"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, webPicture));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = root.objectID;
        cell.rootDeclares.add(rootDeclare);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        String text = walk(pkg);
        assertTrue(text.contains("derived picture"), text);
    }

    @Test
    public void testContentlessPrimaryPictureDoesNotSuppressDerivedPicture() throws Exception {
        ExGuid pictureID = id(40);
        ExGuid webPictureID = id(41);
        RevisionStoreObject root = object(id(42), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001C3F, new NoData()),
                        new PropertySpec(PropertyType.ObjectID, 0x200034C8, new NoData())),
                Arrays.asList(pictureID, webPictureID), Collections.emptyList());
        RevisionStoreObject picture = object(pictureID, propertySet(),
                Collections.emptyList(), Collections.emptyList());
        picture.propertySet.objectSpaceObjectPropSet.body = null;
        RevisionStoreObject webPicture = object(webPictureID, propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("derived picture"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, picture, webPicture));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = root.objectID;
        cell.rootDeclares.add(rootDeclare);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        assertTrue(walk(pkg).contains("derived picture"));
    }

    @Test
    public void testUsablePrimaryPictureSuppressesDerivedPicture() throws Exception {
        ExGuid pictureID = id(80);
        ExGuid webPictureID = id(81);
        RevisionStoreObject root = object(id(82), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001C3F, new NoData()),
                        new PropertySpec(PropertyType.ObjectID, 0x200034C8, new NoData())),
                Arrays.asList(pictureID, webPictureID), Collections.emptyList());
        RevisionStoreObject picture = object(pictureID, propertySet(),
                Collections.emptyList(), Collections.emptyList());
        picture.fileDataObject = fileData("primary image");
        RevisionStoreObject webPicture = object(webPictureID, propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("derived picture"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, picture, webPicture));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = root.objectID;
        cell.rootDeclares.add(rootDeclare);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);

        assertFalse(walk(pkg).contains("derived picture"));
    }

    @Test
    public void testNestedPropertySetsAndMissingReferencesAreTraversedSafely() throws Exception {
        ExGuid childId = id(2);
        PropertySet nested = propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                0x1C003498, text("nested text")));
        PrtArrayOfPropertyValues array = new PrtArrayOfPropertyValues();
        array.data = new PropertySet[]{propertySet(new PropertySpec(
                PropertyType.FourBytesOfLengthFollowedByData, 0x1C003498, text("array text")))};
        RevisionStoreObject root = object(id(1), propertySet(
                        new PropertySpec(PropertyType.PropertySet, 0, nested),
                        new PropertySpec(PropertyType.ArrayOfPropertyValues, 0, array),
                        new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001D5F,
                                arrayNumber(2)),
                        new PropertySpec(PropertyType.ObjectID, 0x24001D5F, new NoData()),
                        new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D78, new NoData()),
                        new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D79, new NoData()),
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C001DD7, bytes((byte) 'u', (byte) 0, (byte) 1)),
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C001C22, bytes((byte) 'h', (byte) 0, (byte) 'i', (byte) 0,
                                        (byte) 0, (byte) 0))),
                Collections.singletonList(childId), Collections.singletonList(cell(50, 51)));
        RevisionStoreObject child = object(childId, propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("child text"))),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(root, child));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = root.objectID;
        cell.rootDeclares.add(rootDeclare);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        String text = walk(pkg);
        assertTrue(text.contains("nested text"));
        assertTrue(text.contains("array text"));
        assertTrue(text.contains("child text"));
        assertTrue(text.contains("u"));
        assertTrue(text.contains("hi"));
    }

    private static String walk(MSOneStorePackage pkg) throws Exception {
        Metadata metadata = new Metadata();
        return walk(pkg, metadata);
    }

    private static String walk(MSOneStorePackage pkg, Metadata metadata) throws Exception {
        ParseContext context = new ParseContext();
        StringWriter writer = new StringWriter();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new ToTextContentHandler(writer), metadata, context);
        xhtml.startDocument();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata, xhtml, context);
        xhtml.endDocument();
        return writer.toString();
    }

    private static String walkXml(MSOneStorePackage pkg) throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        ToXMLContentHandler xml = new ToXMLContentHandler();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(xml, metadata, context);
        xhtml.startDocument();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata, xhtml, context);
        xhtml.endDocument();
        return xml.toString();
    }

    private static byte[] numberListFormat(int code) {
        return new byte[] {2, 0, (byte) 0xfd, (byte) 0xff, (byte) code, 0};
    }

    private static String singleListXml(int seed, byte[] format, Integer restart)
            throws Exception {
        return singleListXml(seed,
                numberListNode(id(seed + 2), format, restart));
    }

    private static String singleListXml(int seed, RevisionStoreObject numberList)
            throws Exception {
        ExGuid outlineId = id(seed);
        ExGuid itemId = id(seed + 1);
        List<ExGuid> itemIds = Collections.singletonList(itemId);
        List<RevisionStoreObject> items = Collections.singletonList(
                listItem(itemId, numberList.objectID, "single item"));
        return walkListItems(outlineId, itemIds, items, Collections.singletonList(numberList),
                "");
    }

    private static String walkListItems(ExGuid outlineId, List<ExGuid> itemIds,
                                        List<RevisionStoreObject> items,
                                        List<RevisionStoreObject> numberLists, String caption)
            throws Exception {
        PropertySpec childArray = new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                arrayNumber(itemIds.size()));
        List<PropertySpec> properties = new ArrayList<>();
        properties.add(childArray);
        if (!caption.isEmpty()) {
            properties.add(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                    0x1C003498, text(caption)));
        }
        RevisionStoreObject outline = object(outlineId,
                propertySet(properties.toArray(new PropertySpec[0])), itemIds,
                Collections.emptyList());
        setJcid(outline, OneNoteJcid.OUTLINE_NODE);
        List<RevisionStoreObject> objects = new ArrayList<>();
        objects.add(outline);
        objects.addAll(items);
        objects.addAll(numberLists);
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(objects.toArray(new RevisionStoreObject[0])));
        cell.rootDeclares.add(rootDeclare(outlineId));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        return walkXml(pkg);
    }

    private static int count(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static RevisionManifestRootDeclare rootDeclare(ExGuid objectID) {
        RevisionManifestRootDeclare declare = new RevisionManifestRootDeclare();
        declare.objectExGuid = objectID;
        return declare;
    }

    private static void setJcid(RevisionStoreObject object, int index) throws Exception {
        JCIDObject jcidObject = new JCIDObject(null, emptyObjectData());
        jcidObject.jcid.index = index;
        object.jcid = jcidObject;
    }

    private static RevisionStoreObject numberListNode(ExGuid objectId, byte[] format,
                                                       Integer restart) throws Exception {
        List<PropertySpec> properties = new ArrayList<>();
        if (format != null) {
            properties.add(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                    0x1C001C1A, bytes(format)));
        }
        if (restart != null) {
            properties.add(new PropertySpec(PropertyType.FourBytesOfData, 0x14001CB7,
                    fourBytes(restart)));
        }
        return numberListNodeWithProperties(objectId,
                properties.toArray(new PropertySpec[0]));
    }

    private static RevisionStoreObject numberListNodeWithProperties(ExGuid objectId,
                                                                     PropertySpec... properties)
            throws Exception {
        RevisionStoreObject numberList = object(objectId, propertySet(properties),
                Collections.emptyList(), Collections.emptyList());
        setJcid(numberList, OneNoteJcid.NUMBER_LIST_NODE);
        return numberList;
    }

    private static RevisionStoreObject listItem(ExGuid objectId, ExGuid numberListId,
                                                String text) throws Exception {
        return listItem(objectId, numberListId, text, null);
    }

    private static RevisionStoreObject listItem(ExGuid objectId, ExGuid numberListId,
                                                String text, byte[] indent) throws Exception {
        List<PropertySpec> properties = new ArrayList<>();
        properties.add(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C26,
                arrayNumber(1)));
        properties.add(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                0x1C003498, text(text)));
        if (indent != null) {
            properties.add(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                    0x1C001C12, bytes(indent)));
        }
        RevisionStoreObject listItem = object(objectId,
                propertySet(properties.toArray(new PropertySpec[0])),
                Collections.singletonList(numberListId), Collections.emptyList());
        setJcid(listItem, OneNoteJcid.OUTLINE_ELEMENT_NODE);
        return listItem;
    }

    private static RevisionStoreObject listItemWithWrongIndent(ExGuid objectId,
                                                               ExGuid numberListId, String text)
            throws Exception {
        RevisionStoreObject listItem = listItem(objectId, numberListId, text);
        PropertySet body = listItem.propertySet.objectSpaceObjectPropSet.body;
        body.rgPrids = Arrays.copyOf(body.rgPrids, body.rgPrids.length + 1);
        body.rgPrids[body.rgPrids.length - 1] = propertyID(PropertyType.FourBytesOfData,
                0x1C001C12);
        body.rgData.add(fourBytes(1));
        body.cProperties++;
        return listItem;
    }

    private static FourBytesOfData fourBytes(int value) {
        FourBytesOfData data = new FourBytesOfData();
        data.data = new byte[] {(byte) value, (byte) (value >>> 8), (byte) (value >>> 16),
                (byte) (value >>> 24)};
        return data;
    }

    private static FourBytesOfData fourBytes(byte[] value) {
        FourBytesOfData data = new FourBytesOfData();
        data.data = value;
        return data;
    }

    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }

    private static RevisionStoreCell cellWithText(CellID cellID, String value) throws Exception {
        RevisionStoreObject object = object(id(cellID.extendGUID1.hashCode()),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text(value))), Collections.emptyList(), Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.cellID = cellID;
        cell.objectGroups.add(group(object));
        RevisionManifestRootDeclare rootDeclare = new RevisionManifestRootDeclare();
        rootDeclare.objectExGuid = object.objectID;
        cell.rootDeclares.add(rootDeclare);
        return cell;
    }

    private static RevisionStoreObject object(ExGuid objectID, PropertySet body,
                                              List<ExGuid> references, List<CellID> spaces)
            throws Exception {
        RevisionStoreObject object = new RevisionStoreObject();
        object.objectID = objectID;
        PropertySetObject propertySetObject = new PropertySetObject(null, emptyObjectData());
        ObjectSpaceObjectPropSet propSet = new ObjectSpaceObjectPropSet();
        propSet.body = body;
        propertySetObject.objectSpaceObjectPropSet = propSet;
        object.propertySet = propertySetObject;
        if (!references.isEmpty()) {
            object.referencedObjectID = new org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGUIDArray();
            object.referencedObjectID.content = references;
        }
        if (!spaces.isEmpty()) {
            object.referencedObjectSpacesID = new org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellIDArray();
            object.referencedObjectSpacesID.content = spaces;
        }
        return object;
    }

    private static FileDataObject fileData(String value) {
        ObjectDataBLOB blob = new ObjectDataBLOB();
        blob.data.content.addAll(ByteUtil.toListOfByte(value.getBytes(StandardCharsets.UTF_8)));
        ObjectDataBLOBDataElementData blobData = new ObjectDataBLOBDataElementData();
        blobData.objectDataBLOB = blob;
        DataElement element = new DataElement();
        element.dataElementType = DataElementType.ObjectDataBLOBDataElementData;
        element.data = blobData;
        FileDataObject fileData = new FileDataObject();
        fileData.objectDataBLOBDataElement = element;
        return fileData;
    }

    private static RevisionStoreObjectGroup group(RevisionStoreObject... objects) {
        RevisionStoreObjectGroup group = new RevisionStoreObjectGroup(id(500));
        group.objects.addAll(Arrays.asList(objects));
        return group;
    }

    private static PropertySet propertySet(PropertySpec... specs) {
        PropertySet set = new PropertySet();
        set.cProperties = specs.length;
        set.rgPrids = new PropertyID[specs.length];
        set.rgData = new ArrayList<>();
        for (int i = 0; i < specs.length; i++) {
            set.rgPrids[i] = propertyID(specs[i].type, specs[i].value);
            set.rgData.add(specs[i].property);
        }
        return set;
    }

    private static PropertyID propertyID(PropertyType type, int value) {
        PropertyID id = new PropertyID();
        id.type = type.getIntVal();
        id.value = value;
        return id;
    }

    private static PrtFourBytesOfLengthFollowedByData bytes(byte... value) {
        PrtFourBytesOfLengthFollowedByData data = new PrtFourBytesOfLengthFollowedByData();
        data.data = value;
        data.cb = data.data.length;
        return data;
    }

    private static PrtFourBytesOfLengthFollowedByData text(String value) {
        PrtFourBytesOfLengthFollowedByData data = new PrtFourBytesOfLengthFollowedByData();
        data.data = value.getBytes(StandardCharsets.US_ASCII);
        data.cb = data.data.length;
        return data;
    }

    private static PrtFourBytesOfLengthFollowedByData utf16Text(String value) {
        PrtFourBytesOfLengthFollowedByData data = new PrtFourBytesOfLengthFollowedByData();
        data.data = (value + "\u0000").getBytes(StandardCharsets.UTF_16LE);
        data.cb = data.data.length;
        return data;
    }

    private static ArrayNumber arrayNumber(int number) {
        ArrayNumber array = new ArrayNumber();
        array.number = number;
        return array;
    }

    private static ObjectGroupObjectData emptyObjectData() {
        ObjectGroupObjectData data = new ObjectGroupObjectData();
        data.data.content.addAll(ByteUtil.toListOfByte(new byte[]{0, 0, 0, (byte) 0x80,
                0, 0, 0, 0}));
        return data;
    }

    private static CellID cell(int first, int second) {
        return new CellID(id(first), id(second));
    }

    private static ExGuid id(int value) {
        return new ExGuid(value, UUID.nameUUIDFromBytes(("id-" + value).getBytes(StandardCharsets.UTF_8)));
    }

    private static final class PropertySpec {
        private final PropertyType type;
        private final int value;
        private final IProperty property;

        private PropertySpec(PropertyType type, int value, IProperty property) {
            this.type = type;
            this.value = value;
            this.property = property;
        }
    }
}
