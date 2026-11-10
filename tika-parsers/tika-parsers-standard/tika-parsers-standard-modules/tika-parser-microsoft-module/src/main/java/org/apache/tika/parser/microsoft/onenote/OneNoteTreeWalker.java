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
package org.apache.tika.parser.microsoft.onenote;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

class OneNoteTreeWalker {

    private static final Logger LOG = LoggerFactory.getLogger(OneNoteTreeWalker.class);
    private static final int MAX_CYCLE_WARNINGS = 100;
    // low enough that the ~3 stack frames per level cannot overflow a default thread stack
    private static final int MAX_WALK_DEPTH = 500;
    // real files revisit each node a few times (once per reference); shared references in a
    // crafted file can make the visits exponential in depth without forming a cycle
    private static final long VISITS_PER_FILE_NODE = 64;
    private static final long MIN_VISIT_BUDGET = 100_000;
    private static final String P = "p";
    /** MS-ONE 2.3.1 TIME32 counts seconds from 1980-01-01 UTC; that epoch in Unix seconds. */
    private static final long TIME32_EPOCH_DIFF_1980;
    /** MS-DTYP 2.3.3 DATETIME counts from 1601-01-01 UTC; that epoch in Unix seconds. */
    private static final long DATETIME_EPOCH_DIFF_1601;

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

    private final Metadata parentMetadata;
    private final ParseContext parseContext;
    private final EmbeddedDocumentExtractor embeddedDocumentExtractor;
    private final Set<String> authors = new HashSet<>();
    private final Set<String> mostRecentAuthors = new HashSet<>();
    private final Set<String> originalAuthors = new HashSet<>();
    private final OneNoteGuidCollector guidCollector;
    private final OneNoteParserConfig options;
    private final OneNoteDocument oneNoteDocument;
    private final OneNoteDirectFileResource dif;
    private final XHTMLContentHandler xhtml;
    private final Pair<Long, ExtendedGUID> roleAndContext;
    private Instant lastModifiedTimestamp = Instant.MIN;
    private long creationTimestamp = Long.MAX_VALUE;
    private long lastModified = Long.MIN_VALUE;

    /** {Offset,Length} pairs already written to the text stream. */
    private final Set<Pair<Long, Integer>> textAlreadyFetched = new HashSet<>();
    private final Set<FileNode> activeFileNodes =
            Collections.newSetFromMap(new IdentityHashMap<>());
    // contexts (parent property, inside version history) each node was walked in; a repeat
    // walk in the same context adds nothing, as text is deduplicated by position
    private final Map<FileNode, Set<Long>> walkedContexts = new IdentityHashMap<>();
    // every revision of a notebook's table of contents lists its sections again
    private final Set<String> emittedFolderChildren = new HashSet<>();
    // recognized words of the inline ink in the paragraph being walked
    private List<String> inkWords = Collections.emptyList();
    private List<OneNoteText.TextRun> textRuns = null;
    private final Set<FileNode> reportedCycleNodes =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private int cycleWarningCount;
    private boolean cycleWarningsSuppressed;
    private int walkDepth;
    private boolean depthWarningRecorded;
    private long visitsRemaining;
    private int versionHistoryDepth;
    private boolean contentEmitted;
    // the revision whose objects the walk resolves, or null for the last declared anywhere
    private ExtendedGUID walkedRevision;
    private final Set<Long> emittedEmbedded = new HashSet<>();
    private boolean visitBudgetWarningRecorded;
    // text read is bounded by the file size; set on first use
    private long textBytesRemaining = Long.MIN_VALUE;
    private boolean textBudgetWarningRecorded;

    /** {@code roleAndContext} is null to crawl all root file nodes, ignoring revisions. */
    public OneNoteTreeWalker(OneNoteParserConfig options, OneNoteDocument oneNoteDocument,
                             OneNoteDirectFileResource dif, XHTMLContentHandler xhtml,
                             Metadata parentMetadata, ParseContext parseContext,
                             Pair<Long, ExtendedGUID> roleAndContext) {
        this.options = options;
        this.oneNoteDocument = oneNoteDocument;
        this.dif = dif;
        this.roleAndContext = roleAndContext;
        this.xhtml = xhtml;
        this.parentMetadata = parentMetadata;
        this.parseContext = parseContext;
        this.guidCollector = new OneNoteGuidCollector(this::recordGuidCapWarning);
        this.visitsRemaining = Math.max(MIN_VISIT_BUDGET,
                VISITS_PER_FILE_NODE * oneNoteDocument.fileNodeCount);
        this.embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
    }

