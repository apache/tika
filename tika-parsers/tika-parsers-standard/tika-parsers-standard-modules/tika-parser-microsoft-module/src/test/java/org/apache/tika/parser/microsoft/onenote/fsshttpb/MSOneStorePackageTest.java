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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.onenote.OneNoteParser;
import org.apache.tika.parser.microsoft.onenote.OneNoteTreeWalkerOptions;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.ArrayNumber;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.IProperty;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.NoData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtArrayOfPropertyValues;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.DataElement;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.FileDataObject;
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
                new XHTMLContentHandler(new ToTextContentHandler(), metadata),
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
        assertEquals("Иван Петров", metadata.get(OneNoteParser.ONE_NOTE_PREFIX + "originalAuthors"));
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
        assertEquals("Single Author", metadata.get(OneNoteParser.ONE_NOTE_PREFIX + "originalAuthors"));
        assertEquals("Single Author", metadata.get(OneNoteParser.ONE_NOTE_PREFIX + "mostRecentAuthors"));
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
        XHTMLContentHandler xhtml = new XHTMLContentHandler(recordingHandler, metadata);
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
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new ToTextContentHandler(writer), metadata);
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
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new ToTextContentHandler(writer), metadata);
        xhtml.startDocument();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata, xhtml, context);
        xhtml.endDocument();
        return writer.toString();
    }

    private static String walkXml(MSOneStorePackage pkg) throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        ToXMLContentHandler xml = new ToXMLContentHandler();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(xml, metadata);
        xhtml.startDocument();
        pkg.walkTree(new OneNoteTreeWalkerOptions(), metadata, xhtml, context);
        xhtml.endDocument();
        return xml.toString();
    }

    private static int count(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
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
