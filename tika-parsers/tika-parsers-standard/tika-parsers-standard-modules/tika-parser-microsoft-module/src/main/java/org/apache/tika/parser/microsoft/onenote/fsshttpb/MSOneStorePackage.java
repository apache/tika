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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OneNote;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.onenote.OneNotePropertyEnum;
import org.apache.tika.parser.microsoft.onenote.OneNoteTreeWalkerOptions;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.ArrayNumber;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.EightBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.FourBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.IProperty;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtArrayOfPropertyValues;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestRootDeclare;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreCell;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObjectGroup;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexCellMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexRevisionMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.HeaderCell;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.unsigned.Unsigned;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class MSOneStorePackage {
    /**
     * See spec MS-ONE - 2.3.1 - TIME32 - epoch of jan 1 1980 UTC.
     * So we create this offset used to calculate number of seconds between this and the Instant
     * .EPOCH.
     */
    private static final long TIME32_EPOCH_DIFF_1980;
    /**
     * See spec MS-DTYP - 2.3.3 - DATETIME dates are based on epoch of jan 1 1601 UTC.
     * So we create this offset used to calculate number of seconds between this and the Instant
     * .EPOCH.
     */
    private static final long DATETIME_EPOCH_DIFF_1601;
    private static final Pattern HYPERLINK_PATTERN =
            Pattern.compile("\uFDDFHYPERLINK\\s+\"([^\"]+)\"([^\"]+)$");
    private static final String P = "p";
    private static final int MAX_TRAVERSAL_DEPTH = 100;

    static {
        LocalDateTime time32Epoch1980 = LocalDateTime.of(1980, Month.JANUARY, 1, 0, 0);
        Instant instant = time32Epoch1980.atZone(ZoneOffset.UTC).toInstant();
        TIME32_EPOCH_DIFF_1980 = (instant.toEpochMilli() - Instant.EPOCH.toEpochMilli()) / 1000;
    }

    static {
        LocalDateTime time32Epoch1601 = LocalDateTime.of(1601, Month.JANUARY, 1, 0, 0);
        Instant instant = time32Epoch1601.atZone(ZoneOffset.UTC).toInstant();
        DATETIME_EPOCH_DIFF_1601 = (instant.toEpochMilli() - Instant.EPOCH.toEpochMilli()) / 1000;
    }

    private final Set<String> authors = new HashSet<>();
    private final Set<String> mostRecentAuthors = new HashSet<>();
    private final Set<String> originalAuthors = new HashSet<>();
    public StorageIndexDataElementData storageIndex;
    public StorageManifestDataElementData storageManifest;
    public CellManifestDataElementData headerCellCellManifest;
    public RevisionManifestDataElementData headerCellRevisionManifest;
    public List<RevisionManifestDataElementData> revisionManifests;
    public List<CellManifestDataElementData> cellManifests;
    public HeaderCell headerCell;
    public List<RevisionStoreObjectGroup> dataRoot;
    public RevisionStoreCell dataRootCell;
    public List<RevisionStoreObjectGroup> OtherFileNodeList;
    /**
     * The content cells (object spaces, e.g. pages), each with its object groups and the
     * root object declarations of its current revision.
     */
    public List<RevisionStoreCell> cells;
    private Instant lastModifiedTimestamp = Instant.MIN;
    private long creationTimestamp = Long.MAX_VALUE;
    private long lastModified = Long.MIN_VALUE;
    private ParseContext parseContext;
    private EmbeddedDocumentExtractor embeddedDocumentExtractor;
    private Metadata parentMetadata;

    public MSOneStorePackage() {
        this.revisionManifests = new ArrayList<>();
        this.cellManifests = new ArrayList<>();
        this.OtherFileNodeList = new ArrayList<>();
        this.cells = new ArrayList<>();
    }

    /**
     * This method is used to find the Storage Index Cell Mapping matches the Cell ID.
     *
     * @param cellID Specify the Cell ID.
     * @return Return the specific Storage Index Cell Mapping.
     */
    public StorageIndexCellMapping findStorageIndexCellMapping(CellID cellID) {
        StorageIndexCellMapping storageIndexCellMapping = null;
        if (this.storageIndex != null) {
            storageIndexCellMapping = this.storageIndex.storageIndexCellMappingList.stream()
                    .filter(s -> s.cellID.equals(cellID)).findFirst()
                    .orElse(new StorageIndexCellMapping());
        }
        return storageIndexCellMapping;
    }

    /**
     * This method is used to find the Storage Index Revision Mapping that matches the Revision Mapping Extended GUID.
     *
     * @param revisionExtendedGUID Specify the Revision Mapping Extended GUID.
     * @return Return the instance of Storage Index Revision Mapping.
     */
    public StorageIndexRevisionMapping findStorageIndexRevisionMapping(
            ExGuid revisionExtendedGUID) {
        StorageIndexRevisionMapping instance = null;
        if (this.storageIndex != null) {
            instance = this.storageIndex.storageIndexRevisionMappingList.stream()
                    .filter(r -> r.revisionExGuid.equals(revisionExtendedGUID)).findFirst()
                    .orElse(new StorageIndexRevisionMapping());
        }

        return instance;
    }

    /**
     * Is this property a binary property?
     *
     * @param property The property.
     * @return Is it binary?
     */
    private boolean propertyIsBinary(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.RgOutlineIndentDistance ||
                property == OneNotePropertyEnum.NotebookManagementEntityGuid ||
                property == OneNotePropertyEnum.RichEditTextUnicode;
    }

    /**
     * The attribution of an Author property, determined by the property through which the
     * author object was referenced.
     */
    private enum AuthorRole {
        NONE, MOST_RECENT, ORIGINAL
    }

    public void walkTree(OneNoteTreeWalkerOptions options, Metadata metadata,
                         XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        walkTree(options, metadata, xhtml, new ParseContext());
    }

    public void walkTree(OneNoteTreeWalkerOptions options, Metadata metadata,
                         XHTMLContentHandler xhtml, ParseContext parseContext)
            throws SAXException, TikaException, IOException {
        this.parseContext = parseContext;
        this.parentMetadata = metadata;
        this.embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
        if (!cells.isEmpty()) {
            // Walk each page cell (object space) as a tree, starting from the root objects of
            // its current revision and following the object references in property order. This
            // emits the text in document order. The pages are walked in the order in which the
            // section object space references them; cells that hold older versions of a page
            // (the same object space in a different revision context) are skipped.
            List<RevisionStoreCell> pageCells = new ArrayList<>();
            List<RevisionStoreCell> otherCells = new ArrayList<>();
            splitCells(pageCells, otherCells);
            for (RevisionStoreCell cell : pageCells) {
                xhtml.startElement("div", "class", "page");
                walkCell(cell, options, metadata, xhtml);
                xhtml.endElement("div");
            }
            for (RevisionStoreCell cell : otherCells) {
                walkCell(cell, options, metadata, xhtml);
            }
        } else {
            // no cell information available - walk the object groups in revision order
            Map<ExGuid, RevisionStoreObject> objectsById = indexObjectsById(OtherFileNodeList);
            Set<ExGuid> visited = new HashSet<>();
            for (RevisionStoreObjectGroup objectGroup : OtherFileNodeList) {
                for (RevisionStoreObject object : objectGroup.objects) {
                    walkObject(object, objectsById, visited, AuthorRole.NONE, options, metadata,
                            xhtml, 0);
                }
            }
        }
        if (!authors.isEmpty()) {
            metadata.set(TikaCoreProperties.CREATOR, authors.toArray(new String[]{}));
        }
        if (!mostRecentAuthors.isEmpty()) {
            metadata.set(OneNote.MOST_RECENT_AUTHORS,
                    mostRecentAuthors.toArray(new String[]{}));
        }
        if (!originalAuthors.isEmpty()) {
            metadata.set(OneNote.ORIGINAL_AUTHORS,
                    originalAuthors.toArray(new String[]{}));
        }
    }

    /**
     * Splits the cells into page cells, ordered as the section object space references them,
     * and the remaining cells. A cell that holds an older version of a page - the same object
     * space referenced by a page cell, but in a different revision context - is dropped, so
     * content is not emitted once per version snapshot.
     */
    private void splitCells(List<RevisionStoreCell> pageCells,
                            List<RevisionStoreCell> otherCells) throws TikaException {
        List<CellID> orderedCellIds = collectSectionReferencedCells();
        if (orderedCellIds.isEmpty()) {
            // no page ordering information available - process the cells in storage order
            pageCells.addAll(cells);
            return;
        }
        Map<CellID, RevisionStoreCell> remainingCells = new LinkedHashMap<>();
        for (RevisionStoreCell cell : cells) {
            remainingCells.put(cell.cellID, cell);
        }
        Set<ExGuid> coveredObjectSpaces = new HashSet<>();
        for (CellID cellId : orderedCellIds) {
            RevisionStoreCell cell = remainingCells.remove(cellId);
            if (cell != null) {
                pageCells.add(cell);
                coveredObjectSpaces.add(cellId.extendGUID2);
            }
        }
        for (RevisionStoreCell cell : remainingCells.values()) {
            if (cell.cellID == null ||
                    !coveredObjectSpaces.contains(cell.cellID.extendGUID2)) {
                // not an older version of one of the pages - keep it so no content is lost
                otherCells.add(cell);
            }
        }
    }

    /**
     * Walks the section object space (the data root cell) and collects the object space (cell)
     * references in document order - this is the order of the pages in the section.
     */
    private List<CellID> collectSectionReferencedCells() throws TikaException {
        List<CellID> orderedCellIds = new ArrayList<>();
        if (dataRootCell == null) {
            return orderedCellIds;
        }
        Map<ExGuid, RevisionStoreObject> objectsById = indexObjectsById(dataRootCell.objectGroups);
        Set<ExGuid> visited = new HashSet<>();
        for (RevisionManifestRootDeclare rootDeclare : dataRootCell.rootDeclares) {
            collectReferencedCells(objectsById.get(rootDeclare.objectExGuid), objectsById, visited,
                    orderedCellIds, 0);
        }
        for (RevisionStoreObjectGroup objectGroup : dataRootCell.objectGroups) {
            for (RevisionStoreObject object : objectGroup.objects) {
                collectReferencedCells(object, objectsById, visited, orderedCellIds, 0);
            }
        }
        return orderedCellIds;
    }

    private void collectReferencedCells(RevisionStoreObject object,
                                        Map<ExGuid, RevisionStoreObject> objectsById,
                                        Set<ExGuid> visited, List<CellID> out, int depth)
            throws TikaException {
        if (depth > MAX_TRAVERSAL_DEPTH) {
            return;
        }
        if (object == null || object.propertySet == null ||
                object.propertySet.objectSpaceObjectPropSet == null) {
            return;
        }
        if (object.objectID != null && !visited.add(object.objectID)) {
            return;
        }
        List<PropertyAction> actions = collectObjectActions(object);
        for (PropertyAction action : actions) {
            if (action.spaceReference != null) {
                out.add(action.spaceReference);
            } else if (action.isChildReference && action.childReference != null) {
                collectReferencedCells(objectsById.get(action.childReference), objectsById,
                        visited, out, depth + 1);
            }
        }
    }

    private void walkCell(RevisionStoreCell cell, OneNoteTreeWalkerOptions options,
                          Metadata metadata, XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        Map<ExGuid, RevisionStoreObject> objectsById = indexObjectsById(cell.objectGroups);
        Set<ExGuid> visited = new HashSet<>();
        // Only objects reachable from the root objects of the current revision are part of
        // the current content. The object groups may also contain older, superseded versions
        // of objects (under a different object ID); those are intentionally not walked.
        for (RevisionManifestRootDeclare rootDeclare : cell.rootDeclares) {
            walkObject(objectsById.get(rootDeclare.objectExGuid), objectsById, visited,
                    AuthorRole.NONE, options, metadata, xhtml, 0);
        }
        if (visited.isEmpty()) {
            // no root objects could be resolved - walk everything so no content is lost
            for (RevisionStoreObjectGroup objectGroup : cell.objectGroups) {
                for (RevisionStoreObject object : objectGroup.objects) {
                    walkObject(object, objectsById, visited, AuthorRole.NONE, options, metadata,
                            xhtml, 0);
                }
            }
        }
    }

    /**
     * Builds a map of object ID to object. The object groups are ordered from the oldest
     * revision to the newest, so a newer version of an object wins over an older one.
     */
    private Map<ExGuid, RevisionStoreObject> indexObjectsById(
            List<RevisionStoreObjectGroup> objectGroups) {
        Map<ExGuid, RevisionStoreObject> objectsById = new HashMap<>();
        for (RevisionStoreObjectGroup objectGroup : objectGroups) {
            for (RevisionStoreObject object : objectGroup.objects) {
                if (object.objectID != null) {
                    objectsById.put(object.objectID, object);
                }
            }
        }
        return objectsById;
    }

    private void walkObject(RevisionStoreObject object,
                            Map<ExGuid, RevisionStoreObject> objectsById, Set<ExGuid> visited,
                            AuthorRole authorRole, OneNoteTreeWalkerOptions options,
                            Metadata metadata, XHTMLContentHandler xhtml, int depth)
            throws SAXException, TikaException, IOException {
        if (depth > MAX_TRAVERSAL_DEPTH) {
            throw new TikaException("OneNote object graph exceeds maximum depth of " +
                    MAX_TRAVERSAL_DEPTH);
        }
        if (object == null) {
            return;
        }
        if (object.objectID != null && !visited.add(object.objectID)) {
            return;
        }
        if (object.fileDataObject != null) {
            // the object carries opaque binary data, e.g. an embedded image or file
            handleEmbedded(object.fileDataObject.getData(), xhtml);
        }
        if (object.propertySet == null ||
                object.propertySet.objectSpaceObjectPropSet == null) {
            return;
        }
        List<PropertyAction> actions = collectObjectActions(object);
        // An image node can reference the same picture twice: PictureContainer holds the
        // canonical image data and WebPictureContainer14 holds a rendition derived from it
        // (e.g. re-rendered when the picture was resized). Only emit the derived rendition
        // when the canonical container is missing, so the picture is not extracted twice.
        boolean hasPrimaryPicture = false;
        for (PropertyAction action : actions) {
            if (action.isChildReference && action.childReference != null &&
                    action.oneNotePropertyEnum == OneNotePropertyEnum.PictureContainer) {
                hasPrimaryPicture = true;
                break;
            }
        }
        // The title structure of a page (StructureElementChildNodes) appears above the page
        // body on screen, but is declared after the body child nodes. Emit it first so the
        // text comes out in visual order.
        for (PropertyAction action : actions) {
            if (action.oneNotePropertyEnum == OneNotePropertyEnum.StructureElementChildNodes) {
                processAction(action, objectsById, visited, authorRole, options, metadata, xhtml,
                        depth);
            }
        }
        for (PropertyAction action : actions) {
            if (hasPrimaryPicture &&
                    action.oneNotePropertyEnum == OneNotePropertyEnum.WebPictureContainer14) {
                continue;
            }
            if (action.oneNotePropertyEnum != OneNotePropertyEnum.StructureElementChildNodes) {
                processAction(action, objectsById, visited, authorRole, options, metadata, xhtml,
                        depth);
            }
        }
    }

    /**
     * A property of an object, together with the object reference assigned to it if it is
     * an object reference property.
     */
    private static final class PropertyAction {
        private final IProperty property;
        private final PropertyType propertyType;
        private final OneNotePropertyEnum oneNotePropertyEnum;
        private final boolean isChildReference;
        private final ExGuid childReference;
        private final CellID spaceReference;

        PropertyAction(IProperty property, PropertyType propertyType,
                       OneNotePropertyEnum oneNotePropertyEnum, boolean isChildReference,
                       ExGuid childReference, CellID spaceReference) {
            this.property = property;
            this.propertyType = propertyType;
            this.oneNotePropertyEnum = oneNotePropertyEnum;
            this.isChildReference = isChildReference;
            this.childReference = childReference;
            this.spaceReference = spaceReference;
        }
    }

    /**
     * Flattens the properties of an object, in order, into a list of actions.
     */
    private List<PropertyAction> collectObjectActions(RevisionStoreObject object)
            throws TikaException {
        List<ExGuid> referencedObjects =
                object.referencedObjectID == null || object.referencedObjectID.content == null ?
                        Collections.emptyList() : object.referencedObjectID.content;
        List<CellID> referencedSpaces = object.referencedObjectSpacesID == null ||
                object.referencedObjectSpacesID.content == null ? Collections.emptyList() :
                object.referencedObjectSpacesID.content;
        List<PropertyAction> actions = new ArrayList<>();
        collectActions(object.propertySet.objectSpaceObjectPropSet.body, referencedObjects,
                new int[]{0}, referencedSpaces, new int[]{0}, actions, 0);
        return actions;
    }

    /**
     * Flattens the properties of a property set, in order, into a list of actions.
     * Properties of type ObjectID or ArrayOfObjectIDs consume, in property order, the object
     * references of the containing object, and properties of type ObjectSpaceID or
     * ArrayOfObjectSpaceIDs consume the object space (cell) references (see MS-ONESTORE
     * section 2.7.8), so the references must be assigned here, in property order, no matter
     * in which order the actions are processed later.
     */
    private void collectActions(PropertySet propertySet, List<ExGuid> referencedObjects,
                                int[] referenceCursor, List<CellID> referencedSpaces,
                                int[] spaceCursor, List<PropertyAction> actions, int depth)
            throws TikaException {
        if (depth > MAX_TRAVERSAL_DEPTH) {
            throw new TikaException("OneNote property graph exceeds maximum depth of " +
                    MAX_TRAVERSAL_DEPTH);
        }
        if (propertySet == null || propertySet.rgPrids == null || propertySet.rgData == null) {
            return;
        }
        for (int i = 0; i < propertySet.rgPrids.length && i < propertySet.rgData.size(); ++i) {
            IProperty property = propertySet.rgData.get(i);
            PropertyID propertyID = propertySet.rgPrids[i];
            PropertyType propertyType = PropertyType.fromIntVal(propertyID.type);
            OneNotePropertyEnum oneNotePropertyEnum =
                    OneNotePropertyEnum.of(Unsigned.uint(propertyID.value).longValue());
            if (propertyType == PropertyType.ObjectID) {
                actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum, true,
                        nextReference(referencedObjects, referenceCursor), null));
            } else if (propertyType == PropertyType.ArrayOfObjectIDs) {
                int count = property instanceof ArrayNumber ?
                        Math.min(Math.max(0, ((ArrayNumber) property).number),
                                referencedObjects.size() - referenceCursor[0]) : 0;
                for (int j = 0; j < count; ++j) {
                    actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum,
                            true, nextReference(referencedObjects, referenceCursor), null));
                }
            } else if (propertyType == PropertyType.ObjectSpaceID) {
                actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum, false,
                        null, nextSpaceReference(referencedSpaces, spaceCursor)));
            } else if (propertyType == PropertyType.ArrayOfObjectSpaceIDs) {
                int count = property instanceof ArrayNumber ?
                        Math.min(Math.max(0, ((ArrayNumber) property).number),
                                referencedSpaces.size() - spaceCursor[0]) : 0;
                for (int j = 0; j < count; ++j) {
                    actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum,
                            false, null, nextSpaceReference(referencedSpaces, spaceCursor)));
                }
            } else if (propertyType == PropertyType.PropertySet) {
                if (property instanceof PropertySet) {
                    collectActions((PropertySet) property, referencedObjects, referenceCursor,
                            referencedSpaces, spaceCursor, actions, depth + 1);
                }
            } else if (propertyType == PropertyType.ArrayOfPropertyValues) {
                if (property instanceof PrtArrayOfPropertyValues &&
                        ((PrtArrayOfPropertyValues) property).data != null) {
                    for (PropertySet nested : ((PrtArrayOfPropertyValues) property).data) {
                        collectActions(nested, referencedObjects, referenceCursor,
                                referencedSpaces, spaceCursor, actions, depth + 1);
                    }
                }
            } else {
                actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum, false,
                        null, null));
            }
        }
    }

    private void processAction(PropertyAction action,
                               Map<ExGuid, RevisionStoreObject> objectsById, Set<ExGuid> visited,
                               AuthorRole authorRole, OneNoteTreeWalkerOptions options,
                               Metadata metadata, XHTMLContentHandler xhtml, int depth)
            throws SAXException, TikaException, IOException {
        if (action.spaceReference != null) {
            // a reference to another object space (cell) - cells are walked separately
            return;
        }
        if (action.isChildReference) {
            AuthorRole childRole = AuthorRole.NONE;
            if (action.oneNotePropertyEnum == OneNotePropertyEnum.AuthorMostRecent) {
                childRole = AuthorRole.MOST_RECENT;
            } else if (action.oneNotePropertyEnum == OneNotePropertyEnum.AuthorOriginal) {
                childRole = AuthorRole.ORIGINAL;
            }
            walkObject(action.childReference == null ? null :
                            objectsById.get(action.childReference), objectsById, visited,
                    childRole, options, metadata, xhtml, depth + 1);
        } else {
            processPrimitiveProperty(action.property, action.propertyType,
                    action.oneNotePropertyEnum, authorRole, options, metadata, xhtml);
        }
    }

    private ExGuid nextReference(List<ExGuid> referencedObjects, int[] referenceCursor) {
        if (referenceCursor[0] < referencedObjects.size()) {
            return referencedObjects.get(referenceCursor[0]++);
        }
        return null;
    }

    private CellID nextSpaceReference(List<CellID> referencedSpaces, int[] spaceCursor) {
        if (spaceCursor[0] < referencedSpaces.size()) {
            return referencedSpaces.get(spaceCursor[0]++);
        }
        return null;
    }

    private void processPrimitiveProperty(IProperty property, PropertyType propertyType,
                                          OneNotePropertyEnum oneNotePropertyEnum,
                                          AuthorRole authorRole,
                                          OneNoteTreeWalkerOptions options, Metadata metadata,
                                          XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        if (oneNotePropertyEnum == OneNotePropertyEnum.LastModifiedTimeStamp) {
            long fullval = getScalar(property);
            Instant instant = Instant.ofEpochSecond(
                    fullval / 10000000 + DATETIME_EPOCH_DIFF_1601);
            if (instant.isAfter(lastModifiedTimestamp)) {
                lastModifiedTimestamp = instant;
            }
            metadata.set(OneNote.LAST_MODIFIED_TIMESTAMP,
                    String.valueOf(lastModifiedTimestamp.toEpochMilli()));
        } else if (oneNotePropertyEnum == OneNotePropertyEnum.CreationTimeStamp) {
            // add the TIME32_EPOCH_DIFF_1980 because OneNote TIME32 epoch time is per 1980, not
            // 1970
            long scalar = getScalar(property);
            long creationTs = scalar + TIME32_EPOCH_DIFF_1980;
            if (creationTs < creationTimestamp) {
                creationTimestamp = creationTs;
            }
            metadata.set(OneNote.CREATION_TIMESTAMP, String.valueOf(creationTimestamp));
        } else if (oneNotePropertyEnum == OneNotePropertyEnum.LastModifiedTime) {
            // add the TIME32_EPOCH_DIFF_1980 because OneNote TIME32 epoch time is per 1980, not
            // 1970
            long scalar = getScalar(property);
            long lastMod = scalar + TIME32_EPOCH_DIFF_1980;
            if (lastMod > lastModified) {
                lastModified = lastMod;
            }
            metadata.set(TikaCoreProperties.MODIFIED, String.valueOf(lastModified));
        } else if (oneNotePropertyEnum == OneNotePropertyEnum.Author) {
            String author = new String(((PrtFourBytesOfLengthFollowedByData) property).data,
                    StandardCharsets.UTF_8);
            if (authorRole == AuthorRole.MOST_RECENT) {
                mostRecentAuthors.add(author);
            } else if (authorRole == AuthorRole.ORIGINAL) {
                originalAuthors.add(author);
                // the original authors are the creators of the content
                authors.add(author);
            } else {
                authors.add(author);
            }
        } else if (propertyType == PropertyType.FourBytesOfLengthFollowedByData) {
            boolean isBinary = propertyIsBinary(oneNotePropertyEnum);
            PrtFourBytesOfLengthFollowedByData dataProperty =
                    (PrtFourBytesOfLengthFollowedByData) property;
            if ((dataProperty.data.length & 1) == 0 &&
                    oneNotePropertyEnum != OneNotePropertyEnum.TextExtendedAscii && !isBinary) {
                if (options.getUtf16PropertiesToPrint().contains(oneNotePropertyEnum)) {
                    xhtml.startElement(P);
                    xhtml.characters(new String(dataProperty.data, StandardCharsets.UTF_16LE));
                    xhtml.endElement(P);
                }
            } else if (oneNotePropertyEnum == OneNotePropertyEnum.TextExtendedAscii) {
                xhtml.startElement(P);
                xhtml.characters(new String(dataProperty.data, StandardCharsets.US_ASCII));
                xhtml.endElement(P);
            } else if (!isBinary) {
                if (options.getUtf16PropertiesToPrint().contains(oneNotePropertyEnum)) {
                    xhtml.startElement(P);
                    xhtml.characters(new String(dataProperty.data, StandardCharsets.UTF_16LE));
                    xhtml.endElement(P);
                }
            } else {
                if (oneNotePropertyEnum == OneNotePropertyEnum.RichEditTextUnicode) {
                    handleRichEditTextUnicode(dataProperty.data, xhtml);
                } else {
                    //TODO -- these seem to be somewhat broken font files and other
                    //odds and ends...what are they and how should we process them?
                    //handleEmbedded(content.size());
                }
            }
        }
    }


    /**
     * Hands the binary data of an embedded object (e.g. an image or an attached file) to the
     * embedded document extractor.
     */
    private void handleEmbedded(byte[] data, XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        if (data == null || data.length == 0 || embeddedDocumentExtractor == null) {
            return;
        }
        Metadata embeddedMetadata = Metadata.newInstance(this.parseContext);
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "embedded");
        xhtml.startElement("div", attributes);
        xhtml.endElement("div");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
                embeddedDocumentExtractor.parseEmbedded(tis, new EmbeddedContentHandler(xhtml),
                        embeddedMetadata, this.parseContext, false);
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
        }
    }

    private void handleRichEditTextUnicode(byte[] arr, XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        // look for the first null
        int firstNull = 0;
        for (int i = 0; i < arr.length - 1; i += 2) {
            if (arr[i] == 0 && arr[i + 1] == 0) {
                firstNull = Math.max(i, 0);
                break;
            }
        }

        if (firstNull == 0) {
            return;
        }
        String txt = new String(arr, 0, firstNull, StandardCharsets.UTF_16LE);
        Matcher m = HYPERLINK_PATTERN.matcher(txt);
        if (m.find()) {
            xhtml.startElement("a", "href", m.group(1));
            xhtml.characters(m.group(2));
            xhtml.endElement("a");
        } else {
            xhtml.startElement(P);
            xhtml.characters(txt);
            xhtml.endElement(P);
        }
    }

    private long getScalar(IProperty property) throws TikaException, IOException {
        if (property instanceof FourBytesOfData) {
            FourBytesOfData fourBytesOfDataProp = (FourBytesOfData) property;
            return BitConverter.toUInt32(fourBytesOfDataProp.data, 0);
        } else if (property instanceof EightBytesOfData) {
            EightBytesOfData fourBytesOfDataProp = (EightBytesOfData) property;
            return BitConverter.toInt64(fourBytesOfDataProp.data, 0);
        }
        throw new TikaException("Could not parse scalar of type " + property.getClass());
    }
}
