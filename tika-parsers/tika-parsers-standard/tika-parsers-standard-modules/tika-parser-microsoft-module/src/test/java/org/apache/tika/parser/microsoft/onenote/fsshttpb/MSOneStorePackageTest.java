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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
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
import org.apache.tika.sax.XHTMLContentHandler;

public class MSOneStorePackageTest {

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

        String text = walk(pkg);
        assertTrue(text.indexOf("page one") < text.indexOf("page two"));
        assertFalse(text.contains("old page one"));
        assertTrue(text.contains("unrelated"));
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

        assertTrue(walk(pkg).contains("fallback content"));
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
    }

    @Test
    public void testBlobOnlyRootStillFallsBackToOtherObjects() throws Exception {
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

        assertTrue(walk(pkg).contains("fallback content"));
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
                PropertySet.class, List.class, int[].class, List.class, int[].class, List.class);
        collectActions.setAccessible(true);
        List<Object> actions = new ArrayList<>();
        collectActions.invoke(new MSOneStorePackage(),
                hugeArrayRoot.propertySet.objectSpaceObjectPropSet.body,
                Collections.emptyList(), new int[]{0}, Collections.emptyList(), new int[]{0},
                actions);
        assertTrue(actions.isEmpty());

        PropertySet alignedSet = propertySet(
                new PropertySpec(PropertyType.ArrayOfObjectIDs, 0x24001D5F,
                        arrayNumber(100001)),
                new PropertySpec(PropertyType.ObjectID, 0x24001D5F, new NoData()));
        actions.clear();
        collectActions.invoke(new MSOneStorePackage(), alignedSet,
                Collections.nCopies(100001, id(601)), new int[]{0}, Collections.emptyList(),
                new int[]{0}, actions);
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
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new ToTextContentHandler(writer), metadata, new ParseContext());
        xhtml.startDocument();
        Object result = walkObject.invoke(new MSOneStorePackage(), hugeArrayRoot,
                new java.util.HashMap<>(), new java.util.HashSet<>(), null,
                new OneNoteTreeWalkerOptions(), metadata, xhtml, 1000);
        xhtml.endDocument();
        assertFalse((Boolean) result);
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
