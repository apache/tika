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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Walk the one note tree and create a Map while it goes.
 * Also writes user input text to a print writer as it parses.
 */
class OneNoteTreeWalker {

    private static final String P = "p";
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
    private static Pattern HYPERLINK_PATTERN =
            Pattern.compile("\uFDDFHYPERLINK\\s+\"([^\"]+)\"([^\"]+)$");

    static {
        LocalDateTime time32Epoch1980 = LocalDateTime.of(
                1980, Month.JANUARY, 1, 0, 0);
        Instant instant = time32Epoch1980.atZone(ZoneOffset.UTC).toInstant();
        TIME32_EPOCH_DIFF_1980 = (instant.toEpochMilli() - Instant.EPOCH.toEpochMilli()) / 1000;
    }

    static {
        LocalDateTime time32Epoch1601 = LocalDateTime.of(
                1601, Month.JANUARY, 1, 0, 0);
        Instant instant = time32Epoch1601.atZone(ZoneOffset.UTC).toInstant();
        DATETIME_EPOCH_DIFF_1601 = (instant.toEpochMilli() - Instant.EPOCH.toEpochMilli()) / 1000;
    }

    private final Metadata parentMetadata;
    private final EmbeddedDocumentExtractor embeddedDocumentExtractor;
    private final Set<String> authors = new HashSet<>();
    private final Set<String> mostRecentAuthors = new HashSet<>();
    private final Set<String> originalAuthors = new HashSet<>();
    private OneNoteTreeWalkerOptions options;
    private OneNoteDocument oneNoteDocument;
    private OneNoteDirectFileResource dif;
    private XHTMLContentHandler xhtml;
    private Pair<Long, ExtendedGUID> roleAndContext;
    private Instant lastModifiedTimestamp = Instant.MIN;
    private long creationTimestamp = Long.MAX_VALUE;
    private long lastModified = Long.MIN_VALUE;
    private boolean mostRecentAuthorProp = false;
    private boolean originalAuthorProp = false;

    /**
     * Create a one tree walker.
     *
     * @param options         The options for how to walk this tree.
     * @param oneNoteDocument The one note document we want to walk.
     * @param dif             The rando  file access structure we read and reposition while
     *                        extracting the content.
     * @param xhtml           The XHTMLContentHandler to populate as you walk the tree.
     * @param roleAndContext  The role  nd context value we want to use when crawling. Set this
     *                        to null if you are
     *                        crawling all root file nodes, and don't care about revisions.
     */
    public OneNoteTreeWalker(OneNoteTreeWalkerOptions options, OneNoteDocument oneNoteDocument,
                             OneNoteDirectFileResource dif, XHTMLContentHandler xhtml,
                             Metadata parentMetadata, ParseContext parseContext,
                             Pair<Long, ExtendedGUID> roleAndContext) {
        this.options = options;
        this.oneNoteDocument = oneNoteDocument;
        this.dif = dif;
        this.roleAndContext = roleAndContext;
        this.xhtml = xhtml;
        this.parentMetadata = parentMetadata;
        this.embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
    }

    /**
     * Parse the tree.
     *
     * @return Map of the fully parsed one note document.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    public Map<String, Object> walkTree() throws IOException, TikaException, SAXException {
        Map<String, Object> structure = new HashMap<>();
        structure.put("header", oneNoteDocument.header);
        structure.put("rootFileNodes", walkRootFileNodes());
        return structure;
    }

    /**
     * Walk the root file nodes, depending on the options will crawl revisions or the entire
     * revision tree.
     *
     * @return List of the root file nodes.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    public List<Map<String, Object>> walkRootFileNodes()
            throws IOException, TikaException, SAXException {
        List<Map<String, Object>> res = new ArrayList<>();
        if (options.isCrawlAllFileNodesFromRoot()) {
            res.add(walkFileNodeList(oneNoteDocument.root));
        } else {
            for (ExtendedGUID revisionListGuid : oneNoteDocument.revisionListOrder) {
                Map<String, Object> structure = new HashMap<>();
                structure.put("oneNoteType", "Revision");
                structure.put("revisionListGuid", revisionListGuid.toString());
                FileNodePtr fileNodePtr =
                        oneNoteDocument.revisionManifestLists.get(revisionListGuid);
                structure.put("fileNode", walkRevision(fileNodePtr));
                res.add(structure);
            }
        }
        return res;
    }

    /**
     * Does the revision role map have this revision role id.
     *
     * @param rid          The revision id.
     * @param revisionRole The revision role Long,GUID pair.
     * @return True if exists, false if not.
     */
    private boolean hasRevisionRole(ExtendedGUID rid, Pair<Long, ExtendedGUID> revisionRole) {
        Pair<Long, ExtendedGUID> where = oneNoteDocument.revisionRoleMap.get(rid);
        return where != null && where.equals(revisionRole);
    }