    /** Walks the tree, writing text, metadata and embedded documents as it goes. */
    public void walkTree() throws IOException, TikaException, SAXException {
        try {
            walkRootFileNodes();
        } finally {
            publishGuidBags();
        }
    }

    public void walkRootFileNodes() throws IOException, TikaException, SAXException {
        if (options.isIncludePageHistory()) {
            walkFileNodeList(oneNoteDocument.root, null);
            return;
        }
        Set<ExtendedGUID> seenLists = new HashSet<>();
        List<FileNode> unlabeledLists = new ArrayList<>();
        for (ExtendedGUID revisionListGuid : oneNoteDocument.revisionListOrder) {
            if (!seenLists.add(revisionListGuid)) {
                continue;
            }
            FileNode list = oneNoteDocument.revisionManifestLists.get(revisionListGuid)
                    .dereference(oneNoteDocument);
            if (!walkRevision(list)) {
                unlabeledLists.add(list);
            }
        }
        // a list with no current revision gives text from all of its revisions
        for (FileNode list : unlabeledLists) {
            walkFileNode(list, null);
        }
        // attachments are in a file-wide store, not linked to the revisions that use them
        for (FileNode node : oneNoteDocument.root.children) {
            if (node.id == FndStructureConstants.FileDataStoreListReferenceFND) {
                walkFileNode(node, null);
            }
        }
    }

    private static boolean isRevisionStart(FileNode node) {
        return node.id == FndStructureConstants.RevisionManifestStart4FND ||
                node.id == FndStructureConstants.RevisionManifestStart6FND ||
                node.id == FndStructureConstants.RevisionManifestStart7FND;
    }

    /**
     * @return the revision that holds {@code label} (a revision role and context) last in the
     * manifest list, or null; a later declaration moves a label to another revision
     */
    static ExtendedGUID labeledRevision(List<FileNode> manifestList,
                                        Pair<Long, ExtendedGUID> label) {
        for (int i = manifestList.size() - 1; i >= 0; --i) {
            FileNode node = manifestList.get(i);
            long role;
            if (isRevisionStart(node)) {
                role = node.subType.revisionManifest.revisionRole;
            } else if (node.id == FndStructureConstants.RevisionRoleDeclarationFND ||
                    node.id == FndStructureConstants.RevisionRoleAndContextDeclarationFND) {
                role = node.subType.revisionRoleDeclaration.revisionRole;
            } else {
                continue;
            }
            if (role == label.getLeft() && label.getRight().equals(node.gctxid)) {
                return node.gosid;
            }
        }
        return null;
    }

    /** @return false when no revision in the list holds {@code roleAndContext} */
    private boolean walkRevision(FileNode revisionFileNode)
            throws IOException, TikaException, SAXException {
        List<FileNode> children = revisionFileNode.childFileNodeList.children;
        ExtendedGUID current =
                roleAndContext == null ? null : labeledRevision(children, roleAndContext);
        if (current == null) {
            return false;
        }
        // per root role: 1 content, 2 page metadata, 4 revision metadata (author, times)
        Map<Long, Map<ExtendedGUID, List<ExtendedGUID>>> rootsByRole = new HashMap<>();
        ExtendedGUID rid = null;
        for (FileNode child : children) {
            if (isRevisionStart(child)) {
                rid = child.gosid;
            } else if (rid != null &&
                    (child.id == FndStructureConstants.RootObjectReference2FNDX ||
                            child.id == FndStructureConstants.RootObjectReference3FND)) {
                long role = child.subType.rootObjectReference.rootObjectReferenceBase.rootRole;
                if (role == 1 || role == 2 || role == 4) {
                    rootsByRole.computeIfAbsent(role, k -> new HashMap<>())
                            .computeIfAbsent(rid, k -> new ArrayList<>()).add(child.gosid);
                }
            }
        }
        walkedRevision = current;
        try {
            for (long role : new long[]{1, 2, 4}) {
                for (ExtendedGUID root : contentRoots(current,
                        rootsByRole.getOrDefault(role, Collections.emptyMap()))) {
                    walkFileNodePtr(object(root), null);
                }
            }
        } finally {
            walkedRevision = null;
        }
        return true;
    }

