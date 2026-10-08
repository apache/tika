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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.HashMap;
import java.util.HashSet;
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
import org.apache.tika.parser.microsoft.onenote.OneNoteGuidCollector;
import org.apache.tika.parser.microsoft.onenote.OneNoteJcid;
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
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.HeaderCell;
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
        setJcid(table, OneNoteStructureJcid.TABLE_NODE);
        setJcid(row, OneNoteStructureJcid.TABLE_ROW_NODE);
        setJcid(tableCell, OneNoteStructureJcid.TABLE_CELL_NODE);

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
        setJcid(outline, OneNoteStructureJcid.OUTLINE_NODE);

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
        setJcid(plainOutline, OneNoteStructureJcid.OUTLINE_ELEMENT_NODE);
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
        setJcid(wrongJcid, OneNoteStructureJcid.OUTLINE_ELEMENT_NODE);
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
        setJcid(plainItem, OneNoteStructureJcid.OUTLINE_ELEMENT_NODE);
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
        setJcid(outerTable, OneNoteStructureJcid.TABLE_NODE);
        setJcid(nestedTable, OneNoteStructureJcid.TABLE_NODE);
        setJcid(row, OneNoteStructureJcid.TABLE_ROW_NODE);
        setJcid(cell, OneNoteStructureJcid.TABLE_CELL_NODE);

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.OtherFileNodeList.add(null);
        RevisionStoreObjectGroup missingObjects = group();
        missingObjects.objects = null;
        pkg.OtherFileNodeList.add(missingObjects);
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
            setJcid(tableCell, OneNoteStructureJcid.TABLE_CELL_NODE);
            setJcid(row, OneNoteStructureJcid.TABLE_ROW_NODE);
            setJcid(table, OneNoteStructureJcid.TABLE_NODE);

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
    public void testTableListStructureAndGuidsSurviveCombinedWalk() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            ExGuid tableId = id(1600);
            ExGuid rowId = id(1601);
            ExGuid cellId = id(1602);
            ExGuid itemId = id(1603);
            RevisionStoreObject numberList = numberListNode(id(1604), numberListFormat(1), 5);
            RevisionStoreObject item = listItem(itemId, numberList.objectID, "combined cell text");
            RevisionStoreObject pageMetadata = notebookGuidObject(1605, OneNoteJcid.PAGE_METADATA,
                    sectionGuidBytes());
            RevisionStoreObject tableCell = object(cellId,
                    propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                            arrayNumber(2))), Arrays.asList(itemId, pageMetadata.objectID),
                    Collections.emptyList());
            RevisionStoreObject row = object(rowId,
                    propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                            arrayNumber(1))), Collections.singletonList(cellId),
                    Collections.emptyList());
            RevisionStoreObject table = object(tableId,
                    propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                            arrayNumber(1))), Collections.singletonList(rowId),
                    Collections.emptyList());
            setJcid(table, OneNoteStructureJcid.TABLE_NODE);
            setJcid(row, OneNoteStructureJcid.TABLE_ROW_NODE);
            setJcid(tableCell, OneNoteStructureJcid.TABLE_CELL_NODE);
            RevisionStoreObjectGroup objects = group(item, tableCell, row, numberList,
                    pageMetadata, table);
            MSOneStorePackage pkg = new MSOneStorePackage();
            if (mode == 0) {
                pkg.OtherFileNodeList.add(objects);
            } else {
                RevisionStoreCell cell = new RevisionStoreCell();
                cell.objectGroups.add(objects);
                if (mode == 2) {
                    cell.rootDeclares.add(rootDeclare(tableId));
                }
                pkg.cells.add(cell);
            }
            Metadata metadata = new Metadata();
            String xml = walkXml(pkg, metadata);

            assertTrue(xml.matches("(?s).*<table>\\s*<tr>\\s*<td>\\s*<ol type=\"I\">\\s*" +
                    "<li value=\"5\">.*combined cell text.*</li>\\s*</ol>\\s*</td>\\s*" +
                    "</tr>\\s*</table>.*"), xml);
            assertEquals(1, countOccurrences(xml, "<table>"), xml);
            assertEquals(1, countOccurrences(xml, "<li"), xml);
            assertArrayEquals(new String[] {"{00112233-4455-6677-8899-AABBCCDDEEFF}"},
                    metadata.getValues(OneNote.PAGE_GUIDS));
        }
    }

    @Test
    public void testSectionAndPageGuidsAreExtracted() throws Exception {
        String sectionGuid = "{00112233-4455-6677-8899-AABBCCDDEEFF}";
        String pageGuid = "{10213243-5465-7687-98A9-BACBDCEDFE0F}";
        String stalePageGuid = "{20314253-6475-8697-A8B9-CADBECFD0E1F}";
        String pageSeriesGuid = "{30415263-7485-96A7-B8C9-DAEBFC0D1E2F}";
        String conflictPageGuid = "{20314253-6475-8697-A8B9-CADBECFD0E1F}";
        String entityGuid = "{40516273-8495-A6B7-C8D9-EAFB0C1D2E3F}";
        String sectionNodeGuid = "{50617283-94A5-B6C7-D8E9-FA0B1C2D3E4F}";
        String pageNodeGuid = "{60718293-A4B5-C6D7-E8F9-0A1B2C3D4E5F}";
        byte[] pageNodeGuidBytes = new byte[] {
                (byte) 0x93, (byte) 0x82, 0x71, 0x60, (byte) 0xb5, (byte) 0xa4, (byte) 0xd7,
                (byte) 0xc6, (byte) 0xe8, (byte) 0xf9, 0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f
        };
        byte[] guidBytes = sectionGuidBytes();
        byte[] pageGuidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        byte[] stalePageGuidBytes = new byte[] {
                0x53, 0x42, 0x31, 0x20, 0x75, 0x64, (byte) 0x97, (byte) 0x86,
                (byte) 0xa8, (byte) 0xb9, (byte) 0xca, (byte) 0xdb,
                (byte) 0xec, (byte) 0xfd, 0x0e, 0x1f
        };
        byte[] pageSeriesGuidBytes = new byte[] {
                0x63, 0x52, 0x41, 0x30, (byte) 0x85, 0x74, (byte) 0xa7, (byte) 0x96,
                (byte) 0xb8, (byte) 0xc9, (byte) 0xda, (byte) 0xeb,
                (byte) 0xfc, 0x0d, 0x1e, 0x2f
        };
        byte[] conflictPageGuidBytes = new byte[] {
                0x53, 0x42, 0x31, 0x20, 0x75, 0x64, (byte) 0x97, (byte) 0x86,
                (byte) 0xa8, (byte) 0xb9, (byte) 0xca, (byte) 0xdb,
                (byte) 0xec, (byte) 0xfd, 0x0e, 0x1f
        };
        byte[] entityGuidBytes = new byte[] {
                0x73, 0x62, 0x51, 0x40, (byte) 0x95, (byte) 0x84, (byte) 0xb7, (byte) 0xa6,
                (byte) 0xc8, (byte) 0xd9, (byte) 0xea, (byte) 0xfb,
                0x0c, 0x1d, 0x2e, 0x3f
        };
        byte[] sectionNodeGuidBytes = new byte[] {
                (byte) 0x83, 0x72, 0x61, 0x50, (byte) 0xa5, (byte) 0x94, (byte) 0xc7, (byte) 0xb6,
                (byte) 0xd8, (byte) 0xe9, (byte) 0xfa, 0x0b, 0x1c, 0x2d, 0x3e, 0x4f
        };
        RevisionStoreObject stalePageMetadata = object(id(1199),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(stalePageGuidBytes))), Collections.emptyList(),
                Collections.emptyList());
        RevisionStoreObject pageMetadata = object(id(1200),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(pageGuidBytes))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(stalePageMetadata, OneNoteJcid.PAGE_METADATA);
        setJcid(pageMetadata, OneNoteJcid.PAGE_METADATA);
        RevisionStoreCell pageCell = new RevisionStoreCell();
        pageCell.cellID = cell(12, 1200);
        pageCell.objectGroups.add(group(stalePageMetadata, pageMetadata));
        pageCell.rootDeclares.add(rootDeclare(pageMetadata.objectID));

        ExGuid sectionRootId = id(1201);
        ExGuid pageSeriesId = id(1203);
        RevisionStoreObject pageSeries = object(pageSeriesId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(pageSeriesGuidBytes))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(pageSeries, OneNoteJcid.PAGE_SERIES_NODE);
        ExGuid conflictPageId = id(1204);
        RevisionStoreObject conflictPage = object(conflictPageId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(conflictPageGuidBytes))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(conflictPage, OneNoteJcid.CONFLICT_PAGE_METADATA);
        ExGuid entityId = id(1205);
        RevisionStoreObject entity = object(entityId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(entityGuidBytes))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(entity, 0x7fff);
        ExGuid sectionNodeId = id(1206);
        RevisionStoreObject sectionNode = object(sectionNodeId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(sectionNodeGuidBytes))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(sectionNode, OneNoteJcid.SECTION_NODE);
        ExGuid pageNodeId = id(1207);
        RevisionStoreObject pageNode = object(pageNodeId,
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(pageNodeGuidBytes))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(pageNode, OneNoteJcid.PAGE_NODE);
        RevisionStoreObject sectionRoot = object(sectionRootId,
                propertySet(new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001C20,
                        arrayNumber(5)),
                        new PropertySpec(PropertyType.ObjectSpaceID, 0x20001D78, new NoData())),
                Arrays.asList(pageSeriesId, conflictPageId, entityId, sectionNodeId, pageNodeId),
                Collections.singletonList(pageCell.cellID));
        RevisionStoreObject sectionIdentity = object(id(1202),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001D94, bytes(guidBytes))), Collections.emptyList(),
                Collections.emptyList());
        RevisionStoreCell sectionCell = new RevisionStoreCell();
        sectionCell.objectGroups.add(group(sectionRoot, sectionIdentity, pageSeries,
                conflictPage, entity, sectionNode, pageNode));
        sectionCell.rootDeclares.add(rootDeclare(sectionRootId));

        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.dataRootCell = sectionCell;
        pkg.cells.add(pageCell);
        Metadata metadata = new Metadata();
        walk(pkg, metadata);

        assertArrayEquals(new String[] {sectionGuid}, metadata.getValues(OneNote.SECTION_GUID));
        assertArrayEquals(new String[] {pageGuid}, metadata.getValues(OneNote.PAGE_GUIDS));
        assertFalse(Arrays.asList(metadata.getValues(OneNote.SECTION_GUID))
                .contains(sectionNodeGuid));
        assertArrayEquals(new String[] {pageSeriesGuid},
                metadata.getValues(OneNote.PAGE_SERIES_GUIDS));
        assertArrayEquals(new String[] {conflictPageGuid},
                metadata.getValues(OneNote.CONFLICT_PAGE_GUIDS));
        assertArrayEquals(new String[] {pageNodeGuid},
                metadata.getValues(OneNote.PAGE_NODE_GUIDS));
        // a JCID with no GUID key (0x7fff here) contributes no GUID metadata
        for (String name : metadata.names()) {
            assertFalse(Arrays.asList(metadata.getValues(name)).contains(entityGuid), name);
        }
        String xml = walkXml(pkg);
        assertTrue(xml.contains("id=\"" + pageGuid + "\""));
        assertFalse(xml.contains("id=\"" + stalePageGuid + "\""));
    }

    @Test
    public void testWalksOtherFileNodeListWhenCellsAreMissing() throws Exception {
        byte[] guidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        String pageGuid = "{10213243-5465-7687-98A9-BACBDCEDFE0F}";
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.OtherFileNodeList.add(group(notebookGuidObject(1400, OneNoteJcid.PAGE_METADATA,
                guidBytes)));
        Metadata metadata = new Metadata();

        walk(pkg, metadata);

        assertArrayEquals(new String[] {pageGuid}, metadata.getValues(OneNote.PAGE_GUIDS));
    }

    @Test
    public void testFallbackWalkSkipsMalformedGroups() throws Exception {
        // a null group and a group without objects must be skipped, not crash the walk
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.OtherFileNodeList.add(null);
        RevisionStoreObjectGroup noObjects = group();
        noObjects.objects = null;
        pkg.OtherFileNodeList.add(noObjects);
        RevisionStoreObjectGroup withNullObject = group();
        withNullObject.objects.add(null);
        RevisionStoreObject noId = object(id(2401),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("no-id text"))), Collections.emptyList(),
                Collections.emptyList());
        noId.objectID = null;
        withNullObject.objects.add(noId);
        withNullObject.objects.add(object(id(2400),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("fallback text"))), Collections.emptyList(),
                Collections.emptyList()));
        pkg.OtherFileNodeList.add(withNullObject);
        Metadata metadata = new Metadata();

        String text = walk(pkg, metadata);

        assertTrue(text.contains("fallback text"));
        assertTrue(text.contains("no-id text"));
    }

    @Test
    public void testFileIdentityScannerHandlesNestedSetsArraysAndMalformedValues() {
        byte[] firstGuidBytes = sectionGuidBytes();
        PropertySet noProperties = new PropertySet();
        noProperties.rgData = new ArrayList<>();
        PropertySet noData = new PropertySet();
        noData.rgPrids = new PropertyID[0];
        // guid-free nested structures first: once the winning GUID is found, the scan stops,
        // so anything placed later would never be scanned
        PrtArrayOfPropertyValues guidFreeArray = new PrtArrayOfPropertyValues();
        guidFreeArray.data = new PropertySet[] {
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("nested"))), null, noProperties, noData
        };
        PropertySet deep = noProperties;
        for (int i = 0; i <= PropertySet.MAX_PROPERTY_NESTING; i++) {
            deep = propertySet(new PropertySpec(PropertyType.PropertySet, 0x24000001, deep));
        }
        PrtArrayOfPropertyValues winningArray = new PrtArrayOfPropertyValues();
        winningArray.data = new PropertySet[] {
                fileIdentitySet(firstGuidBytes), noProperties
        };
        PrtArrayOfPropertyValues emptyArray = new PrtArrayOfPropertyValues();
        PropertySet top = propertySet(
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001D94, bytes(new byte[17])),
                new PropertySpec(PropertyType.PropertySet, 0x24000002,
                        propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("nested")))),
                new PropertySpec(PropertyType.PropertySet, 0x1C001D94,
                        propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("nested")))),
                new PropertySpec(PropertyType.PropertySet, 0x24000003, new NoData()),
                new PropertySpec(PropertyType.ArrayOfPropertyValues, 0x24000004, guidFreeArray),
                new PropertySpec(PropertyType.ArrayOfPropertyValues, 0x24000005, emptyArray),
                new PropertySpec(PropertyType.ArrayOfPropertyValues, 0x24000007, new NoData()),
                new PropertySpec(PropertyType.PropertySet, 0x24000006, deep),
                new PropertySpec(PropertyType.ArrayOfPropertyValues, 0x24000008, winningArray),
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("after the guid")));
        MSOneStorePackage pkg = new MSOneStorePackage();

        pkg.collectFileIdentityGuids(null, 0);
        pkg.collectFileIdentityGuids(top, 0);

        assertEquals(Collections.singletonList("{00112233-4455-6677-8899-AABBCCDDEEFF}"),
                new ArrayList<>(sectionGuidValues(pkg)));

        MSOneStorePackage full = new MSOneStorePackage();
        fillGuidBudget(full, OneNoteGuidCollector.MAX_GUID_COUNT);
        full.collectFileIdentityGuids(fileIdentitySet(firstGuidBytes), 0);
        assertTrue(sectionGuidValues(full).isEmpty());
    }

    @Test
    public void testFileIdentityGroupScannerSkipsMalformedGroups() throws Exception {
        String sectionGuid = "{00112233-4455-6677-8899-AABBCCDDEEFF}";
        byte[] guidBytes = sectionGuidBytes();
        RevisionStoreObjectGroup noObjects = group();
        noObjects.objects = null;
        RevisionStoreObject noPropertySet = new RevisionStoreObject();
        RevisionStoreObject noObjectSpace = object(id(1450), fileIdentitySet(guidBytes),
                Collections.emptyList(), Collections.emptyList());
        noObjectSpace.propertySet.objectSpaceObjectPropSet = null;
        RevisionStoreObject valid = object(id(1451), fileIdentitySet(guidBytes),
                Collections.emptyList(), Collections.emptyList());
        List<RevisionStoreObjectGroup> groups = new ArrayList<>(Arrays.asList(
                null, noObjects, group((RevisionStoreObject) null), group(noPropertySet),
                group(noObjectSpace), group(valid)));
        MSOneStorePackage pkg = new MSOneStorePackage();

        pkg.collectFileIdentityGuidsFromGroups(null);
        pkg.collectFileIdentityGuidsFromGroups(groups);

        assertEquals(Collections.singletonList(sectionGuid), new ArrayList<>(sectionGuidValues(pkg)));
    }

    @Test
    public void testHeaderCellGuidAndMissingHeaderDataFallbacks() throws Exception {
        String sectionGuid = "{00112233-4455-6677-8899-AABBCCDDEEFF}";
        byte[] guidBytes = sectionGuidBytes();
        HeaderCell noObjectData = new HeaderCell();
        HeaderCell noBody = new HeaderCell();
        noBody.objectData = new ObjectSpaceObjectPropSet();
        for (HeaderCell header : Arrays.asList(null, noObjectData, noBody)) {
            MSOneStorePackage pkg = new MSOneStorePackage();
            pkg.headerCell = header;
            pkg.OtherFileNodeList.add(group(object(id(1460), fileIdentitySet(guidBytes),
                    Collections.emptyList(), Collections.emptyList())));
            pkg.cells.add(new RevisionStoreCell());
            Metadata metadata = new Metadata();
            walk(pkg, metadata);
            assertArrayEquals(new String[] {sectionGuid},
                    metadata.getValues(OneNote.SECTION_GUID));
        }

        HeaderCell header = new HeaderCell();
        header.objectData = new ObjectSpaceObjectPropSet();
        header.objectData.body = fileIdentitySet(guidBytes);
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.headerCell = header;
        pkg.cells.add(new RevisionStoreCell());
        Metadata metadata = new Metadata();
        walk(pkg, metadata);
        assertArrayEquals(new String[] {sectionGuid}, metadata.getValues(OneNote.SECTION_GUID));
    }

    @Test
    public void testSectionShortCircuitStopsWithBudgetToSpare() throws Exception {
        byte[] secondGuidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        // the winning identity is NOT the cap-filling value, so every stop below is
        // attributable to the sectionCollected short-circuit alone: removing the property
        // loop or nested-array operand sends the scan into the deep chain, removing the
        // group-loop operand enters the second group - either way the entry count grows
        PrtArrayOfPropertyValues array = new PrtArrayOfPropertyValues();
        array.data = new PropertySet[] {
                fileIdentitySet(sectionGuidBytes()), propertySet()
        };
        PropertySet deep = propertySet();
        for (int i = 0; i <= PropertySet.MAX_PROPERTY_NESTING; i++) {
            deep = propertySet(new PropertySpec(PropertyType.PropertySet, 0x24000006, deep));
        }
        RevisionStoreObject first = object(id(1474),
                propertySet(new PropertySpec(PropertyType.ArrayOfPropertyValues,
                                0x24000007, array),
                        new PropertySpec(PropertyType.PropertySet, 0x24000006, deep)),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreObject afterLimit = object(id(1475), fileIdentitySet(secondGuidBytes),
                Collections.emptyList(), Collections.emptyList());
        CountingMSOneStorePackage pkg = new CountingMSOneStorePackage();
        fillGuidBudget(pkg, OneNoteGuidCollector.MAX_GUID_COUNT - 5);
        pkg.dataRootCell = new RevisionStoreCell();
        pkg.dataRootCell.objectGroups.add(group(first));
        pkg.dataRootCell.objectGroups.add(group(afterLimit));
        Metadata metadata = new Metadata();

        walk(pkg, metadata);

        assertArrayEquals(new String[] {"{00112233-4455-6677-8899-AABBCCDDEEFF}"},
                metadata.getValues(OneNote.SECTION_GUID));
        assertEquals(2, pkg.identityScanEntries);
        assertEquals(0, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("Capping OneNote GUID metadata")).count());
    }

    @Test
    public void testSectionIdentityScanStopsAtGuidLimit() throws Exception {
        byte[] firstGuidBytes = sectionGuidBytes();
        byte[] secondGuidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        PrtArrayOfPropertyValues array = new PrtArrayOfPropertyValues();
        array.data = new PropertySet[] {
                fileIdentitySet(firstGuidBytes), fileIdentitySet(secondGuidBytes)
        };
        RevisionStoreObject withArray = object(id(1470),
                propertySet(new PropertySpec(PropertyType.ArrayOfPropertyValues,
                        0x24000007, array)), Collections.emptyList(), Collections.emptyList());
        RevisionStoreObject afterLimit = object(id(1471), fileIdentitySet(secondGuidBytes),
                Collections.emptyList(), Collections.emptyList());
        CountingMSOneStorePackage pkg = new CountingMSOneStorePackage();
        fillGuidBudget(pkg, OneNoteGuidCollector.MAX_GUID_COUNT - 1);
        pkg.dataRootCell = new RevisionStoreCell();
        pkg.dataRootCell.objectGroups.add(group(withArray));
        pkg.dataRootCell.objectGroups.add(group(afterLimit));
        Metadata metadata = new Metadata();

        walk(pkg, metadata);

        assertArrayEquals(new String[] {"{00112233-4455-6677-8899-AABBCCDDEEFF}"},
                metadata.getValues(OneNote.SECTION_GUID));
        // exact-work check: the nested array's first element wins, so the scan must not
        // enter the second element, the rest of the property list, or the second group
        assertEquals(2, pkg.identityScanEntries);
        assertEquals(1, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("Capping OneNote GUID metadata")).count());
    }

    @Test
    public void testSectionScanStopsAtLimitWithExactWork() throws Exception {
        byte[] secondGuidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        // a nested property-set chain deeper than MAX_PROPERTY_NESTING: isFull and
        // sectionCollected both fire at the winning identity here, so the chain is reached
        // only when BOTH stop conditions are removed (removing one leaves the other
        // stopping the scan); single-operand attribution is covered by
        // testSectionShortCircuitStopsWithBudgetToSpare
        PropertySet deep = propertySet();
        for (int i = 0; i <= PropertySet.MAX_PROPERTY_NESTING; i++) {
            deep = propertySet(new PropertySpec(PropertyType.PropertySet, 0x24000006, deep));
        }
        RevisionStoreObject first = object(id(1472),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C001D94, bytes(sectionGuidBytes())),
                        new PropertySpec(PropertyType.PropertySet, 0x24000006, deep)),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreObject afterLimit = object(id(1473), fileIdentitySet(secondGuidBytes),
                Collections.emptyList(), Collections.emptyList());
        CountingMSOneStorePackage pkg = new CountingMSOneStorePackage();
        fillGuidBudget(pkg, OneNoteGuidCollector.MAX_GUID_COUNT - 1);
        pkg.dataRootCell = new RevisionStoreCell();
        pkg.dataRootCell.objectGroups.add(group(first, afterLimit));
        Metadata metadata = new Metadata();

        walk(pkg, metadata);

        assertArrayEquals(new String[] {"{00112233-4455-6677-8899-AABBCCDDEEFF}"},
                metadata.getValues(OneNote.SECTION_GUID));
        assertEquals(1, pkg.identityScanEntries);
        assertEquals(1, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("Capping OneNote GUID metadata")).count());
    }

    @Test
    public void testSectionGuidSourcePrecedence() throws Exception {
        byte[] dataRootBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        byte[] fallbackBytes = new byte[] {
                0x63, 0x52, 0x41, 0x30, (byte) 0x85, 0x74, (byte) 0xa7, (byte) 0x96,
                (byte) 0xb8, (byte) 0xc9, (byte) 0xda, (byte) 0xeb,
                (byte) 0xfc, 0x0d, 0x1e, 0x2f
        };

        // the header wins over both lower-priority sources
        MSOneStorePackage withHeader = new MSOneStorePackage();
        withHeader.headerCell = new HeaderCell();
        withHeader.headerCell.objectData = new ObjectSpaceObjectPropSet();
        withHeader.headerCell.objectData.body = fileIdentitySet(sectionGuidBytes());
        withHeader.dataRootCell = new RevisionStoreCell();
        withHeader.dataRootCell.objectGroups.add(group(object(id(1500),
                fileIdentitySet(dataRootBytes), Collections.emptyList(),
                Collections.emptyList())));
        withHeader.OtherFileNodeList.add(group(object(id(1501),
                fileIdentitySet(fallbackBytes), Collections.emptyList(),
                Collections.emptyList())));
        Metadata headerMetadata = new Metadata();
        walk(withHeader, headerMetadata);
        assertEquals("{00112233-4455-6677-8899-AABBCCDDEEFF}",
                headerMetadata.get(OneNote.SECTION_GUID));

        // without a usable header, the data root wins over the OtherFileNodeList fallback
        MSOneStorePackage withoutHeader = new MSOneStorePackage();
        withoutHeader.dataRootCell = new RevisionStoreCell();
        withoutHeader.dataRootCell.objectGroups.add(group(object(id(1502),
                fileIdentitySet(dataRootBytes), Collections.emptyList(),
                Collections.emptyList())));
        withoutHeader.OtherFileNodeList.add(group(object(id(1503),
                fileIdentitySet(fallbackBytes), Collections.emptyList(),
                Collections.emptyList())));
        Metadata fallbackMetadata = new Metadata();
        walk(withoutHeader, fallbackMetadata);
        assertEquals("{10213243-5465-7687-98A9-BACBDCEDFE0F}",
                fallbackMetadata.get(OneNote.SECTION_GUID));

        // a scan entered after the section GUID was found must stop at the group check
        // (budget not full, so the short-circuit is the sectionCollected operand): the
        // assertion pins the collector at exactly one SECTION value, so collecting the
        // fallback GUID here would fail
        withHeader.collectFileIdentityGuidsFromGroups(withHeader.OtherFileNodeList);
        assertEquals(Collections.singleton("{00112233-4455-6677-8899-AABBCCDDEEFF}"),
                sectionGuidValues(withHeader));
        assertEquals("{00112233-4455-6677-8899-AABBCCDDEEFF}",
                headerMetadata.get(OneNote.SECTION_GUID));
    }

    @Test
    public void testCellRootsDeduplicatesAndCapsRoots() throws Exception {
        RevisionStoreCell cell = new RevisionStoreCell();
        RevisionStoreObject textRoot = object(id(2100), propertySet(
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("root text"))), Collections.emptyList(),
                Collections.emptyList());
        cell.rootDeclares.add(rootDeclare(textRoot.objectID));
        cell.rootDeclares.add(rootDeclare(textRoot.objectID));
        List<RevisionStoreObject> roots = new ArrayList<>();
        roots.add(textRoot);
        for (int i = 0; i <= MSOneStorePackage.MAX_CELL_ROOTS; i++) {
            RevisionStoreObject root = object(id(2101 + i), propertySet(),
                    Collections.emptyList(), Collections.emptyList());
            roots.add(root);
            cell.rootDeclares.add(rootDeclare(root.objectID));
        }
        cell.objectGroups.add(group(roots.toArray(new RevisionStoreObject[0])));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();

        String xml = walk(pkg, metadata);

        assertTrue(xml.contains("root text"));
        assertEquals(1, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("more than " + MSOneStorePackage.MAX_CELL_ROOTS
                        + " root objects")).count());
    }

    @Test
    public void testCellRootCapTruncationStillWalksContentRoot() throws Exception {
        // the cap must not silence the walk-everything fallback: 10,000 blob-only roots
        // fill it, so the real content root is declare #10,001 and gets truncated
        RevisionStoreCell cell = new RevisionStoreCell();
        List<RevisionStoreObject> roots = new ArrayList<>();
        for (int i = 0; i < MSOneStorePackage.MAX_CELL_ROOTS; i++) {
            RevisionStoreObject blob = new RevisionStoreObject();
            blob.objectID = id(2200 + i);
            roots.add(blob);
            cell.rootDeclares.add(rootDeclare(blob.objectID));
        }
        RevisionStoreObject contentRoot = object(id(2199),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("root text"))), Collections.emptyList(),
                Collections.emptyList());
        roots.add(contentRoot);
        cell.rootDeclares.add(rootDeclare(contentRoot.objectID));
        cell.objectGroups.add(group(roots.toArray(new RevisionStoreObject[0])));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();

        String xml = walk(pkg, metadata);

        assertTrue(xml.contains("root text"));
        assertEquals(1, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("more than " + MSOneStorePackage.MAX_CELL_ROOTS
                        + " root objects; ignoring the rest")).count());
        assertEquals(1, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("more than " + MSOneStorePackage.MAX_CELL_ROOTS
                        + " root objects; walking all objects")).count());
    }

    @Test
    public void testCellRootsDeduplicatesRootDeclares() throws Exception {
        // two declares of the same object plus MAX_CELL_ROOTS - 1 distinct roots fit the cap
        // exactly when deduplicated; without dedupe the duplicate consumes a slot and the
        // last distinct root is truncated, so the absent-truncation assertion fails
        RevisionStoreCell cell = new RevisionStoreCell();
        RevisionStoreObject textRoot = object(id(2300), propertySet(
                new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("root text"))), Collections.emptyList(),
                Collections.emptyList());
        cell.rootDeclares.add(rootDeclare(textRoot.objectID));
        cell.rootDeclares.add(rootDeclare(textRoot.objectID));
        List<RevisionStoreObject> roots = new ArrayList<>();
        roots.add(textRoot);
        for (int i = 0; i < MSOneStorePackage.MAX_CELL_ROOTS - 1; i++) {
            RevisionStoreObject root = object(id(2301 + i), propertySet(),
                    Collections.emptyList(), Collections.emptyList());
            roots.add(root);
            cell.rootDeclares.add(rootDeclare(root.objectID));
        }
        cell.objectGroups.add(group(roots.toArray(new RevisionStoreObject[0])));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();

        String xml = walk(pkg, metadata);

        assertTrue(xml.contains("root text"));
        assertEquals(0, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("more than " + MSOneStorePackage.MAX_CELL_ROOTS
                        + " root objects")).count());
    }

    @Test
    public void testPageGuidLookupFollowsReferencesAndFallbackRoots() throws Exception {
        String pageGuid = "{10213243-5465-7687-98A9-BACBDCEDFE0F}";
        byte[] guidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        MSOneStorePackage pkg = new MSOneStorePackage();
        RevisionStoreObject page = notebookGuidObject(1480, OneNoteJcid.PAGE_METADATA, guidBytes);
        RevisionStoreObject parent = object(id(1481),
                propertySet(new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData()),
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("non-reference property"))),
                Collections.singletonList(page.objectID), Collections.emptyList());
        Map<ExGuid, RevisionStoreObject> objectsById = new HashMap<>();
        objectsById.put(parent.objectID, parent);
        objectsById.put(page.objectID, page);
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(parent, page));
        cell.rootDeclares.add(rootDeclare(parent.objectID));

        assertEquals(pageGuid, pkg.findPageGuid(cell, objectsById));

        RevisionStoreCell fallbackCell = new RevisionStoreCell();
        RevisionStoreObjectGroup noObjects = group();
        noObjects.objects = null;
        fallbackCell.objectGroups.add(null);
        fallbackCell.objectGroups.add(noObjects);
        fallbackCell.objectGroups.add(group(parent, page));
        assertEquals(pageGuid, pkg.findPageGuid(fallbackCell, objectsById));
        fallbackCell.rootDeclares.add(rootDeclare(id(1482)));
        assertEquals(pageGuid, pkg.findPageGuid(fallbackCell, objectsById));
        fallbackCell.rootDeclares = null;
        assertEquals(pageGuid, pkg.findPageGuid(fallbackCell, objectsById));
        assertNull(pkg.findPageGuid(null, objectsById));
        RevisionStoreCell noGroups = new RevisionStoreCell();
        noGroups.objectGroups = null;
        assertNull(pkg.findPageGuid(noGroups, objectsById));

        RevisionStoreObject resolvedRoot = object(id(1483),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, text("resolved root without page GUID"))),
                Collections.emptyList(), Collections.emptyList());
        Map<ExGuid, RevisionStoreObject> roots = new HashMap<>(objectsById);
        roots.put(resolvedRoot.objectID, resolvedRoot);
        RevisionStoreCell noUnreferencedFallback = new RevisionStoreCell();
        noUnreferencedFallback.objectGroups.add(group(resolvedRoot, page));
        noUnreferencedFallback.rootDeclares.add(rootDeclare(resolvedRoot.objectID));
        assertNull(pkg.findPageGuid(noUnreferencedFallback, roots));
        noUnreferencedFallback.rootDeclares.clear();
        noUnreferencedFallback.rootDeclares.add(rootDeclare(id(1482)));
        noUnreferencedFallback.rootDeclares.add(rootDeclare(resolvedRoot.objectID));
        assertNull(pkg.findPageGuid(noUnreferencedFallback, roots));
        RevisionStoreObject rootWithoutPropertySet = new RevisionStoreObject();
        rootWithoutPropertySet.objectID = id(1486);
        Map<ExGuid, RevisionStoreObject> noPropertyRoots = new HashMap<>(objectsById);
        noPropertyRoots.put(rootWithoutPropertySet.objectID, rootWithoutPropertySet);
        RevisionStoreCell noPropertyRoot = new RevisionStoreCell();
        noPropertyRoot.objectGroups.add(group(rootWithoutPropertySet, page));
        noPropertyRoot.rootDeclares.add(rootDeclare(rootWithoutPropertySet.objectID));
        assertNull(pkg.findPageGuid(noPropertyRoot, noPropertyRoots));
        RevisionStoreCell emptyFallback = new RevisionStoreCell();
        assertNull(pkg.findPageGuid(emptyFallback, objectsById));
        RevisionStoreCell failedFallback = new RevisionStoreCell();
        failedFallback.objectGroups.add(group());
        failedFallback.objectGroups.add(group(rootWithoutPropertySet));
        assertNull(pkg.findPageGuid(failedFallback, objectsById));

        assertNull(pkg.findPageGuid((RevisionStoreObject) null, objectsById,
                new HashSet<>(), 0));
        assertNull(pkg.findPageGuid(page, objectsById,
                new HashSet<>(Collections.singleton(page.objectID)), 0));
        assertNull(pkg.findPageGuid(page, objectsById, new HashSet<>(), 1000));
        RevisionStoreObject malformedPage = notebookGuidObject(1487,
                OneNoteJcid.PAGE_METADATA, new byte[17]);
        assertNull(pkg.findPageGuid(malformedPage, objectsById, new HashSet<>(), 0));
        RevisionStoreObject noChildReference = object(id(1488),
                propertySet(new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData())),
                Collections.emptyList(), Collections.emptyList());
        assertNull(pkg.findPageGuid(noChildReference, objectsById, new HashSet<>(), 0));
        RevisionStoreObject noPropertySet = new RevisionStoreObject();
        assertNull(pkg.findPageGuid(noPropertySet, objectsById, new HashSet<>(), 0));
        RevisionStoreObject noObjectSpace = object(id(1484),
                propertySet(new PropertySpec(PropertyType.NoData, 0x20001D78, new NoData())),
                Collections.emptyList(), Collections.emptyList());
        noObjectSpace.propertySet.objectSpaceObjectPropSet = null;
        assertNull(pkg.findPageGuid(noObjectSpace, objectsById, new HashSet<>(), 0));
        RevisionStoreObject noObjectId = notebookGuidObject(1485,
                OneNoteJcid.PAGE_METADATA, guidBytes);
        noObjectId.objectID = null;
        assertEquals(pageGuid, pkg.findPageGuid(noObjectId, objectsById, new HashSet<>(), 0));
    }

    @Test
    public void testPageEmissionBuildsOneObjectIndex() throws Exception {
        String pageGuid = "{10213243-5465-7687-98A9-BACBDCEDFE0F}";
        byte[] guidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        CountingMSOneStorePackage pkg = new CountingMSOneStorePackage();
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(notebookGuidObject(1498, OneNoteJcid.PAGE_METADATA,
                guidBytes)));
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        ToXMLContentHandler xml = new ToXMLContentHandler();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(xml, metadata, context);
        xhtml.startDocument();

        pkg.emitPage(cell, new OneNoteTreeWalkerOptions(), metadata, xhtml);

        xhtml.endDocument();
        assertEquals(1, pkg.objectIndexBuilds);
        assertTrue(xml.toString().contains("id=\"" + pageGuid + "\""));
    }

    @Test
    public void testNotebookManagementGuidRejectsMissingAndMalformedProperties() throws Exception {
        MSOneStorePackage pkg = new MSOneStorePackage();
        assertNull(pkg.notebookManagementEntityGuid(null));
        assertNull(pkg.notebookManagementEntityGuid(new RevisionStoreObject()));

        RevisionStoreObject noObjectSpace = object(id(1490),
                propertySet(new PropertySpec(PropertyType.NoData, 0x20001D78, new NoData())),
                Collections.emptyList(), Collections.emptyList());
        noObjectSpace.propertySet.objectSpaceObjectPropSet = null;
        assertNull(pkg.notebookManagementEntityGuid(noObjectSpace));

        byte[] validGuid = sectionGuidBytes();
        RevisionStoreObject unrelated = object(id(1491),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C003498, bytes(validGuid))), Collections.emptyList(),
                Collections.emptyList());
        assertNull(pkg.notebookManagementEntityGuid(unrelated));
        RevisionStoreObject wrongType = object(id(1492),
                propertySet(new PropertySpec(PropertyType.NoData, 0x1C001C30, new NoData())),
                Collections.emptyList(), Collections.emptyList());
        assertNull(pkg.notebookManagementEntityGuid(wrongType));
        RevisionStoreObject malformed = object(id(1493),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(new byte[17]))), Collections.emptyList(),
                Collections.emptyList());
        assertNull(pkg.notebookManagementEntityGuid(malformed));
        RevisionStoreObject valid = notebookGuidObject(1494, OneNoteJcid.PAGE_METADATA,
                validGuid);
        assertEquals("{00112233-4455-6677-8899-AABBCCDDEEFF}",
                pkg.notebookManagementEntityGuid(valid));
        pkg.recordEntityGuid(null, OneNoteJcid.PAGE_METADATA);
    }

    @Test
    public void testFssJcidIndexRequiresCompleteJcid() throws Exception {
        assertEquals(-1, MSOneStorePackage.jcidIndex(null));
        RevisionStoreObject object = new RevisionStoreObject();
        assertEquals(-1, MSOneStorePackage.jcidIndex(object));
        object.jcid = new JCIDObject(null, emptyObjectData());
        object.jcid.jcid = null;
        assertEquals(-1, MSOneStorePackage.jcidIndex(object));
        object.jcid = new JCIDObject(null, emptyObjectData());
        object.jcid.jcid.index = OneNoteJcid.PAGE_METADATA;
        assertEquals(OneNoteJcid.PAGE_METADATA, MSOneStorePackage.jcidIndex(object));
    }

    @Test
    public void testGuidCapWarningIsPublishedAsParseWarning() throws Exception {
        MSOneStorePackage pkg = new MSOneStorePackage();
        fillGuidBudget(pkg, OneNoteGuidCollector.MAX_GUID_COUNT);
        pkg.guidCollector().add(OneNoteGuidCollector.Category.PAGE, "overflow-1");

        Metadata metadata = new Metadata();
        walk(pkg, metadata);
        assertEquals(OneNoteGuidCollector.MAX_GUID_COUNT,
                metadata.getValues(OneNote.PAGE_GUIDS).length);
        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertEquals(1, Arrays.stream(warnings)
                .filter(warning -> warning.contains("Capping OneNote GUID metadata")).count());
    }

    @Test
    public void testGuidCapWarningSurvivesSuppressedParseWarnings() throws Exception {
        MSOneStorePackage pkg = new MSOneStorePackage();
        for (int i = 0; i < 101; i++) {
            pkg.recordParseWarning("warning " + i);
        }
        fillGuidBudget(pkg, OneNoteGuidCollector.MAX_GUID_COUNT - 1);
        RevisionStoreObject pageMetadata = notebookGuidObject(1304, OneNoteJcid.PAGE_METADATA,
                new byte[] {
                        0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                        (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                        (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
                });
        RevisionStoreCell pageCell = new RevisionStoreCell();
        pageCell.objectGroups.add(group(pageMetadata));
        pageCell.rootDeclares.add(rootDeclare(pageMetadata.objectID));
        pkg.cells.add(pageCell);
        Metadata metadata = new Metadata();

        walk(pkg, metadata);

        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertTrue(Arrays.stream(warnings)
                .anyMatch(warning -> warning.contains("Additional OneNote parse warnings were suppressed")));
        // the cap signal is exempt from the budget the generic notice consumed
        assertEquals(1, Arrays.stream(warnings)
                .filter(warning -> warning.contains("Capping OneNote GUID metadata")).count());
        assertEquals(OneNoteGuidCollector.MAX_GUID_COUNT,
                metadata.getValues(OneNote.PAGE_GUIDS).length);
    }

    @Test
    public void testGuidsCollectedBeforeWalkFailureArePublished() throws Exception {
        String sectionGuid = "{00112233-4455-6677-8899-AABBCCDDEEFF}";
        String pageGuid = "{10213243-5465-7687-98A9-BACBDCEDFE0F}";
        RevisionStoreObject sectionIdentity = object(id(1302),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001D94, bytes(sectionGuidBytes()))), Collections.emptyList(),
                Collections.emptyList());
        RevisionStoreObject pageMetadata = object(id(1303),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(new byte[] {
                                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
                        }))), Collections.emptyList(),
                Collections.emptyList());
        setJcid(pageMetadata, OneNoteJcid.PAGE_METADATA);
        RevisionStoreCell validCell = new RevisionStoreCell();
        validCell.objectGroups.add(group(pageMetadata));
        validCell.rootDeclares.add(rootDeclare(pageMetadata.objectID));
        RevisionStoreCell damagedCell = new RevisionStoreCell();
        damagedCell.objectGroups = null;

        // deliberate failure seam: a future null guard inside indexObjectsById must not
        // turn this test green for the wrong reason
        MSOneStorePackage pkg = new MSOneStorePackage() {
            @Override
            Map<ExGuid, RevisionStoreObject> indexObjectsById(
                    List<RevisionStoreObjectGroup> objectGroups) {
                if (objectGroups == null) {
                    throw new IllegalStateException("forced index failure");
                }
                return super.indexObjectsById(objectGroups);
            }
        };
        pkg.OtherFileNodeList.add(group(sectionIdentity));
        pkg.cells.add(validCell);
        pkg.cells.add(damagedCell);
        Metadata metadata = new Metadata();

        assertThrows(IllegalStateException.class, () -> walk(pkg, metadata));
        assertArrayEquals(new String[] {sectionGuid}, metadata.getValues(OneNote.SECTION_GUID));
        assertArrayEquals(new String[] {pageGuid}, metadata.getValues(OneNote.PAGE_GUIDS));
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
        // reverse-lexical page GUIDs: page one's GUID sorts after page two's, so the sorted
        // bag order cannot be mistaken for document order
        attachPageGuid(pageOneCell, 3100, (byte) 0x60);
        attachPageGuid(pageTwoCell, 3200, (byte) 0x50);

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
        // div ids are attributes, so the pairing assertions need the XML output
        String text = walkXml(pkg, metadata);
        assertTrue(text.indexOf("page one") >= 0);
        assertTrue(text.indexOf("page two") >= 0);
        assertTrue(text.indexOf("page one") < text.indexOf("page two"));
        assertFalse(text.contains("old page one"));
        assertTrue(text.contains("unrelated"));
        // each page div carries its own page GUID, in document order
        int idPageOne = text.indexOf("id=\"{60000000-0000-0000-0000-000000000000}\"");
        int idPageTwo = text.indexOf("id=\"{50000000-0000-0000-0000-000000000000}\"");
        assertTrue(idPageOne >= 0);
        assertTrue(idPageTwo >= 0);
        assertTrue(idPageOne < text.indexOf("page one"));
        assertTrue(text.indexOf("page one") < idPageTwo);
        assertTrue(idPageTwo < text.indexOf("page two"));
        // the published bag is sorted, not document-ordered
        assertArrayEquals(new String[] {"{50000000-0000-0000-0000-000000000000}",
                "{60000000-0000-0000-0000-000000000000}"},
                metadata.getValues(OneNote.PAGE_GUIDS));
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
    public void testDualRoleAuthorSkipsMissingPropertySetAndMalformedValue() throws Exception {
        RevisionStoreObject noPropertySet = new RevisionStoreObject();
        noPropertySet.objectID = id(712);
        RevisionStoreObject malformedAuthor = object(id(714), propertySet(
                        new PropertySpec(PropertyType.NoData, 0x1C001D75, new NoData())),
                Collections.emptyList(), Collections.emptyList());
        RevisionStoreObject nullSetRoot = object(id(713), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData()),
                        new PropertySpec(PropertyType.ObjectID, 0x20001D79, new NoData())),
                Arrays.asList(noPropertySet.objectID, noPropertySet.objectID),
                Collections.emptyList());
        RevisionStoreObject malformedRoot = object(id(715), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData()),
                        new PropertySpec(PropertyType.ObjectID, 0x20001D79, new NoData())),
                Arrays.asList(malformedAuthor.objectID, malformedAuthor.objectID),
                Collections.emptyList());
        RevisionStoreObject noObjectSpace = object(id(716), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData())),
                Collections.emptyList(), Collections.emptyList());
        noObjectSpace.propertySet.objectSpaceObjectPropSet = null;
        RevisionStoreObject emptySpaceRoot = object(id(717), propertySet(
                        new PropertySpec(PropertyType.ObjectID, 0x20001D78, new NoData()),
                        new PropertySpec(PropertyType.ObjectID, 0x20001D79, new NoData())),
                Arrays.asList(noObjectSpace.objectID, noObjectSpace.objectID),
                Collections.emptyList());
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.objectGroups.add(group(nullSetRoot, noPropertySet, malformedRoot, malformedAuthor,
                emptySpaceRoot, noObjectSpace));
        cell.rootDeclares.add(rootDeclare(nullSetRoot.objectID));
        cell.rootDeclares.add(rootDeclare(malformedRoot.objectID));
        cell.rootDeclares.add(rootDeclare(emptySpaceRoot.objectID));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();

        walk(pkg, metadata);

        assertEquals(0, metadata.getValues(TikaCoreProperties.CREATOR).length);
    }

    @Test
    public void testHeaderGuidWinsOverDataRootScan() throws Exception {
        byte[] dataRootGuidBytes = new byte[] {
                0x43, 0x32, 0x21, 0x10, 0x65, 0x54, (byte) 0x87, 0x76,
                (byte) 0x98, (byte) 0xa9, (byte) 0xba, (byte) 0xcb,
                (byte) 0xdc, (byte) 0xed, (byte) 0xfe, 0x0f
        };
        HeaderCell header = new HeaderCell();
        header.objectData = new ObjectSpaceObjectPropSet();
        header.objectData.body = fileIdentitySet(sectionGuidBytes());
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.headerCell = header;
        pkg.dataRootCell = new RevisionStoreCell();
        pkg.dataRootCell.objectGroups.add(group(object(id(1500),
                fileIdentitySet(dataRootGuidBytes), Collections.emptyList(),
                Collections.emptyList())));
        Metadata metadata = new Metadata();

        walk(pkg, metadata);

        assertEquals("{00112233-4455-6677-8899-AABBCCDDEEFF}",
                metadata.get(OneNote.SECTION_GUID));
    }

    @Test
    public void testWalksCellWithMissingRootDeclares() throws Exception {
        RevisionStoreCell cell = new RevisionStoreCell();
        cell.rootDeclares = null;
        cell.objectGroups.add(group(object(id(630), propertySet(
                        new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                                0x1C003498, text("late body text"))),
                Collections.emptyList(), Collections.emptyList())));
        MSOneStorePackage pkg = new MSOneStorePackage();
        pkg.cells.add(cell);
        Metadata metadata = new Metadata();

        String xml = walk(pkg, metadata);

        assertTrue(xml.contains("late body text"));
        assertTrue(Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .anyMatch(warning -> warning.contains("could not be resolved; walking all objects")));
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

    private static class CountingMSOneStorePackage extends MSOneStorePackage {
        private int objectIndexBuilds;
        private int identityScanEntries;

        @Override
        Map<ExGuid, RevisionStoreObject> indexObjectsById(
                List<RevisionStoreObjectGroup> objectGroups) {
            objectIndexBuilds++;
            return super.indexObjectsById(objectGroups);
        }

        @Override
        void collectFileIdentityGuids(PropertySet propertySet, int depth) {
            identityScanEntries++;
            super.collectFileIdentityGuids(propertySet, depth);
        }
    }

    private static byte[] sectionGuidBytes() {
        return new byte[] {
                0x33, 0x22, 0x11, 0x00, 0x55, 0x44, 0x77, 0x66,
                (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
                (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
        };
    }

    private static PropertySet fileIdentitySet(byte[] value) {
        return propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                0x1C001D94, bytes(value)));
    }

    private static RevisionStoreObject notebookGuidObject(int objectId, int jcid, byte[] value)
            throws Exception {
        RevisionStoreObject object = object(id(objectId),
                propertySet(new PropertySpec(PropertyType.FourBytesOfLengthFollowedByData,
                        0x1C001C30, bytes(value))), Collections.emptyList(), Collections.emptyList());
        setJcid(object, jcid);
        return object;
    }

    private static void fillGuidBudget(MSOneStorePackage pkg, int count) {
        for (int i = 0; i < count; i++) {
            pkg.guidCollector().add(OneNoteGuidCollector.Category.PAGE, "budget-" + i);
        }
    }

    private static Set<String> sectionGuidValues(MSOneStorePackage pkg) {
        return pkg.guidCollector().values(OneNoteGuidCollector.Category.SECTION);
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
        return walkXml(pkg, new Metadata());
    }

    private static String walkXml(MSOneStorePackage pkg, Metadata metadata) throws Exception {
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
        setJcid(outline, OneNoteStructureJcid.OUTLINE_NODE);
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
        setJcid(numberList, OneNoteStructureJcid.NUMBER_LIST_NODE);
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
        setJcid(listItem, OneNoteStructureJcid.OUTLINE_ELEMENT_NODE);
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

    /** Adds a PAGE_METADATA guid object as a second root so the page div carries its id. */
    private static void attachPageGuid(RevisionStoreCell cell, int objectId, byte firstGroupByte)
            throws Exception {
        byte[] guidBytes = new byte[16];
        guidBytes[3] = firstGroupByte;
        RevisionStoreObject pageMetadata = notebookGuidObject(objectId,
                OneNoteJcid.PAGE_METADATA, guidBytes);
        cell.objectGroups.add(group(pageMetadata));
        cell.rootDeclares.add(rootDeclare(pageMetadata.objectID));
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

    // PropertyID.type and .value are set independently here; the real deserializer derives
    // the type from the value's high bits, so some fixtures are deliberately impossible inputs
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