    /**
     * Walk revisions.
     *
     * @param fileNodePtr The file node pointer to start with.
     * @return A map of the parsed data.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    private Map<String, Object> walkRevision(FileNodePtr fileNodePtr)
            throws IOException, TikaException, SAXException {
        Map<String, Object> structure = new HashMap<>();
        structure.put("oneNoteType", "FileNodePointer");
        structure.put("offsets", fileNodePtr.nodeListPositions);
        FileNode revisionFileNode = fileNodePtr.dereference(oneNoteDocument);
        structure.put("fileNodeId", revisionFileNode.id);
        if (revisionFileNode.gosid != null) {
            structure.put("gosid", revisionFileNode.gosid.toString());
        }
        structure.put("subType", revisionFileNode.subType);
        structure.put("size", revisionFileNode.size);
        structure.put("isFileData", revisionFileNode.isFileData);

        Set<ExtendedGUID> validRevisions = new HashSet<>();
        for (int i = revisionFileNode.childFileNodeList.children.size() - 1; i >= 0; --i) {
            FileNode child = revisionFileNode.childFileNodeList.children.get(i);
            if (roleAndContext != null && hasRevisionRole(child.gosid, roleAndContext)) {
                validRevisions.add(child.gosid);
                if (options.isOnlyLatestRevision()) {
                    break;
                }
            }
        }
        List<Map<String, Object>> children = new ArrayList<>();
        boolean okGroup = false;
        for (FileNode child : revisionFileNode.childFileNodeList.children) {
            if (child.id == FndStructureConstants.RevisionManifestStart4FND ||
                    child.id == FndStructureConstants.RevisionManifestStart6FND ||
                    child.id == FndStructureConstants.RevisionManifestStart7FND) {
                okGroup = validRevisions.contains(child.gosid);
            }
            if (okGroup) {
                if ((child.id == FndStructureConstants.RootObjectReference2FNDX ||
                        child.id == FndStructureConstants.RootObjectReference3FND) &&
                        child.subType.rootObjectReference.rootObjectReferenceBase.rootRole == 1) {
                    FileNodePtr childFileNodePointer =
                            oneNoteDocument.guidToObject.get(child.gosid);
                    children.add(walkFileNodePtr(childFileNodePointer));
                }
            }
        }
        if (!children.isEmpty()) {
            Map<String, Object> childFileNodeListMap = new HashMap<>();
            childFileNodeListMap.put("fileNodeListHeader",
                    revisionFileNode.childFileNodeList.fileNodeListHeader);
            childFileNodeListMap.put("children", children);
            structure.put("revisionFileNodeList", childFileNodeListMap);
        }
        return structure;
    }

    /**
     * Walk the file node pointer.
     *
     * @param fileNodePtr The file node pointer.
     * @return Returns a map of the main data.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    public Map<String, Object> walkFileNodePtr(FileNodePtr fileNodePtr)
            throws IOException, TikaException, SAXException {
        if (fileNodePtr != null) {
            FileNode fileNode = fileNodePtr.dereference(oneNoteDocument);
            return walkFileNode(fileNode);
        }
        return Collections.emptyMap();
    }

    /**
     * Walk the file node list.
     *
     * @param fileNodeList The file node list to parse.
     * @return The result.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    public Map<String, Object> walkFileNodeList(FileNodeList fileNodeList)
            throws IOException, TikaException, SAXException {
        Map<String, Object> structure = new HashMap<>();
        structure.put("oneNoteType", "FileNodeList");
        structure.put("fileNodeListHeader", fileNodeList.fileNodeListHeader);
        if (!fileNodeList.children.isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (FileNode child : fileNodeList.children) {
                children.add(walkFileNode(child));
            }
            structure.put("children", children);
        }
        return structure;
    }

    /**
     * Walk a single file node.
     *
     * @param fileNode The file node.
     * @return Map which is result of the parsed file node.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    public Map<String, Object> walkFileNode(FileNode fileNode)
            throws IOException, TikaException, SAXException {
        Map<String, Object> structure = new HashMap<>();
        structure.put("oneNoteType", "FileNode");
        structure.put("gosid", fileNode.gosid.toString());
        structure.put("size", fileNode.size);
        structure.put("fileNodeId", "0x" + Long.toHexString(fileNode.id));
        structure.put("fileNodeIdName", FndStructureConstants.nameOf(fileNode.id));
        structure.put("fileNodeBaseType", "0x" + Long.toHexString(fileNode.baseType));
        structure.put("isFileData", fileNode.isFileData);
        structure.put("idDesc", fileNode.idDesc);
        if (fileNode.childFileNodeList != null &&
                fileNode.childFileNodeList.fileNodeListHeader != null) {
            structure.put("childFileNodeList", walkFileNodeList(fileNode.childFileNodeList));
        }
        if (fileNode.propertySet != null) {
            List<Map<String, Object>> propSet = processPropertySet(fileNode.propertySet);
            if (!propSet.isEmpty()) {
                structure.put("propertySet", propSet);
            }
        }
        if (fileNode.subType.fileDataStoreObjectReference.ref != null && !FileChunkReference.nil()
                .equals(fileNode.subType.fileDataStoreObjectReference.ref.fileData)) {
            structure.put("fileDataStoreObjectReference", walkFileDataStoreObjectReference(
                    fileNode.subType.fileDataStoreObjectReference));
        }
        return structure;
    }

    /**
     * Walk a file data store object reference.
     *
     * @param fileDataStoreObjectReference The file data store object reference we are parsing.
     * @return Map containing parsed content.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    private Map<String, Object> walkFileDataStoreObjectReference(
            FileDataStoreObjectReference fileDataStoreObjectReference)
            throws IOException, SAXException, TikaException {
        Map<String, Object> structure = new HashMap<>();
        OneNotePtr content = new OneNotePtr(oneNoteDocument, dif);
        content.reposition(fileDataStoreObjectReference.ref.fileData);
        if (fileDataStoreObjectReference.ref.fileData.cb > dif.size()) {
            throw new TikaMemoryLimitException(
                    "File data store cb " + fileDataStoreObjectReference.ref.fileData.cb +
                            " exceeds document size: " + dif.size());
        }
        handleEmbedded((int) fileDataStoreObjectReference.ref.fileData.cb);
        structure.put("fileDataStoreObjectMetadata", fileDataStoreObjectReference);
        return structure;
    }

    private void handleEmbedded(int length) throws TikaException, IOException, SAXException {
        TikaInputStream stream = null;
        ByteBuffer buf = null;
        try {
            buf = ByteBuffer.allocate(length);
            dif.read(buf);
        } catch (IOException e) {
            //store this exception in the parent's metadata
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
            return;
        }
        Metadata embeddedMetadata = new Metadata();
        try {
            stream = TikaInputStream.get(buf.array());
            embeddedDocumentExtractor
                    .parseEmbedded(stream, new EmbeddedContentHandler(xhtml), embeddedMetadata,
                            false);
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        } finally {
            IOUtils.closeQuietly(stream);
        }

    }

    /**
     * @param propertySet
     * @return
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    private List<Map<String, Object>> processPropertySet(PropertySet propertySet)
            throws IOException, TikaException, SAXException {
        List<Map<String, Object>> propValues = new ArrayList<>();
        for (PropertyValue propertyValue : propertySet.rgPridsData) {
            propValues.add(processPropertyValue(propertyValue));
        }
        return propValues;
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
     * Process a property value and populate a map containing all the property value data.
     * <p>
     * Parse out any relevant text and write it to the print writer as well for easy search
     * engine parsing.
     *
     * @param propertyValue The property value we are parsing.
     * @return The map parsed by this property value.
     * @throws IOException Can throw these when manipulating the seekable byte channel.
     */
    private Map<String, Object> processPropertyValue(PropertyValue propertyValue)
            throws IOException, TikaException, SAXException {
        Map<String, Object> propMap = new HashMap<>();
        propMap.put("oneNoteType", "PropertyValue");
        propMap.put("propertyId", propertyValue.propertyId.toString());

        if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.LastModifiedTimeStamp) {
            long fullval = propertyValue.scalar;
            Instant instant = Instant.ofEpochSecond(fullval / 10000000 + DATETIME_EPOCH_DIFF_1601);
            if (instant.isAfter(lastModifiedTimestamp)) {
                lastModifiedTimestamp = instant;
            }
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.CreationTimeStamp) {
            // add the TIME32_EPOCH_DIFF_1980 because OneNote TIME32 epoch time is per 1980, not
            // 1970
            long creationTs = propertyValue.scalar + TIME32_EPOCH_DIFF_1980;
            if (creationTs < creationTimestamp) {
                creationTimestamp = creationTs;
            }
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.LastModifiedTime) {
            // add the TIME32_EPOCH_DIFF_1980 because OneNote TIME32 epoch time is per 1980, not
            // 1970
            long lastMod = propertyValue.scalar + TIME32_EPOCH_DIFF_1980;
            if (lastMod > lastModified) {
                lastModified = lastMod;
            }
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.Author) {
            String author = getAuthor(propertyValue);
            if (mostRecentAuthorProp) {
                propMap.put("MostRecentAuthor", author);
                mostRecentAuthors.add(author);
            } else if (originalAuthorProp) {
                propMap.put("OriginalAuthor", author);
                originalAuthors.add(author);
            } else {
                propMap.put("Author", author);
                authors.add(author);
            }
            mostRecentAuthorProp = false;
            originalAuthorProp = false;
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.AuthorMostRecent) {
            mostRecentAuthorProp = true;
        } else if (propertyValue.propertyId.propertyEnum == OneNotePropertyEnum.AuthorOriginal) {
            originalAuthorProp = true;
        } else if (propertyValue.propertyId.type > 0 && propertyValue.propertyId.type <= 6) {
            propMap.put("scalar", propertyValue.scalar);
        } else {
            OneNotePtr content = new OneNotePtr(oneNoteDocument, dif);
            content.reposition(propertyValue.rawData);
            boolean isBinary = propertyIsBinary(propertyValue.propertyId.propertyEnum);
            propMap.put("isBinary", isBinary);
            if ((content.size() & 1) == 0 && propertyValue.propertyId.propertyEnum !=
                    OneNotePropertyEnum.TextExtendedAscii && !isBinary) {
                if (content.size() > dif.size()) {
                    throw new TikaMemoryLimitException(
                            "File data store cb " + content.size() + " exceeds document size: " +
                                    dif.size());
                }
                ByteBuffer buf = ByteBuffer.allocate(content.size());
                dif.read(buf);
                propMap.put("dataUnicode16LE", new String(buf.array(), StandardCharsets.UTF_16LE));
                if (options.getUtf16PropertiesToPrint().contains(propertyValue.propertyId)) {
                    xhtml.startElement(P);
                    xhtml.characters((String) propMap.get("dataUnicode16LE"));
                    xhtml.endElement(P);
                }
            } else if (propertyValue.propertyId.propertyEnum ==
                    OneNotePropertyEnum.TextExtendedAscii) {
                if (content.size() > dif.size()) {
                    throw new TikaMemoryLimitException(
                            "File data store cb " + content.size() + " exceeds document size: " +
                                    dif.size());
                }
                ByteBuffer buf = ByteBuffer.allocate(content.size());
                dif.read(buf);
                propMap.put("dataAscii", new String(buf.array(), StandardCharsets.US_ASCII));
                xhtml.startElement(P);
                xhtml.characters((String) propMap.get("dataAscii"));
                xhtml.endElement(P);
            } else if (!isBinary) {
                if (content.size() > dif.size()) {
                    throw new TikaMemoryLimitException(
                            "File data store cb " + content.size() + " exceeds document size: " +
                                    dif.size());
                }
                ByteBuffer buf = ByteBuffer.allocate(content.size());
                dif.read(buf);
                propMap.put("dataUnicode16LE", new String(buf.array(), StandardCharsets.UTF_16LE));
                if (options.getUtf16PropertiesToPrint().contains(propertyValue.propertyId)) {
                    xhtml.startElement(P);
                    xhtml.characters((String) propMap.get("dataUnicode16LE"));
                    xhtml.endElement(P);
                }
            } else {
                if (content.size() > dif.size()) {
                    throw new TikaMemoryLimitException(
                            "File data store cb " + content.size() + " exceeds document size: " +
                                    dif.size());
                }
                if (propertyValue.propertyId.propertyEnum ==
                        OneNotePropertyEnum.RichEditTextUnicode) {
                    handleRichEditTextUnicode(content.size());
                } else {
                    //TODO -- these seem to be somewhat broken font files and other
                    //odds and ends...what are they and how should we process them?
                    //handleEmbedded(content.size());
                }
            }
        }
        if (propertyValue.compactIDs != null) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (CompactID compactID : propertyValue.compactIDs) {
                FileNodePtr childFileNodePointer = oneNoteDocument.guidToObject.get(compactID.guid);
                children.add(walkFileNodePtr(childFileNodePointer));
            }
            if (!children.isEmpty()) {
                propMap.put("children", children);
            }
        }
        if (propertyValue.propertySet != null && propertyValue.propertySet.rgPridsData != null) {
            List<Map<String, Object>> propSet = processPropertySet(propertyValue.propertySet);
            if (!propSet.isEmpty()) {
                propMap.put("propertySet", propSet);
            }
        }
        return propMap;
    }

    /**
     * returns a UTF-16LE author string.
     *
     * @param propertyValue The property value of an author.
     * @return Resulting author string in UTF-16LE format.
     */
    private String getAuthor(PropertyValue propertyValue)
            throws IOException, TikaMemoryLimitException {
        OneNotePtr content = new OneNotePtr(oneNoteDocument, dif);
        content.reposition(propertyValue.rawData);
        if (content.size() > dif.size()) {
            throw new TikaMemoryLimitException(
                    "File data store cb " + content.size() + " exceeds document size: " +
                            dif.size());
        }
        ByteBuffer buf = ByteBuffer.allocate(content.size());
        dif.read(buf);
        return new String(buf.array(), StandardCharsets.UTF_16LE);
    }

    private void handleRichEditTextUnicode(int length)
            throws SAXException, IOException, TikaException {
        //this is a null-ended UTF-16LE string
        ByteBuffer buf = ByteBuffer.allocate(length);
        dif.read(buf);
        byte[] arr = buf.array();
        //look for the first null
        int firstNull = 0;
        for (int i = 0; i < arr.length - 1; i += 2) {
            if (arr[i] == 0 && arr[i + 1] == 0) {
                firstNull = (i > 0) ? i : 0;
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

    public void setLastModifiedTimestamp(Instant lastModifiedTimestamp) {
        this.lastModifiedTimestamp = lastModifiedTimestamp;
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

    public void setCreationTimestamp(long creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }
}