    private FileNodePtr object(ExtendedGUID oid) {
        return walkedRevision == null ? oneNoteDocument.guidToObject.get(oid) :
                oneNoteDocument.objectIn(walkedRevision, oid);
    }

    /** A revision that declares no root of a role keeps its dependency's. */
    private List<ExtendedGUID> contentRoots(ExtendedGUID rid,
                                            Map<ExtendedGUID, List<ExtendedGUID>> contentRoots) {
        ExtendedGUID r = rid;
        for (int i = 0; r != null && i <= oneNoteDocument.revisionMap.size(); i++) {
            List<ExtendedGUID> roots = contentRoots.get(r);
            if (roots != null) {
                return roots;
            }
            Revision revision = oneNoteDocument.revisionMap.get(r);
            r = revision == null ? null : revision.dependent;
        }
        return Collections.emptyList();
    }

    public void walkFileNodePtr(FileNodePtr fileNodePtr, OneNotePropertyId parentPropertyId)
            throws IOException, TikaException, SAXException {
        if (fileNodePtr != null) {
            walkFileNode(fileNodePtr.dereference(oneNoteDocument), parentPropertyId);
        }
    }

    public void walkFileNodeList(FileNodeList fileNodeList, OneNotePropertyId parentPropertyId)
            throws IOException, TikaException, SAXException {
        for (FileNode child : fileNodeList.children) {
            walkFileNode(child, parentPropertyId);
        }
    }

    public void walkFileNode(FileNode fileNode, OneNotePropertyId parentPropertyId)
            throws IOException, TikaException, SAXException {
        if (fileNode == null) {
            return;
        }
        if (walkDepth >= MAX_WALK_DEPTH) {
            // the cycle guard cannot catch a long acyclic chain - cap the recursion depth
            // so a crafted file cannot overflow the stack
            recordDepthWarning();
            return;
        }
        if (visitsRemaining <= 0) {
            recordVisitBudgetWarning();
            return;
        }
        if (!activeFileNodes.add(fileNode)) {
            recordCycleWarning(fileNode);
            return;
        }
        long context = ((parentPropertyId == null ? -1L : parentPropertyId.pid) << 1) |
                (versionHistoryDepth > 0 ? 1 : 0);
        if (!walkedContexts.computeIfAbsent(fileNode, k -> new HashSet<>()).add(context)) {
            activeFileNodes.remove(fileNode);
            return;
        }
        visitsRemaining--;
        walkDepth++;
        try {
            walkFileNodeInternal(fileNode, parentPropertyId);
        } finally {
            walkDepth--;
            activeFileNodes.remove(fileNode);
        }
    }

    private void emitUtf16Property(OneNotePropertyEnum property, String text)
            throws SAXException {
        if (property == OneNotePropertyEnum.FolderChildFilename &&
                !emittedFolderChildren.add(OneNoteText.clean(text))) {
            return;
        }
        emitParagraph(text);
    }

    private void emitParagraph(String text) throws SAXException {
        text = OneNoteText.clean(text);
        if (text == null) {
            return;
        }
        xhtml.startElement(P);
        try {
            xhtml.characters(text);
        } finally {
            xhtml.endElement(P);
        }
        contentEmitted = true;
    }

    /**
     * @return whether the walk wrote any text or handed any embedded document to the
     * extractor
     */
    public boolean hasEmittedContent() {
        return contentEmitted;
    }

    private void recordVisitBudgetWarning() {
        if (visitBudgetWarningRecorded) {
            return;
        }
        visitBudgetWarningRecorded = true;
        String warning = "OneNote file-node traversal exceeded visit budget";
        LOG.warn(warning);
        if (parentMetadata != null) {
            parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        }
    }

    private void recordDepthWarning() {
        if (depthWarningRecorded) {
            return;
        }
        depthWarningRecorded = true;
        String warning = "OneNote file-node traversal exceeded depth limit " + MAX_WALK_DEPTH;
        LOG.warn(warning);
        if (parentMetadata != null) {
            parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        }
    }

