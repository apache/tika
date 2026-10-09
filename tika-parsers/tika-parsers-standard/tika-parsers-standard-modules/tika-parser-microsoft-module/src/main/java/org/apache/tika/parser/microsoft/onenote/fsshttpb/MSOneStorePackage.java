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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.apache.tika.parser.microsoft.onenote.GUID;
import org.apache.tika.parser.microsoft.onenote.OneNoteGuidCollector;
import org.apache.tika.parser.microsoft.onenote.OneNoteJcid;
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
    private static final Logger LOG = LoggerFactory.getLogger(MSOneStorePackage.class);
    private static final String P = "p";
    private static final int MAX_OBJECT_WALK_DEPTH = 1000;
    private static final int MAX_REFERENCE_COUNT = 100000;
    private static final int MAX_PARSE_WARNINGS = 100;
    private static final int MIN_CANONICAL_LIST_FORMAT_LENGTH = 128;

    /** Defensive bound on root objects resolved per cell; real cells declare a handful. */
    static final int MAX_CELL_ROOTS = 10_000;
    private static final int FILE_IDENTITY_GUID_PROPERTY_ID = 0x1C001D94;

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
    private final OneNoteGuidCollector guids = new OneNoteGuidCollector(this::recordGuidCapWarning);
    /**
     * The fully populated storage index. Set this before performing storage-index lookups; its
     * mapping lists are indexed once and must not be mutated afterward.
     */
    public StorageIndexDataElementData storageIndex;
    public StorageManifestDataElementData storageManifest;
    public CellManifestDataElementData headerCellCellManifest;
    public RevisionManifestDataElementData headerCellRevisionManifest;
    public List<RevisionManifestDataElementData> revisionManifests;
    public List<CellManifestDataElementData> cellManifests;
    public HeaderCell headerCell;
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
    private final List<String> parseWarnings = new ArrayList<>();
    // This state intentionally spans parser construction and tree walking for one package.
    private final Set<String> recordedParseWarningKeys = new HashSet<>();
    private boolean parseWarningsSuppressed;
    private boolean storageMappingsIndexed;
    private boolean contentEmitted;
    private final Map<RevisionStoreObject, Map<AuthorRole, EmbeddedResourceInfo>> depthSkippedObjects =
            new LinkedHashMap<>();
    // flattened property actions per object; objects can be re-flattened many times during
    // picture/resource-name resolution, which is quadratic without this cache
    private final Map<RevisionStoreObject, List<PropertyAction>> objectActionsCache =
            new IdentityHashMap<>();
    private final Map<RevisionStoreObject, NumberListInfo> numberListInfoCache =
            new IdentityHashMap<>();
    private final Map<String, List<byte[]>> listFormatKeysByDigest = new HashMap<>();
    private final Map<CellID, StorageIndexCellMapping> storageIndexCellMappingsById =
            new HashMap<>();
    private final Map<ExGuid, StorageIndexRevisionMapping> storageIndexRevisionMappingsById =
            new HashMap<>();

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
     * @return the specific Storage Index Cell Mapping, or {@code null} if it is absent.
     */
    public StorageIndexCellMapping findStorageIndexCellMapping(CellID cellID) {
        indexStorageMappings();
        return storageIndexCellMappingsById.get(cellID);
    }

    /**
     * This method is used to find the Storage Index Revision Mapping that matches the Revision Mapping Extended GUID.
     *
     * @param revisionExtendedGUID Specify the Revision Mapping Extended GUID.
     * @return the instance of Storage Index Revision Mapping, or {@code null} if it is absent.
     */
    public StorageIndexRevisionMapping findStorageIndexRevisionMapping(
            ExGuid revisionExtendedGUID) {
        indexStorageMappings();
        return storageIndexRevisionMappingsById.get(revisionExtendedGUID);
    }

    private void indexStorageMappings() {
        if (storageMappingsIndexed || storageIndex == null) {
            return;
        }
        storageMappingsIndexed = true;
        for (StorageIndexCellMapping mapping : storageIndex.storageIndexCellMappingList) {
            storageIndexCellMappingsById.putIfAbsent(mapping.cellID, mapping);
        }
        for (StorageIndexRevisionMapping mapping : storageIndex.storageIndexRevisionMappingList) {
            storageIndexRevisionMappingsById.putIfAbsent(mapping.revisionExGuid, mapping);
        }
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

    private enum ParseWarningKind {
        DEFAULT,
        OBJECT_REFERENCE_ARRAY_UNAVAILABLE,
        OBJECT_REFERENCE_ARRAY_CAPPED,
        OBJECT_SPACE_REFERENCE_ARRAY_UNAVAILABLE,
        OBJECT_SPACE_REFERENCE_ARRAY_CAPPED
    }

    public void walkTree(OneNoteTreeWalkerOptions options, Metadata metadata,
                         XHTMLContentHandler xhtml, ParseContext parseContext)
            throws SAXException, TikaException, IOException {
        depthSkippedObjects.clear();
        this.parseContext = parseContext;
        this.parentMetadata = metadata;
        this.embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
        for (String warning : parseWarnings) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        }
        parseWarnings.clear();
        try {
            collectSectionFileIdentityGuids();
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
                    emitPage(cell, options, metadata, xhtml);
                }
                for (RevisionStoreCell cell : otherCells) {
                    walkCell(cell, indexObjectsById(cell.objectGroups), options, metadata, xhtml);
                }
            } else {
                // no cell information available - walk the object groups in revision order
                Map<ExGuid, RevisionStoreObject> objectsById = indexObjectsById(OtherFileNodeList);
                Set<ExGuid> visited = new HashSet<>();
                walkObjectGroupRoots(OtherFileNodeList, objectsById, visited, options, metadata,
                        xhtml);
            }
            if (!authors.isEmpty()) {
                metadata.set(TikaCoreProperties.CREATOR, sortedValues(authors));
            }
            if (!mostRecentAuthors.isEmpty()) {
                metadata.set(OneNote.MOST_RECENT_AUTHORS, sortedValues(mostRecentAuthors));
            }
            if (!originalAuthors.isEmpty()) {
                metadata.set(OneNote.ORIGINAL_AUTHORS, sortedValues(originalAuthors));
            }
        } finally {
            numberListInfoCache.clear();
            listFormatKeysByDigest.clear();
            guids.publish(metadata);
        }
    }

    OneNoteGuidCollector guidCollector() {
        return guids;
    }

    boolean hasCachedListStyles() {
        return !numberListInfoCache.isEmpty() || !listFormatKeysByDigest.isEmpty();
    }

    void emitPage(RevisionStoreCell cell, OneNoteTreeWalkerOptions options,
                          Metadata metadata, XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        Map<ExGuid, RevisionStoreObject> objectsById = indexObjectsById(cell.objectGroups);
        String pageGuid = findPageGuid(cell, objectsById);
        guids.add(OneNoteGuidCollector.Category.PAGE, pageGuid);
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "page");
        if (pageGuid != null) {
            attributes.addAttribute("", "id", "id", "CDATA", pageGuid);
        }
        xhtml.startElement("div", attributes);
        try {
            walkCell(cell, objectsById, options, metadata, xhtml);
        } finally {
            xhtml.endElement("div");
        }
    }

    /**
     * Splits the cells into page cells, ordered as the section object space references them,
     * and the remaining cells. A cell that holds an older version of a page - the same object
     * space referenced by a page cell, but in a different revision context - is dropped, so
     * content is not emitted once per version snapshot.
     */
    private void splitCells(List<RevisionStoreCell> pageCells,
                            List<RevisionStoreCell> otherCells) {
        if (dataRootCell == null) {
            // Without a data root there is no reliable page ordering information.
            otherCells.addAll(cells);
            return;
        }
        List<CellID> orderedCellIds = collectSectionReferencedCells();
        if (orderedCellIds.isEmpty()) {
            // no page ordering information available - process the cells in storage order
            otherCells.addAll(cells);
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
                if (cellId.extendGUID2 != null) {
                    coveredObjectSpaces.add(cellId.extendGUID2);
                }
            }
        }
        for (RevisionStoreCell cell : remainingCells.values()) {
            if (!coveredObjectSpaces.contains(cell.cellID.extendGUID2)) {
                // not an older version of one of the pages - keep it so no content is lost
                otherCells.add(cell);
            }
        }
    }

    /**
     * Walks the section object space (the data root cell) and collects the object space (cell)
     * references in document order - this is the order of the pages in the section.
     */
    private List<CellID> collectSectionReferencedCells() {
        List<CellID> orderedCellIds = new ArrayList<>();
        if (dataRootCell == null) {
            return orderedCellIds;
        }
        Map<ExGuid, RevisionStoreObject> objectsById = indexObjectsById(dataRootCell.objectGroups);
        Set<ExGuid> visited = new HashSet<>();
        for (RevisionManifestRootDeclare rootDeclare : dataRootCell.rootDeclares) {
            RevisionStoreObject rootObject = objectsById.get(rootDeclare.objectExGuid);
            if (rootObject == null) {
                recordParseWarning("OneNote section root object " + rootDeclare.objectExGuid +
                        " could not be resolved");
            }
            collectReferencedCells(rootObject, objectsById, visited, orderedCellIds, 0);
        }
        return orderedCellIds;
    }

    private void collectReferencedCells(RevisionStoreObject object,
                                        Map<ExGuid, RevisionStoreObject> objectsById,
                                        Set<ExGuid> visited, List<CellID> out, int depth) {
        if (object == null || object.propertySet == null ||
                object.propertySet.objectSpaceObjectPropSet == null) {
            return;
        }
        if (depth >= MAX_OBJECT_WALK_DEPTH) {
            recordParseWarning("OneNote section reference traversal exceeded depth limit " +
                    MAX_OBJECT_WALK_DEPTH);
            return;
        }
        if (object.objectID != null && !visited.add(object.objectID)) {
            return;
        }
        recordEntityGuid(object, jcidIndex(object));
        List<PropertyAction> actions = collectObjectActions(object);
        for (PropertyAction action : actions) {
            if (action.spaceReference != null) {
                out.add(action.spaceReference);
            } else if (action.isChildReference && action.childReference != null) {
                RevisionStoreObject child = objectsById.get(action.childReference);
                if (child == null && !warningsSaturated()) {
                    recordParseWarning("OneNote section object " + action.childReference +
                            " could not be resolved");
                }
                collectReferencedCells(child, objectsById, visited, out, depth + 1);
            }
        }
    }

    private void walkCell(RevisionStoreCell cell,
                          Map<ExGuid, RevisionStoreObject> objectsById,
                          OneNoteTreeWalkerOptions options, Metadata metadata,
                          XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        depthSkippedObjects.clear();
        Set<ExGuid> visited = new HashSet<>();
        // Only objects reachable from the root objects of the current revision are part of
        // the current content. The object groups may also contain older, superseded versions
        // of objects (under a different object ID); those are intentionally not walked.
        CellRoots roots = cellRoots(cell, objectsById, this::recordParseWarning);
        for (RevisionStoreObject rootObject : roots.declared) {
            walkRootObject(rootObject, objectsById, visited, options, metadata, xhtml);
        }
        if (roots.walkAll) {
            if (cell.rootDeclares != null && cell.rootDeclares.isEmpty()) {
                recordParseWarning("OneNote cell has no declared root objects; walking all objects");
            } else if (roots.truncated) {
                // reachable only when no content root survived the cap, so the plain
                // check is enough here
                recordParseWarning("OneNote cell has more than " + MAX_CELL_ROOTS
                        + " root objects; walking all objects");
            } else {
                recordParseWarning("OneNote cell root objects could not be resolved; walking all objects");
            }
            walkObjectGroupRoots(cell.objectGroups, objectsById, visited, options, metadata,
                    xhtml);
        }
    }

    /**
     * Resolves the objects a cell walk starts from: the root objects declared by the current
     * revision, then, when the declares are empty or dangle without a property-set root, every
     * object in the groups. A blob-only root (no property set) cannot reach the page body, so
     * it does not count as a resolved content root: if the content root declare dangles next
     * to it, the walk-everything fallback must still fire or the page body is silently lost.
     * The same holds when the root cap truncates the declares before reaching a content root.
     */
    private CellRoots cellRoots(RevisionStoreCell cell,
                                Map<ExGuid, RevisionStoreObject> objectsById,
                                Consumer<String> unresolvedRootWarning) {
        CellRoots roots = new CellRoots();
        if (cell.rootDeclares != null) {
            Set<RevisionStoreObject> seenRoots = new HashSet<>();
            for (RevisionManifestRootDeclare rootDeclare : cell.rootDeclares) {
                RevisionStoreObject rootObject = objectsById.get(rootDeclare.objectExGuid);
                if (rootObject == null) {
                    unresolvedRootWarning.accept("OneNote cell root object " +
                            rootDeclare.objectExGuid + " could not be resolved");
                    roots.unresolvedRoot = true;
                } else if (seenRoots.add(rootObject)) {
                    if (roots.declared.size() < MAX_CELL_ROOTS) {
                        if (rootObject.propertySet != null) {
                            roots.resolvedContentRoot = true;
                        }
                        roots.declared.add(rootObject);
                    } else if (!roots.truncated) {
                        roots.truncated = true;
                        unresolvedRootWarning.accept("OneNote cell has more than " + MAX_CELL_ROOTS
                                + " root objects; ignoring the rest");
                    }
                }
            }
        }
        roots.walkAll = cell.rootDeclares == null || cell.rootDeclares.isEmpty()
                || ((roots.unresolvedRoot || roots.truncated) && !roots.resolvedContentRoot);
        return roots;
    }

    /**
     * Root resolution shared by {@link #walkCell} and
     * {@link #findPageGuid(RevisionStoreCell, Map)}.
     */
    private static final class CellRoots {
        final List<RevisionStoreObject> declared = new ArrayList<>();
        boolean resolvedContentRoot;
        boolean unresolvedRoot;
        boolean truncated;
        boolean walkAll;
    }

    private String[] sortedValues(Set<String> values) {
        String[] sorted = values.toArray(new String[0]);
        Arrays.sort(sorted);
        return sorted;
    }

    private boolean warningsSaturated() {
        return parseWarningsSuppressed;
    }

    void recordParseWarning(String warning) {
        recordParseWarning(ParseWarningKind.DEFAULT, warning);
    }

    private void recordParseWarning(ParseWarningKind kind, String warning) {
        String warningKey = kind == ParseWarningKind.DEFAULT ? warning : kind.name();
        if (recordedParseWarningKeys.contains(warningKey)) {
            return;
        }
        if (recordedParseWarningKeys.size() >= MAX_PARSE_WARNINGS) {
            if (!parseWarningsSuppressed) {
                parseWarningsSuppressed = true;
                emitParseWarning("Additional OneNote parse warnings were suppressed after " +
                        MAX_PARSE_WARNINGS + " distinct warnings");
            }
            return;
        }
        recordedParseWarningKeys.add(warningKey);
        emitParseWarning(warning);
    }

    private void emitParseWarning(String warning) {
        LOG.warn(warning);
        if (parentMetadata == null) {
            parseWarnings.add(warning);
        } else {
            parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        }
    }

    /**
     * Exempt from the parse-warning budget: capped GUID metadata must always carry its own
     * incompleteness signal, even when generic warnings were suppressed.
     */
    private void recordGuidCapWarning(String warning) {
        emitParseWarning(warning);
    }

    /**
     * Builds a map of object ID to object. The object groups are ordered from the oldest
     * revision to the newest, so a newer version of an object wins over an older one.
     */
    Map<ExGuid, RevisionStoreObject> indexObjectsById(
            List<RevisionStoreObjectGroup> objectGroups) {
        Map<ExGuid, RevisionStoreObject> objectsById = new HashMap<>();
        for (RevisionStoreObjectGroup objectGroup : objectGroups) {
            if (objectGroup == null || objectGroup.objects == null) {
                continue;
            }
            for (RevisionStoreObject object : objectGroup.objects) {
                if (object != null && object.objectID != null) {
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
        walkObject(object, objectsById, visited, authorRole, options, metadata, xhtml,
                depth, null);
    }

    private void walkObject(RevisionStoreObject object,
                             Map<ExGuid, RevisionStoreObject> objectsById, Set<ExGuid> visited,
                             AuthorRole authorRole, OneNoteTreeWalkerOptions options,
                             Metadata metadata, XHTMLContentHandler xhtml, int depth,
                             EmbeddedResourceInfo inheritedResourceInfo)
            throws SAXException, TikaException, IOException {
        if (object == null) {
            return;
        }
        if (depth >= MAX_OBJECT_WALK_DEPTH) {
            recordParseWarning("OneNote object traversal exceeded depth limit " +
                    MAX_OBJECT_WALK_DEPTH);
            if (object.objectID != null) {
                Map<AuthorRole, EmbeddedResourceInfo> references = depthSkippedObjects.computeIfAbsent(
                        object, key -> new EnumMap<>(AuthorRole.class));
                AuthorRole role = authorRole == null ? AuthorRole.NONE : authorRole;
                if (!references.containsKey(role)) {
                    references.put(role, inheritedResourceInfo);
                }
            }
            return;
        }
        if (object.objectID != null && !visited.add(object.objectID)) {
            // one object may be referenced under several author roles (e.g. the same author
            // as both AuthorOriginal and AuthorMostRecent) - record the role even though
            // the subtree is not walked again
            recordAuthors(object, authorRole);
            return;
        }
        List<PropertyAction> actions = object.propertySet != null &&
                object.propertySet.objectSpaceObjectPropSet != null ?
                collectObjectActions(object) : Collections.emptyList();
        EmbeddedResourceInfo resourceInfo = embeddedResourceInfo(actions);
        if (resourceInfo == null) {
            resourceInfo = inheritedResourceInfo;
        }
        if (resourceInfo == null && object.fileDataObject != null && object.objectID != null) {
            resourceInfo = resourceInfoFromReferencingObject(object, objectsById, depth);
        }
        if (object.fileDataObject != null) {
            // the object carries opaque binary data, e.g. an embedded image or file
            handleEmbedded(object.fileDataObject.getData(), xhtml, resourceInfo, object.objectID);
        }
        if (object.propertySet == null ||
                object.propertySet.objectSpaceObjectPropSet == null) {
            return;
        }
        // An image node can reference the same picture twice: PictureContainer holds the
        // canonical image data and WebPictureContainer14 holds a rendition derived from it
        // (e.g. re-rendered when the picture was resized). Only emit the derived rendition
        // when the canonical container is missing, so the picture is not extracted twice.
        boolean hasPrimaryPicture = false;
        for (PropertyAction action : actions) {
            if (action.isChildReference && action.childReference != null &&
                    action.oneNotePropertyEnum == OneNotePropertyEnum.PictureContainer &&
                    hasUsablePicture(action.childReference, objectsById, new HashSet<>(), depth)) {
                hasPrimaryPicture = true;
                break;
            }
        }
        int objectType = jcidIndex(object);
        recordEntityGuid(object, objectType);
        ListStyle listStyle = listStyleForObject(object, actions, objectsById);
        startObjectStructure(objectType, listStyle, xhtml);
        try {
            // Emit the title structure before page body nodes to preserve visual order.
            List<PropertyAction> structureActions = new ArrayList<>();
            for (PropertyAction action : actions) {
                if (action.oneNotePropertyEnum == OneNotePropertyEnum.StructureElementChildNodes) {
                    structureActions.add(action);
                }
            }
            processStructureChildActions(structureActions, objectsById, visited, authorRole,
                    options, metadata, xhtml, depth, resourceInfo);
            List<PropertyAction> remainingActions = new ArrayList<>();
            for (PropertyAction action : actions) {
                if (hasPrimaryPicture &&
                        action.oneNotePropertyEnum == OneNotePropertyEnum.WebPictureContainer14) {
                    continue;
                }
                if (action.oneNotePropertyEnum != OneNotePropertyEnum.StructureElementChildNodes) {
                    remainingActions.add(action);
                }
            }
            processStructureChildActions(remainingActions, objectsById, visited, authorRole,
                    options, metadata, xhtml, depth, resourceInfo);
        } finally {
            endObjectStructure(objectType, listStyle, xhtml);
        }
    }

    private void startObjectStructure(int objectType, ListStyle listStyle,
                                      XHTMLContentHandler xhtml) throws SAXException {
        switch (objectType) {
            case OneNoteJcid.TABLE_NODE:
                xhtml.startElement("table");
                break;
            case OneNoteJcid.TABLE_ROW_NODE:
                xhtml.startElement("tr");
                break;
            case OneNoteJcid.TABLE_CELL_NODE:
                xhtml.startElement("td");
                break;
            case OneNoteJcid.OUTLINE_ELEMENT_NODE:
                if (listStyle != null) {
                    if (listStyle.restartValue == null) {
                        xhtml.startElement("li");
                    } else {
                        xhtml.startElement("li", "value", listStyle.restartValue.toString());
                    }
                }
                break;
            default:
                break;
        }
    }

    private void endObjectStructure(int objectType, ListStyle listStyle,
                                    XHTMLContentHandler xhtml) throws SAXException {
        switch (objectType) {
            case OneNoteJcid.TABLE_NODE:
                xhtml.endElement("table");
                break;
            case OneNoteJcid.TABLE_ROW_NODE:
                xhtml.endElement("tr");
                break;
            case OneNoteJcid.TABLE_CELL_NODE:
                xhtml.endElement("td");
                break;
            case OneNoteJcid.OUTLINE_ELEMENT_NODE:
                if (listStyle != null) {
                    xhtml.endElement("li");
                }
                break;
            default:
                break;
        }
    }

    private void walkObjectGroupRoots(List<RevisionStoreObjectGroup> objectGroups,
                                      Map<ExGuid, RevisionStoreObject> objectsById,
                                      Set<ExGuid> visited, OneNoteTreeWalkerOptions options,
                                      Metadata metadata, XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        Set<ExGuid> structureDescendantIds = collectStructureDescendantIds(objectsById);
        List<RevisionStoreObject> deferredObjects = new ArrayList<>();
        for (RevisionStoreObjectGroup objectGroup : objectGroups) {
            if (objectGroup == null || objectGroup.objects == null) {
                continue;
            }
            for (RevisionStoreObject object : objectGroup.objects) {
                if (object != null && object.objectID != null &&
                        structureDescendantIds.contains(object.objectID)) {
                    deferredObjects.add(object);
                    continue;
                }
                walkRootObject(object, objectsById, visited, options, metadata, xhtml);
            }
        }
        recoverDepthSkippedObjects(objectsById, visited, options, metadata, xhtml);
        // Residual owners belong to cycles; keep their storage order, not type priority.
        for (RevisionStoreObject object : deferredObjects) {
            if (!visited.contains(object.objectID) &&
                    (jcidIndex(object) == OneNoteJcid.TABLE_NODE ||
                            isListContainer(object, objectsById))) {
                walkRootObject(object, objectsById, visited, options, metadata, xhtml);
            }
        }
        recoverDepthSkippedObjects(objectsById, visited, options, metadata, xhtml);
    }

    private void recoverDepthSkippedObjects(Map<ExGuid, RevisionStoreObject> objectsById,
                                            Set<ExGuid> visited, OneNoteTreeWalkerOptions options,
                                            Metadata metadata, XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        // Recover only depth-skipped objects, not intentionally suppressed renditions.
        while (!depthSkippedObjects.isEmpty()) {
            Map.Entry<RevisionStoreObject, Map<AuthorRole, EmbeddedResourceInfo>> skipped =
                    depthSkippedObjects.entrySet().iterator().next();
            depthSkippedObjects.remove(skipped.getKey());
            for (Map.Entry<AuthorRole, EmbeddedResourceInfo> reference : skipped.getValue().entrySet()) {
                walkRootObject(skipped.getKey(), objectsById, visited, reference.getKey(), options,
                        metadata, xhtml, reference.getValue());
            }
        }
    }

    private boolean isListContainer(RevisionStoreObject object,
                                    Map<ExGuid, RevisionStoreObject> objectsById) {
        int type = jcidIndex(object);
        if (type == OneNoteJcid.OUTLINE_NODE ||
                type == OneNoteJcid.OUTLINE_ELEMENT_NODE) {
            return true;
        }
        if (object == null || object.propertySet == null ||
                object.propertySet.objectSpaceObjectPropSet == null) {
            return false;
        }
        for (PropertyAction action : collectObjectActions(object)) {
            if (action.isChildReference &&
                    jcidIndex(objectsById.get(action.childReference)) ==
                            OneNoteJcid.OUTLINE_ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private Set<ExGuid> collectStructureDescendantIds(
            Map<ExGuid, RevisionStoreObject> objectsById) {
        Set<ExGuid> descendants = new HashSet<>();
        Set<ExGuid> traversed = new HashSet<>();
        List<RevisionStoreObject> pending = new ArrayList<>();
        for (RevisionStoreObject object : objectsById.values()) {
            if (jcidIndex(object) == OneNoteJcid.TABLE_NODE ||
                    isListContainer(object, objectsById)) {
                traversed.add(object.objectID);
                pending.add(object);
            }
        }
        for (int index = 0; index < pending.size(); index++) {
            RevisionStoreObject object = pending.get(index);
            if (object.propertySet == null ||
                    object.propertySet.objectSpaceObjectPropSet == null) {
                continue;
            }
            for (PropertyAction action : collectObjectActions(object)) {
                if (!action.isChildReference || action.childReference == null) {
                    continue;
                }
                ExGuid childId = action.childReference;
                if (childId.equals(object.objectID)) {
                    continue;
                }
                descendants.add(childId);
                RevisionStoreObject child = objectsById.get(childId);
                if (child != null && traversed.add(childId)) {
                    pending.add(child);
                }
            }
        }
        return descendants;
    }

    private void walkRootObject(RevisionStoreObject object,
                                Map<ExGuid, RevisionStoreObject> objectsById, Set<ExGuid> visited,
                                OneNoteTreeWalkerOptions options, Metadata metadata,
                                XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        walkRootObject(object, objectsById, visited, AuthorRole.NONE, options, metadata, xhtml, null);
    }

    private void walkRootObject(RevisionStoreObject object,
                                Map<ExGuid, RevisionStoreObject> objectsById, Set<ExGuid> visited,
                                AuthorRole authorRole, OneNoteTreeWalkerOptions options, Metadata metadata,
                                XHTMLContentHandler xhtml, EmbeddedResourceInfo resourceInfo)
            throws SAXException, TikaException, IOException {
        if (!canWalkObject(object, visited, 0)) {
            walkObject(object, objectsById, visited, authorRole, options, metadata, xhtml, 0, resourceInfo);
            return;
        }
        List<PropertyAction> actions = object.propertySet != null
                && object.propertySet.objectSpaceObjectPropSet != null ?
                collectObjectActions(object) : Collections.emptyList();
        ListStyle listStyle = listStyleForObject(object, actions, objectsById);
        if (listStyle != null) {
            startList(listStyle, xhtml);
        }
        try {
            walkObject(object, objectsById, visited, authorRole, options, metadata, xhtml,
                    0, resourceInfo);
        } finally {
            if (listStyle != null) {
                xhtml.endElement(listStyle.elementName);
            }
        }
    }

    private void processStructureChildActions(List<PropertyAction> actions,
                                              Map<ExGuid, RevisionStoreObject> objectsById,
                                              Set<ExGuid> visited, AuthorRole authorRole,
                                              OneNoteTreeWalkerOptions options, Metadata metadata,
                                              XHTMLContentHandler xhtml, int depth,
                                              EmbeddedResourceInfo resourceInfo)
            throws SAXException, TikaException, IOException {
        int index = 0;
        while (index < actions.size()) {
            PropertyAction action = actions.get(index);
            ListStyle style = isSkippedChild(action, objectsById, visited, depth) ? null :
                    listStyleForChild(action, objectsById);
            if (style == null) {
                processAction(action, objectsById, visited, authorRole, options, metadata, xhtml,
                        depth, resourceInfo);
                index++;
                continue;
            }
            startList(style, xhtml);
            int next = index;
            try {
                while (next < actions.size()) {
                    PropertyAction sibling = actions.get(next);
                    if (isSkippedChild(sibling, objectsById, visited, depth)) {
                        processAction(sibling, objectsById, visited, authorRole, options, metadata,
                                xhtml, depth, resourceInfo);
                        next++;
                        continue;
                    }
                    ListStyle siblingStyle = listStyleForChild(sibling, objectsById);
                    if (siblingStyle == null || !style.matches(siblingStyle)) {
                        break;
                    }
                    processAction(sibling, objectsById, visited, authorRole, options, metadata,
                            xhtml, depth, resourceInfo);
                    next++;
                }
            } finally {
                xhtml.endElement(style.elementName);
            }
            index = next;
        }
    }

    private boolean canWalkObject(RevisionStoreObject object, Set<ExGuid> visited, int depth) {
        return object != null && depth < MAX_OBJECT_WALK_DEPTH &&
                (object.objectID == null || !visited.contains(object.objectID));
    }

    private boolean isSkippedChild(PropertyAction action,
                                    Map<ExGuid, RevisionStoreObject> objectsById,
                                    Set<ExGuid> visited, int depth) {
        return action.isChildReference &&
                !canWalkObject(objectsById.get(action.childReference), visited, depth + 1);
    }

    private ListStyle listStyleForChild(PropertyAction action,
                                        Map<ExGuid, RevisionStoreObject> objectsById) {
        if (!action.isChildReference || action.childReference == null) {
            return null;
        }
        RevisionStoreObject child = objectsById.get(action.childReference);
        if (child == null || child.propertySet == null
                || child.propertySet.objectSpaceObjectPropSet == null
                || jcidIndex(child) != OneNoteJcid.OUTLINE_ELEMENT_NODE) {
            return null;
        }
        return listStyleForObject(child, collectObjectActions(child), objectsById);
    }

    private ListStyle listStyleForObject(RevisionStoreObject object, List<PropertyAction> actions,
                                         Map<ExGuid, RevisionStoreObject> objectsById) {
        if (object == null || jcidIndex(object) != OneNoteJcid.OUTLINE_ELEMENT_NODE) {
            return null;
        }
        boolean isListItem = false;
        NumberListInfo numberListInfo = null;
        NumberListInfo malformedNumberListInfo = null;
        for (PropertyAction action : actions) {
            if (action.oneNotePropertyEnum == OneNotePropertyEnum.ListNodes) {
                isListItem = true;
                RevisionStoreObject listNode = action.childReference == null ? null :
                        objectsById.get(action.childReference);
                NumberListInfo candidate = numberListInfo(listNode);
                if (candidate != null && candidate.formatValid) {
                    numberListInfo = candidate;
                } else if (candidate != null) {
                    malformedNumberListInfo = candidate;
                }
            }
        }
        if (!isListItem) {
            return null;
        }
        if (numberListInfo == null) {
            numberListInfo = malformedNumberListInfo;
        }
        boolean numbered = numberListInfo != null && numberListInfo.numbered;
        ListStyle style = new ListStyle(numbered ? "ol" : "ul",
                numberListInfo == null ? null : numberListInfo.htmlType,
                numberListInfo == null ? null : numberListInfo.format,
                numbered ? numberListInfo.restartValue : null);
        return style;
    }

    private NumberListInfo numberListInfo(RevisionStoreObject listNode) {
        if (listNode == null || listNode.propertySet == null
                || listNode.propertySet.objectSpaceObjectPropSet == null
                || jcidIndex(listNode) != OneNoteJcid.NUMBER_LIST_NODE) {
            return null;
        }
        if (numberListInfoCache.containsKey(listNode)) {
            return numberListInfoCache.get(listNode);
        }
        byte[] format = null;
        Long restartValue = null;
        for (PropertyAction action : collectObjectActions(listNode)) {
            if (action.oneNotePropertyEnum == OneNotePropertyEnum.NumberListFormat
                    && action.property instanceof PrtFourBytesOfLengthFollowedByData) {
                format = ((PrtFourBytesOfLengthFollowedByData) action.property).data;
            } else if (action.oneNotePropertyEnum == OneNotePropertyEnum.ListRestart
                    && action.property instanceof FourBytesOfData) {
                restartValue = unsignedInt32(((FourBytesOfData) action.property).data);
            }
        }
        ParsedListFormat parsedFormat = parseListFormat(format);
        NumberListInfo info = new NumberListInfo(canonicalListFormat(format), parsedFormat != null,
                parsedFormat != null && parsedFormat.numbered,
                parsedFormat == null ? null : parsedFormat.htmlType,
                parsedFormat != null && parsedFormat.numbered ? restartValue : null);
        numberListInfoCache.put(listNode, info);
        return info;
    }

    private static ParsedListFormat parseListFormat(byte[] format) {
        if (format == null || format.length < 2 || (format.length & 1) != 0) {
            return null;
        }
        int characterCount = unsignedInt16(format, 0);
        if (characterCount != format.length / 2 - 1) {
            return null;
        }
        for (int i = 2; i + 1 < format.length; i += 2) {
            if (unsignedInt16(format, i) == 0xfffd) {
                if (i + 3 >= format.length) {
                    return null;
                }
                return new ParsedListFormat(true,
                        htmlListType(unsignedInt16(format, i + 2)));
            }
        }
        return new ParsedListFormat(false, null);
    }

    private static int unsignedInt16(byte[] data, int index) {
        return (data[index] & 0xff) | ((data[index + 1] & 0xff) << 8);
    }

    private static Long unsignedInt32(byte[] data) {
        if (data == null || data.length != 4) {
            return null;
        }
        return (data[0] & 0xffL) | ((data[1] & 0xffL) << 8) |
                ((data[2] & 0xffL) << 16) | ((data[3] & 0xffL) << 24);
    }

    private static String htmlListType(int formatCode) {
        switch (formatCode) {
            case 0x00:
                return "1";
            case 0x01:
                return "I";
            case 0x02:
                return "i";
            case 0x03:
                return "A";
            case 0x04:
                return "a";
            default:
                return null;
        }
    }

    private void startList(ListStyle style, XHTMLContentHandler xhtml) throws SAXException {
        if (style.htmlType == null) {
            xhtml.startElement(style.elementName);
        } else {
            xhtml.startElement(style.elementName, "type", style.htmlType);
        }
    }

    private static final class ParsedListFormat {
        private final boolean numbered;
        private final String htmlType;

        private ParsedListFormat(boolean numbered, String htmlType) {
            this.numbered = numbered;
            this.htmlType = htmlType;
        }
    }

    private static final class NumberListInfo {
        private final byte[] format;
        private final boolean formatValid;
        private final boolean numbered;
        private final String htmlType;
        private final Long restartValue;

        private NumberListInfo(byte[] format, boolean formatValid, boolean numbered,
                               String htmlType, Long restartValue) {
            this.format = format;
            this.formatValid = formatValid;
            this.numbered = numbered;
            this.htmlType = htmlType;
            this.restartValue = restartValue;
        }
    }

    private byte[] canonicalListFormat(byte[] format) {
        if (format == null || format.length < MIN_CANONICAL_LIST_FORMAT_LENGTH) {
            return format;
        }
        List<byte[]> candidates = listFormatKeysByDigest.computeIfAbsent(
                DigestUtils.sha256Hex(format), key -> new ArrayList<>());
        for (byte[] candidate : candidates) {
            // Keep grouping exact even if digests collide.
            if (Arrays.equals(candidate, format)) {
                return candidate;
            }
        }
        candidates.add(format);
        return format;
    }

    private static final class ListStyle {
        private final String elementName;
        private final String htmlType;
        private final byte[] format;
        private final Long restartValue;

        private ListStyle(String elementName, String htmlType, byte[] format, Long restartValue) {
            this.elementName = elementName;
            this.htmlType = htmlType;
            this.format = format;
            this.restartValue = restartValue;
        }

        private boolean matches(ListStyle other) {
            return elementName.equals(other.elementName) &&
                    (htmlType == null ? other.htmlType == null : htmlType.equals(other.htmlType)) &&
                    Arrays.equals(format, other.format);
        }
    }

    private boolean hasUsablePicture(ExGuid objectId,
                                      Map<ExGuid, RevisionStoreObject> objectsById,
                                      Set<ExGuid> visited, int depth) {
        if (depth >= MAX_OBJECT_WALK_DEPTH || objectId == null || !visited.add(objectId)) {
            return false;
        }
        RevisionStoreObject object = objectsById.get(objectId);
        if (object == null) {
            return false;
        }
        byte[] data = object.fileDataObject == null ? null : object.fileDataObject.getData();
        if (data != null && data.length > 0) {
            return true;
        }
        if (object.propertySet == null || object.propertySet.objectSpaceObjectPropSet == null) {
            return false;
        }
        for (PropertyAction action : collectObjectActions(object)) {
            if (action.isChildReference && action.childReference != null &&
                    hasUsablePicture(action.childReference, objectsById, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private EmbeddedResourceInfo resourceInfoFromReferencingObject(
            RevisionStoreObject target, Map<ExGuid, RevisionStoreObject> objectsById, int depth) {
        if (depth >= MAX_OBJECT_WALK_DEPTH) {
            return null;
        }
        EmbeddedResourceInfo typeOnlyFallback = null;
        for (RevisionStoreObject candidate : objectsById.values()) {
            if (candidate.propertySet == null ||
                    candidate.propertySet.objectSpaceObjectPropSet == null) {
                continue;
            }
            List<PropertyAction> candidateActions = collectObjectActions(candidate);
            for (PropertyAction action : candidateActions) {
                if (action.isChildReference && target.objectID.equals(action.childReference) &&
                        (action.oneNotePropertyEnum == OneNotePropertyEnum.PictureContainer ||
                                action.oneNotePropertyEnum == OneNotePropertyEnum.EmbeddedFileContainer ||
                                action.oneNotePropertyEnum == OneNotePropertyEnum.WebPictureContainer14)) {
                    EmbeddedResourceInfo info = embeddedResourceInfo(candidateActions);
                    if (info != null) {
                        return info;
                    }
                    if (typeOnlyFallback == null) {
                        typeOnlyFallback = resourceInfoForChild(action, null);
                    }
                }
            }
        }
        return typeOnlyFallback;
    }

    private EmbeddedResourceInfo resourceInfoForChild(PropertyAction action,
                                                       EmbeddedResourceInfo parentInfo) {
        if (action.oneNotePropertyEnum == OneNotePropertyEnum.PictureContainer ||
                action.oneNotePropertyEnum == OneNotePropertyEnum.WebPictureContainer14) {
            return parentInfo == null ? new EmbeddedResourceInfo(null,
                    TikaCoreProperties.EmbeddedResourceType.INLINE.toString()) : parentInfo;
        }
        if (action.oneNotePropertyEnum == OneNotePropertyEnum.EmbeddedFileContainer) {
            return parentInfo == null ? new EmbeddedResourceInfo(null,
                    TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString()) : parentInfo;
        }
        return parentInfo;
    }

    /**
     * Metadata used when emitting an embedded resource.
     */
    private static final class EmbeddedResourceInfo {
        private final String name;
        private final String type;

        private EmbeddedResourceInfo(String name, String type) {
            this.name = name;
            this.type = type;
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
     * Flattens the properties of an object, in order, into a list of actions. The result is
     * cached per object; callers must not mutate the returned list.
     */
    private List<PropertyAction> collectObjectActions(RevisionStoreObject object) {
        List<PropertyAction> actions = objectActionsCache.get(object);
        if (actions != null) {
            return actions;
        }
        List<ExGuid> referencedObjects =
                object.referencedObjectID == null || object.referencedObjectID.content == null ?
                        Collections.emptyList() : object.referencedObjectID.content;
        List<CellID> referencedSpaces = object.referencedObjectSpacesID == null ||
                object.referencedObjectSpacesID.content == null ? Collections.emptyList() :
                object.referencedObjectSpacesID.content;
        actions = new ArrayList<>();
        collectActions(object.propertySet.objectSpaceObjectPropSet.body, referencedObjects,
                new int[]{0}, referencedSpaces, new int[]{0}, actions, 0);
        objectActionsCache.put(object, actions);
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
                                int[] spaceCursor, List<PropertyAction> actions, int depth) {
        if (propertySet == null || propertySet.rgPrids == null || propertySet.rgData == null) {
            return;
        }
        if (depth >= MAX_OBJECT_WALK_DEPTH) {
            recordParseWarning("OneNote property traversal exceeded depth limit " +
                    MAX_OBJECT_WALK_DEPTH);
            return;
        }
        for (int i = 0; i < propertySet.rgPrids.length && i < propertySet.rgData.size(); ++i) {
            IProperty property = propertySet.rgData.get(i);
            PropertyID propertyID = propertySet.rgPrids[i];
            PropertyType propertyType = PropertyType.fromIntVal(propertyID.type);
            OneNotePropertyEnum oneNotePropertyEnum =
                    OneNotePropertyEnum.of(Unsigned.uint(propertyID.value).longValue());
            if (propertyType == PropertyType.ObjectID) {
                ExGuid childReference = nextReference(referencedObjects, referenceCursor);
                if (childReference == null) {
                    recordParseWarning("OneNote object reference slot was exhausted");
                }
                actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum, true,
                        childReference, null));
            } else if (propertyType == PropertyType.ArrayOfObjectIDs) {
                int available = referencedObjects.size() - referenceCursor[0];
                int requestedCount = property instanceof ArrayNumber ?
                        Math.max(0, ((ArrayNumber) property).number) : 0;
                int declaredCount = boundedAvailableReferenceCount(requestedCount, available);
                int count = Math.min(declaredCount, MAX_REFERENCE_COUNT);
                if (requestedCount > available && !warningsSaturated()) {
                    recordParseWarning(ParseWarningKind.OBJECT_REFERENCE_ARRAY_UNAVAILABLE,
                            "OneNote object reference array had unavailable entries " +
                                    "(first occurrence: declared " + requestedCount +
                                    " entries but only " + Math.max(available, 0) +
                                    " were available)");
                }
                if (declaredCount > count && !warningsSaturated()) {
                    recordParseWarning(ParseWarningKind.OBJECT_REFERENCE_ARRAY_CAPPED,
                            "Capping OneNote object reference array at " +
                                    MAX_REFERENCE_COUNT + " entries");
                }
                for (int j = 0; j < count; ++j) {
                    actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum,
                            true, nextReference(referencedObjects, referenceCursor), null));
                }
                referenceCursor[0] += declaredCount - count;
            } else if (propertyType == PropertyType.ObjectSpaceID) {
                CellID spaceReference = nextSpaceReference(referencedSpaces, spaceCursor);
                if (spaceReference == null) {
                    recordParseWarning("OneNote object-space reference slot was exhausted");
                }
                actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum, false,
                        null, spaceReference));
            } else if (propertyType == PropertyType.ArrayOfObjectSpaceIDs) {
                int available = referencedSpaces.size() - spaceCursor[0];
                int requestedCount = property instanceof ArrayNumber ?
                        Math.max(0, ((ArrayNumber) property).number) : 0;
                int declaredCount = boundedAvailableReferenceCount(requestedCount, available);
                int count = Math.min(declaredCount, MAX_REFERENCE_COUNT);
                if (requestedCount > available && !warningsSaturated()) {
                    recordParseWarning(ParseWarningKind.OBJECT_SPACE_REFERENCE_ARRAY_UNAVAILABLE,
                            "OneNote object-space reference array had unavailable entries " +
                                    "(first occurrence: declared " + requestedCount +
                                    " entries but only " + Math.max(available, 0) +
                                    " were available)");
                }
                if (declaredCount > count && !warningsSaturated()) {
                    recordParseWarning(ParseWarningKind.OBJECT_SPACE_REFERENCE_ARRAY_CAPPED,
                            "Capping OneNote object-space reference array at " +
                                    MAX_REFERENCE_COUNT + " entries");
                }
                for (int j = 0; j < count; ++j) {
                    actions.add(new PropertyAction(property, propertyType, oneNotePropertyEnum,
                            false, null, nextSpaceReference(referencedSpaces, spaceCursor)));
                }
                spaceCursor[0] += declaredCount - count;
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
                               Metadata metadata, XHTMLContentHandler xhtml, int depth,
                               EmbeddedResourceInfo parentResourceInfo)
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
            EmbeddedResourceInfo childResourceInfo = resourceInfoForChild(action,
                    parentResourceInfo);
            RevisionStoreObject child = action.childReference == null ? null :
                    objectsById.get(action.childReference);
            if (child == null && action.childReference != null && !warningsSaturated()) {
                recordParseWarning("OneNote object " + action.childReference +
                        " could not be resolved");
            }
            walkObject(child, objectsById, visited, childRole, options, metadata, xhtml,
                    depth + 1, childResourceInfo);
        } else {
            processPrimitiveProperty(action.property, action.propertyType,
                    action.oneNotePropertyEnum, authorRole, options, metadata, xhtml);
        }
    }

    private int boundedAvailableReferenceCount(int declaredCount, int remainingReferences) {
        if (declaredCount <= 0 || remainingReferences <= 0) {
            return 0;
        }
        return Math.min(declaredCount, remainingReferences);
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
        } else if (oneNotePropertyEnum == OneNotePropertyEnum.Author
                && property instanceof PrtFourBytesOfLengthFollowedByData) {
            recordAuthor(decodeOneNoteText(
                    ((PrtFourBytesOfLengthFollowedByData) property).data), authorRole);
        } else if (propertyType == PropertyType.FourBytesOfLengthFollowedByData) {
            boolean isBinary = propertyIsBinary(oneNotePropertyEnum);
            PrtFourBytesOfLengthFollowedByData dataProperty =
                    (PrtFourBytesOfLengthFollowedByData) property;
            if ((dataProperty.data.length & 1) == 0 &&
                    oneNotePropertyEnum != OneNotePropertyEnum.TextExtendedAscii && !isBinary) {
                if (options.getUtf16PropertiesToPrint().contains(oneNotePropertyEnum)) {
                    emitParagraph(xhtml, new String(dataProperty.data,
                            StandardCharsets.UTF_16LE));
                }
            } else if (oneNotePropertyEnum == OneNotePropertyEnum.TextExtendedAscii) {
                emitParagraph(xhtml, new String(dataProperty.data, StandardCharsets.US_ASCII));
            } else if (!isBinary) {
                if (options.getUtf16PropertiesToPrint().contains(oneNotePropertyEnum)) {
                    emitParagraph(xhtml, new String(dataProperty.data,
                            StandardCharsets.UTF_16LE));
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


    private String decodeOneNoteText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_16LE).replace("\u0000", "");
    }

    private void recordAuthor(String author, AuthorRole role) {
        if (role == AuthorRole.MOST_RECENT) {
            mostRecentAuthors.add(author);
        } else if (role == AuthorRole.ORIGINAL) {
            originalAuthors.add(author);
            // the original authors are the creators of the content
            authors.add(author);
        } else {
            authors.add(author);
        }
    }

    /**
     * Records the Author properties of an already-visited object under the given role.
     */
    private void recordAuthors(RevisionStoreObject object, AuthorRole role) {
        // NONE also records (into authors) so the result is visit-order independent
        if (object.propertySet == null ||
                object.propertySet.objectSpaceObjectPropSet == null) {
            return;
        }
        for (PropertyAction action : collectObjectActions(object)) {
            if (action.oneNotePropertyEnum == OneNotePropertyEnum.Author &&
                    action.property instanceof PrtFourBytesOfLengthFollowedByData) {
                recordAuthor(decodeOneNoteText(
                        ((PrtFourBytesOfLengthFollowedByData) action.property).data), role);
            }
        }
    }

    private void collectSectionFileIdentityGuids() {
        if (headerCell != null && headerCell.objectData != null
                && headerCell.objectData.body != null) {
            collectFileIdentityGuids(headerCell.objectData.body, 0);
        }
        if (!sectionGuidCollected() && dataRootCell != null) {
            collectFileIdentityGuidsFromGroups(dataRootCell.objectGroups);
        }
        if (!sectionGuidCollected()) {
            collectFileIdentityGuidsFromGroups(OtherFileNodeList);
        }
    }

    // sectionGuid is a scalar: past the first candidate, further scanning cannot change
    // the output, only burn work on values the collector will refuse
    private boolean sectionGuidCollected() {
        return !guids.values(OneNoteGuidCollector.Category.SECTION).isEmpty();
    }

    void collectFileIdentityGuidsFromGroups(List<RevisionStoreObjectGroup> groups) {
        if (groups == null || guids.isFull()) {
            return;
        }
        for (RevisionStoreObjectGroup group : groups) {
            if (guids.isFull() || sectionGuidCollected()) {
                return;
            }
            if (group == null || group.objects == null) {
                continue;
            }
            for (RevisionStoreObject object : group.objects) {
                if (guids.isFull()) {
                    return;
                }
                if (object == null || object.propertySet == null
                        || object.propertySet.objectSpaceObjectPropSet == null) {
                    continue;
                }
                collectFileIdentityGuids(object.propertySet.objectSpaceObjectPropSet.body, 0);
            }
        }
    }

    void collectFileIdentityGuids(PropertySet propertySet, int depth) {
        if (propertySet == null || propertySet.rgPrids == null || propertySet.rgData == null
                || depth >= PropertySet.MAX_PROPERTY_NESTING || guids.isFull()) {
            return;
        }
        int count = Math.min(propertySet.rgPrids.length, propertySet.rgData.size());
        for (int i = 0; i < count && !guids.isFull() && !sectionGuidCollected(); i++) {
            PropertyID propertyID = propertySet.rgPrids[i];
            IProperty property = propertySet.rgData.get(i);
            long propertyIdValue = Unsigned.uint(propertyID.value).longValue();
            PropertyType propertyType = PropertyType.fromIntVal(propertyID.type);
            if (propertyIdValue == FILE_IDENTITY_GUID_PROPERTY_ID
                    && property instanceof PrtFourBytesOfLengthFollowedByData) {
                GUID guid = GUID.fromMicrosoftBytes(
                        ((PrtFourBytesOfLengthFollowedByData) property).data);
                if (guid != null) {
                    guids.add(OneNoteGuidCollector.Category.SECTION, guid.toString());
                }
            } else if (propertyType == PropertyType.PropertySet && property instanceof PropertySet) {
                collectFileIdentityGuids((PropertySet) property, depth + 1);
            } else if (propertyType == PropertyType.ArrayOfPropertyValues
                    && property instanceof PrtArrayOfPropertyValues
                    && ((PrtArrayOfPropertyValues) property).data != null) {
                for (PropertySet nested : ((PrtArrayOfPropertyValues) property).data) {
                    if (guids.isFull() || sectionGuidCollected()) {
                        return;
                    }
                    collectFileIdentityGuids(nested, depth + 1);
                }
            }
        }
    }

    void recordEntityGuid(RevisionStoreObject object, int objectType) {
        guids.addForObjectType(objectType, notebookManagementEntityGuid(object));
    }

    String findPageGuid(RevisionStoreCell cell,
                                Map<ExGuid, RevisionStoreObject> objectsById) {
        if (cell == null || cell.objectGroups == null) {
            return null;
        }
        Set<ExGuid> visited = new HashSet<>();
        CellRoots roots = cellRoots(cell, objectsById, warning -> { });
        for (RevisionStoreObject rootObject : roots.declared) {
            String guid = findPageGuid(rootObject, objectsById, visited, 0);
            if (guid != null) {
                return guid;
            }
        }
        if (roots.walkAll) {
            for (RevisionStoreObjectGroup group : cell.objectGroups) {
                if (group == null || group.objects == null) {
                    continue;
                }
                for (RevisionStoreObject object : group.objects) {
                    String guid = findPageGuid(object, objectsById, visited, 0);
                    if (guid != null) {
                        return guid;
                    }
                }
            }
        }
        return null;
    }

    String findPageGuid(RevisionStoreObject object,
                                Map<ExGuid, RevisionStoreObject> objectsById,
                                Set<ExGuid> visited, int depth) {
        if (object == null || depth >= MAX_OBJECT_WALK_DEPTH
                || object.propertySet == null
                || object.propertySet.objectSpaceObjectPropSet == null) {
            return null;
        }
        if (object.objectID != null && !visited.add(object.objectID)) {
            return null;
        }
        if (jcidIndex(object) == OneNoteJcid.PAGE_METADATA) {
            String guid = notebookManagementEntityGuid(object);
            if (guid != null) {
                return guid;
            }
        }
        for (PropertyAction action : collectObjectActions(object)) {
            if (action.isChildReference && action.childReference != null) {
                String guid = findPageGuid(objectsById.get(action.childReference), objectsById,
                        visited, depth + 1);
                if (guid != null) {
                    return guid;
                }
            }
        }
        return null;
    }

    String notebookManagementEntityGuid(RevisionStoreObject object) {
        if (object == null || object.propertySet == null
                || object.propertySet.objectSpaceObjectPropSet == null) {
            return null;
        }
        for (PropertyAction action : collectObjectActions(object)) {
            if (action.oneNotePropertyEnum == OneNotePropertyEnum.NotebookManagementEntityGuid
                    && action.property instanceof PrtFourBytesOfLengthFollowedByData) {
                GUID guid = GUID.fromMicrosoftBytes(
                        ((PrtFourBytesOfLengthFollowedByData) action.property).data);
                if (guid != null) {
                    return guid.toString();
                }
            }
        }
        return null;
    }

    static int jcidIndex(RevisionStoreObject object) {
        if (object == null || object.jcid == null || object.jcid.jcid == null) {
            return -1;
        }
        return object.jcid.jcid.index;
    }

    private EmbeddedResourceInfo embeddedResourceInfo(List<PropertyAction> actions) {
        for (PropertyAction action : actions) {
            if (action.property instanceof PrtFourBytesOfLengthFollowedByData &&
                    (action.oneNotePropertyEnum == OneNotePropertyEnum.ImageFilename ||
                            action.oneNotePropertyEnum == OneNotePropertyEnum.EmbeddedFileName)) {
                byte[] bytes = ((PrtFourBytesOfLengthFollowedByData) action.property).data;
                String name = sanitizeResourceName(new String(bytes, StandardCharsets.UTF_16LE)
                        .replace("\u0000", ""));
                if (!name.isEmpty()) {
                    String type = action.oneNotePropertyEnum == OneNotePropertyEnum.ImageFilename ?
                            TikaCoreProperties.EmbeddedResourceType.INLINE.toString() :
                            TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString();
                    return new EmbeddedResourceInfo(name, type);
                }
            }
        }
        return null;
    }

    static String sanitizeResourceName(String name) {
        name = name.replace('\\', '/');
        // a drive-relative name like C:pic.png has no slash, so strip the prefix separately
        if (name.length() > 1 && name.charAt(1) == ':') {
            name = name.substring(2);
        }
        int slash = name.lastIndexOf('/');
        name = slash >= 0 ? name.substring(slash + 1) : name;
        return ".".equals(name) || "..".equals(name) ? "" : name;
    }

    /**
     * Hands the binary data of an embedded object (e.g. an image or an attached file) to the
     * embedded document extractor.
     */
    private void handleEmbedded(byte[] data, XHTMLContentHandler xhtml,
                                EmbeddedResourceInfo resourceInfo, ExGuid objectID)
            throws SAXException, IOException {
        if (data == null || data.length == 0 || embeddedDocumentExtractor == null) {
            return;
        }
        Metadata embeddedMetadata = Metadata.newInstance(this.parseContext);
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                resourceInfo == null ? TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString() :
                        resourceInfo.type);
        if (resourceInfo != null && resourceInfo.name != null) {
            embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, resourceInfo.name);
        }
        String relationshipID = objectID == null ? null : "_" + objectID.value + "_" +
                objectID.guid;
        if (relationshipID != null) {
            embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, relationshipID);
        }
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "embedded");
        if (relationshipID != null) {
            attributes.addAttribute("", "id", "id", "CDATA", relationshipID);
        }
        xhtml.startElement("div", attributes);
        xhtml.endElement("div");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata, parseContext)) {
                // only counts as content when the extractor accepts it, so a declined
                // embedded object in an otherwise-empty file still triggers the fallback
                contentEmitted = true;
                embeddedDocumentExtractor.parseEmbedded(tis, new EmbeddedContentHandler(xhtml),
                        embeddedMetadata, this.parseContext, false);
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata, parseContext);
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
            try {
                xhtml.characters(m.group(2));
            } finally {
                xhtml.endElement("a");
            }
            contentEmitted = true;
        } else {
            emitParagraph(xhtml, txt);
        }
    }

    private void emitParagraph(XHTMLContentHandler xhtml, String text) throws SAXException {
        xhtml.startElement(P);
        try {
            xhtml.characters(text);
        } finally {
            xhtml.endElement(P);
        }
        // blank text does not count as content, so it cannot suppress the legacy fallback
        if (!text.isBlank()) {
            contentEmitted = true;
        }
    }

    /**
     * Whether the tree walk emitted any content - text or an embedded object. When it did
     * not, the caller can fall back to the legacy string dump so a degraded file still
     * yields its text.
     */
    public boolean hasEmittedContent() {
        return contentEmitted;
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