    private void recordCycleWarning(FileNode fileNode) {
        if (reportedCycleNodes.contains(fileNode)) {
            return;
        }
        if (cycleWarningCount >= MAX_CYCLE_WARNINGS) {
            if (!cycleWarningsSuppressed) {
                cycleWarningsSuppressed = true;
                String warning = "Additional OneNote file-node cycle warnings were suppressed " +
                        "after " + MAX_CYCLE_WARNINGS + " distinct cycles";
                LOG.warn(warning);
                if (parentMetadata != null) {
                    parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
                }
            }
            return;
        }
        reportedCycleNodes.add(fileNode);
        cycleWarningCount++;
        String warning = "OneNote file-node cycle detected at " + fileNode.gosid;
        LOG.warn(warning);
        if (parentMetadata != null) {
            parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        }
    }

    private void walkFileNodeInternal(FileNode fileNode, OneNotePropertyId parentPropertyId)
            throws IOException, TikaException, SAXException {
        if (fileNode.childFileNodeList != null &&
                fileNode.childFileNodeList.fileNodeListHeader != null) {
            walkFileNodeList(fileNode.childFileNodeList, parentPropertyId);
        }
        if (fileNode.propertySet != null) {
            int jcid = jcidIndex(fileNode);
            boolean history = jcid == OneNoteJcid.VERSION_HISTORY_CONTENT;
            if (history) {
                versionHistoryDepth++;
            }
            try {
                processPropertySet(fileNode.propertySet, parentPropertyId, jcid);
            } finally {
                if (history) {
                    versionHistoryDepth--;
                }
            }
        }
        if (fileNode.subType.fileDataStoreObjectReference.ref != null && !FileChunkReference.nil()
                .equals(fileNode.subType.fileDataStoreObjectReference.ref.fileData)) {
            walkFileDataStoreObjectReference(fileNode.subType.fileDataStoreObjectReference,
                    fileNode.gosid);
        }
    }

    private void walkFileDataStoreObjectReference(
            FileDataStoreObjectReference fileDataStoreObjectReference, ExtendedGUID gosid)
            throws IOException, SAXException, TikaException {
        FileChunkReference fileData = fileDataStoreObjectReference.ref.fileData;
        if (fileData.cb > dif.size()) {
            throw new TikaMemoryLimitException(
                    "File data store cb " + fileData.cb + " exceeds document size: " +
                            dif.size());
        }
        handleEmbedded(fileData.stp, (int) fileData.cb, gosid);
    }

    private void handleEmbedded(long stp, int length, ExtendedGUID gosid)
            throws TikaException, IOException, SAXException {
        if (!emittedEmbedded.add(stp)) {
            return;
        }
        TikaInputStream tis = null;
        Metadata embeddedMetadata = Metadata.newInstance(this.parseContext);
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString());
        if (gosid != null && !ExtendedGUID.nil().equals(gosid)) {
            embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, gosid.toString());
        }
        try {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
            if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata, parseContext)) {
                ByteBuffer buf;
                try {
                    buf = ByteBuffer.allocate(length);
                    dif.position(stp);
                    dif.read(buf);
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata,
                            parseContext);
                    return;
                }
                tis = TikaInputStream.get(buf.array());
                contentEmitted = true;
                embeddedDocumentExtractor.parseEmbedded(tis, new EmbeddedContentHandler(xhtml),
                        embeddedMetadata, this.parseContext, false);
            }
        } finally {
            IOUtils.closeQuietly(tis);
        }

    }

    private void processPropertySet(PropertySet propertySet, OneNotePropertyId parentPropertyId,
                                    int objectType)
            throws IOException, TikaException, SAXException {
        // nested property sets recurse too
        if (walkDepth >= MAX_WALK_DEPTH) {
            recordDepthWarning();
            return;
        }
        walkDepth++;
        try {
            processPropertySetInternal(propertySet, parentPropertyId, objectType);
        } finally {
            walkDepth--;
        }
    }

    private void processPropertySetInternal(PropertySet propertySet,
                                            OneNotePropertyId parentPropertyId, int objectType)
            throws IOException, TikaException, SAXException {
        emitImage(propertySet);
        List<String> outerInkWords = inkWords;
        List<OneNoteText.TextRun> outerTextRuns = textRuns;
        inkWords = inlineInkWords(propertySet);
        textRuns = textRuns(propertySet);
        try {
            for (int i = 0; i < propertySet.rgPridsData.size(); ++i) {
                PropertyValue propertyValue = propertySet.rgPridsData.get(i);
                processPropertyValue(propertyValue, parentPropertyId, objectType);
            }
        } finally {
            inkWords = outerInkWords;
            textRuns = outerTextRuns;
        }
    }

    private void emitImage(PropertySet propertySet)
            throws IOException, TikaException, SAXException {
        String fileName = null;
        String altText = null;
        for (PropertyValue value : propertySet.rgPridsData) {
            OneNotePropertyEnum property = value.propertyId.propertyEnum;
            if (OneNoteText.isImageText(property) && value.propertyId.type == 7) {
                String text = readUtf16(value);
                if (property == OneNotePropertyEnum.ImageFilename) {
                    fileName = text;
                } else {
                    altText = text;
                }
            }
        }
        if (OneNoteText.emitImage(xhtml, fileName, altText)) {
            contentEmitted = true;
        }
    }

    /** @return the paragraph's text runs and which are hyperlink text, or null */
    private List<OneNoteText.TextRun> textRuns(PropertySet propertySet)
            throws IOException, TikaException {
        int[] ends = null;
        List<Boolean> hyperlink = null;
        for (PropertyValue value : propertySet.rgPridsData) {
            OneNotePropertyEnum property = value.propertyId.propertyEnum;
            if (property == OneNotePropertyEnum.TextRunIndex && value.propertyId.type == 7) {
                ends = OneNoteText.textRunEnds(readBytes(value));
            } else if (property == OneNotePropertyEnum.TextRunFormatting &&
                    value.compactIDs != null) {
                hyperlink = new ArrayList<>();
                for (CompactID compactID : value.compactIDs) {
                    FileNodePtr ptr = object(compactID.guid);
                    FileNode style = ptr == null ? null : ptr.dereference(oneNoteDocument);
                    hyperlink.add(style != null && style.propertySet != null &&
                            isHyperlink(style.propertySet));
                }
            }
        }
        return ends == null || hyperlink == null ? null : OneNoteText.textRuns(ends, hyperlink);
    }

    private static boolean isHyperlink(PropertySet style) {
        for (PropertyValue value : style.rgPridsData) {
            if (value.propertyId.propertyEnum == OneNotePropertyEnum.Hyperlink) {
                return value.propertyId.inlineBool;
            }
        }
        return false;
    }

    private byte[] readBytes(PropertyValue value) throws IOException, TikaException {
        ByteBuffer buf = ByteBuffer.allocate(positionAt(value));
        dif.read(buf);
        return buf.array();
    }

    /** Positions {@code dif} at the property's data. @return the data's length */
    private int positionAt(PropertyValue value) throws IOException, TikaException {
        if (value.rawData == null) {
            // not a data property (type 0x7)
            dif.position(0);
            return 0;
        }
        long cb = value.rawData.cb;
        if (cb < 0 || cb > dif.size()) {
            throw new TikaMemoryLimitException(
                    "File data store cb " + cb + " exceeds document size: " + dif.size());
        }
        dif.position(value.rawData.stp);
        return (int) cb;
    }

    /** @return the recognized words of the paragraph's inline ink, in order */
    private List<String> inlineInkWords(PropertySet propertySet)
            throws IOException, TikaException {
        List<String> words = Collections.emptyList();
        for (PropertyValue value : propertySet.rgPridsData) {
            if (value.propertyId.propertyEnum != OneNotePropertyEnum.InlineInkWords ||
                    value.compactIDs == null) {
                continue;
            }
            words = new ArrayList<>();
            for (CompactID compactID : value.compactIDs) {
                FileNodePtr ptr = object(compactID.guid);
                FileNode word = ptr == null ? null : ptr.dereference(oneNoteDocument);
                String alternates = word == null || word.propertySet == null ? null :
                        readAlternates(word.propertySet);
                String best = alternates == null ? null :
                        OneNoteText.bestInkAlternate(alternates);
                // a null keeps later words on their own placeholders
                words.add(best);
            }
        }
        return words;
    }

    private String readAlternates(PropertySet word) throws IOException, TikaException {
        for (PropertyValue value : word.rgPridsData) {
            if (value.propertyId.propertyEnum == OneNotePropertyEnum.InkRecognitionAlternates &&
                    value.propertyId.type == 7) {
                return readString(positionAt(value), StandardCharsets.UTF_16LE);
            }
        }
        return null;
    }

    /** @return the property's UTF-16 text, or null if it was already emitted */
    private String readUtf16(PropertyValue value) throws IOException, TikaException {
        int size = positionAt(value);
        return shouldEmitText(size) ? readString(size, StandardCharsets.UTF_16LE) : null;
    }

    private boolean propertyIsBinary(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.RgOutlineIndentDistance ||
                property == OneNotePropertyEnum.NotebookManagementEntityGuid ||
                property == OneNotePropertyEnum.RichEditTextUnicode ||
                property == OneNotePropertyEnum.CachedTitleString;
    }

    /**
     * Processes a property value: records its metadata, writes its text and walks the
     * objects it references.
     */
    void processPropertyValue(PropertyValue propertyValue, OneNotePropertyId parentPropertyId,
                              int objectType)
            throws IOException, TikaException, SAXException {
        if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.LastModifiedTimeStamp) {
            long fullval = propertyValue.scalar;
            Instant instant = Instant.ofEpochSecond(fullval / 10000000 + DATETIME_EPOCH_DIFF_1601);
            if (instant.isAfter(lastModifiedTimestamp)) {
                lastModifiedTimestamp = instant;
            }
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.CreationTimeStamp) {
            long creationTs = propertyValue.scalar + TIME32_EPOCH_DIFF_1980;
            if (creationTs < creationTimestamp) {
                creationTimestamp = creationTs;
            }
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.LastModifiedTime) {
            long lastMod = propertyValue.scalar + TIME32_EPOCH_DIFF_1980;
            if (lastMod > lastModified) {
                lastModified = lastMod;
            }
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.Author) {
            String author = getAuthor(propertyValue);
            // the role is the reference the author object was reached through
            OneNotePropertyEnum role =
                    parentPropertyId == null ? null : parentPropertyId.propertyEnum;
            if (!author.isBlank() && author.length() <= OneNoteText.MAX_AUTHOR_CHARS &&
                    authors.size() + mostRecentAuthors.size() + originalAuthors.size() <
                            OneNoteText.MAX_AUTHORS) {
                if (role == OneNotePropertyEnum.AuthorMostRecent) {
                    mostRecentAuthors.add(author);
                } else if (role == OneNotePropertyEnum.AuthorOriginal) {
                    originalAuthors.add(author);
                } else {
                    authors.add(author);
                }
            }
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.AuthorMostRecent ||
                propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.AuthorOriginal) {
            // reference only; its target records the author under this role
        } else if (propertyValue.propertyId.type > 0 && propertyValue.propertyId.type <= 6) {
            // scalar: nothing to read
        } else {
            int size = positionAt(propertyValue);
            OneNotePropertyEnum property = propertyValue.propertyId.propertyEnum;
            if (OneNoteText.isExtendedAscii(property)) {
                if (shouldEmitText(size)) {
                    emitParagraph(readString(size, OneNoteText.EXTENDED_ASCII));
                }
            } else if (!propertyIsBinary(property)) {
                if (property == OneNotePropertyEnum.FolderChildFilename && shouldEmitText(size)) {
                    emitUtf16Property(property, readString(size, StandardCharsets.UTF_16LE));
                }
            } else if (property == OneNotePropertyEnum.RichEditTextUnicode ||
                    property == OneNotePropertyEnum.CachedTitleString) {
                if ((parentPropertyId != null || options.isIncludePageHistory()) &&
                        shouldEmitText(size)) {
                    handleRichEditTextUnicode(size);
                }
            } else if (property == OneNotePropertyEnum.NotebookManagementEntityGuid &&
                    size == 16) {
                ByteBuffer guidBuffer = ByteBuffer.allocate(16);
                dif.read(guidBuffer);
                String guid = GUID.fromMicrosoftBytes(guidBuffer.array()).toString();
                // version-history copies of a page are object-space roots; only the current
                // page's metadata is referenced via MetaDataObjectsAboveGraphSpace
                if (objectType != OneNoteJcid.PAGE_METADATA || (parentPropertyId != null &&
                        parentPropertyId.propertyEnum ==
                                OneNotePropertyEnum.MetaDataObjectsAboveGraphSpace)) {
                    addClassicEntityGuid(objectType, guid);
                }
            }
        }
        if (propertyValue.compactIDs != null) {
            // everything below a version-history reference is an older copy
            boolean history = propertyValue.propertyId.propertyEnum ==
                    OneNotePropertyEnum.VersionHistoryGraphSpaceContextNodes;
            if (history) {
                versionHistoryDepth++;
            }
            try {
                for (CompactID compactID : propertyValue.compactIDs) {
                    FileNodePtr childFileNodePointer =
                            object(compactID.guid);
                    walkFileNodePtr(childFileNodePointer, propertyValue.propertyId);
                }
            } finally {
                if (history) {
                    versionHistoryDepth--;
                }
            }
        }
        if (propertyValue.propertySet != null && propertyValue.propertySet.rgPridsData != null) {
            processPropertySet(propertyValue.propertySet, parentPropertyId, objectType);
        }
    }

    /** @return the next {@code length} bytes at {@code dif}'s position, decoded */
    private String readString(int length, Charset charset) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        dif.read(buf);
        return new String(buf.array(), charset);
    }

    /** Reads a UTF-16LE author string, without its NUL terminator. */
    private String getAuthor(PropertyValue propertyValue)
            throws IOException, TikaException {
        int size = positionAt(propertyValue);
        if (size > 2 * (OneNoteText.MAX_AUTHOR_CHARS + 1)) {
            return "";
        }
        return readString(size, StandardCharsets.UTF_16LE).replace("\u0000", "");
    }

    /**
     * Whether text at the current {@code dif} position should be written: not already written
     * (the walk can reach shared objects more than once) and, unless all revisions are asked
     * for, not under a version-history reference.
     */
    private boolean shouldEmitText(int length)
            throws IOException {
        if (!options.isIncludePageHistory() && versionHistoryDepth > 0) {
            return false;
        }
        if (!textAlreadyFetched.add(Pair.of(dif.position(), length))) {
            return false;
        }
        if (textBytesRemaining == Long.MIN_VALUE) {
            textBytesRemaining = 2 * dif.size();
        }
        textBytesRemaining -= length;
        if (textBytesRemaining < 0) {
            // overlapping text regions in a crafted file
            recordTextBudgetWarning();
            return false;
        }
        return true;
    }

    private void recordTextBudgetWarning() {
        if (textBudgetWarningRecorded) {
            return;
        }
        textBudgetWarningRecorded = true;
        String warning = "OneNote text exceeds twice the file size; the rest is skipped";
        LOG.warn(warning);
        if (parentMetadata != null) {
            parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        }
    }

    private void handleRichEditTextUnicode(int length)
            throws SAXException, IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        dif.read(buf);
        String txt = OneNoteText.richEditText(buf.array());
        if (txt.isEmpty()) {
            return;
        }
        if (OneNoteText.isRichText(txt, inkWords)) {
            if (OneNoteText.emitRichText(xhtml, txt, textRuns, inkWords)) {
                contentEmitted = true;
            }
        } else {
            emitParagraph(txt);
        }
    }

    void addClassicEntityGuid(int objectType, String guid) {
        guidCollector.addForObjectType(objectType, guid);
    }

    private void publishGuidBags() {
        if (parentMetadata != null) {
            guidCollector.publish(parentMetadata);
        }
    }

    /**
     * Exempt from any warning budget: capped GUID metadata must always carry its own
     * incompleteness signal.
     */
    private void recordGuidCapWarning(String warning) {
        LOG.warn(warning);
        if (parentMetadata != null) {
            parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        }
    }


    static int jcidIndex(FileNode fileNode) {
        if (fileNode == null || fileNode.subType == null
                || fileNode.subType.objectDeclarationWithRefCount == null
                || fileNode.subType.objectDeclarationWithRefCount.body == null
                || fileNode.subType.objectDeclarationWithRefCount.body.jcid == null) {
            return -1;
        }
        return (int) fileNode.subType.objectDeclarationWithRefCount.body.jcid.index;
    }

    public Set<String> getAuthors() {
        return authors;
    }

    public Set<String> getMostRecentAuthors() {
        return mostRecentAuthors;
    }

    public Set<String> getOriginalAuthors() {
        return originalAuthors;
    }

    public Instant getLastModifiedTimestamp() {
        return lastModifiedTimestamp;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }
}
