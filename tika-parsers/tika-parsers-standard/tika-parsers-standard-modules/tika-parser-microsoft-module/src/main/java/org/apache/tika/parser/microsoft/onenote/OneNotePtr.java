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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;

/** An offset and end bound in the file, with the deserializers for its MS-ONESTORE structures. */
class OneNotePtr {

    public static final long FOOTER_CONST = 0x8BC215C38233BA4BL;
    public static final int IFNDF_GUID_LENGTH = 38; // 36 char guid with a { and a } char.
    public static final int NUM_RESERVED_BYTES_AT_END_OF_HEADER = 728;
    private static final Logger LOG = LoggerFactory.getLogger(OneNotePtr.class);
    private static final byte[] IFNDF =
            new byte[]{60, 0, 105, 0, 102, 0, 110, 0, 100, 0, 102, 0, 62, 0};
    private static final String PACKAGE_STORAGE_FILE_FORMAT_GUID =
            "{638DE92F-A6D4-4BC1-9A36-B3FC2511A5B7}";
    private static final int MAX_PROPERTY_VALUES = 100_000;
    // real files spend at least 12.9 bytes per property value
    private static final int MIN_BYTES_PER_PROPERTY_VALUE = 8;
    private static final int MAX_PROPERTY_SET_DEPTH = 100;
    // the format nests file node lists only a handful of levels deep; 100 leaves wide
    // margin while keeping the recursion far from any stack limit
    private static final int MAX_FILE_NODE_LIST_DEPTH = 100;

    // the per-node cap still lets many GlobalIdTableEntry3 nodes copy entries without bound
    private static final long MAX_GLOBAL_ID_COPIES = 10_000_000;
    // floor for the limits below that scale with file size
    private static final long MIN_DOCUMENT_LIMIT = 100_000;

    /**
     * Limits shared by every pointer copied while parsing one document. Each scales with file
     * size, so structures that point at the same bytes many times cannot multiply them.
     */
    private static final class DocumentBudget {
        private long globalIdCopiesRemaining;
        private long propertyValuesRemaining;
        // object stream entries are 4 bytes each
        private long streamEntriesRemaining;
        // file nodes are at least 4 bytes each
        private long fileNodesRemaining;

        DocumentBudget(long fileSize) {
            propertyValuesRemaining = propertyValueLimit(fileSize);
            globalIdCopiesRemaining =
                    Math.min(MAX_GLOBAL_ID_COPIES, Math.max(MIN_DOCUMENT_LIMIT, fileSize));
            streamEntriesRemaining = Math.max(MIN_DOCUMENT_LIMIT, fileSize / 4);
            fileNodesRemaining = Math.max(MIN_DOCUMENT_LIMIT, fileSize / 4);
        }
    }

    /**
     * Recursion state for the baseType-2 file-node-list nesting, shared by every pointer
     * copied while parsing one document.
     */
    private static final class FileNodeListRecursion {
        private int depth;
        // each list is referenced once in a valid file; a second reference would re-parse
        // the whole subtree, exponentially so for a chain of shared lists
        private final Set<Long> parsedListOffsets = new HashSet<>();
    }

    int indentLevel = 0;
    private FileNodeListRecursion fileNodeListRecursion = new FileNodeListRecursion();
    private DocumentBudget documentBudget;

    long offset;
    long end;

    OneNoteDocument document;
    OneNoteDirectFileResource dif;

    public OneNotePtr(OneNoteDocument document, OneNoteDirectFileResource oneNoteDirectFileResource)
            throws IOException {
        this.document = document;
        this.dif = oneNoteDirectFileResource;
        offset = oneNoteDirectFileResource.position();
        end = oneNoteDirectFileResource.size();
        documentBudget = new DocumentBudget(end);
        document.propertySetReader = new OneNotePtr(this);
    }

    public OneNotePtr(OneNotePtr oneNotePtr) {
        this.document = oneNotePtr.document;
        this.dif = oneNotePtr.dif;
        this.offset = oneNotePtr.offset;
        this.end = oneNotePtr.end;
        this.indentLevel = oneNotePtr.indentLevel;
        this.fileNodeListRecursion = oneNotePtr.fileNodeListRecursion;
        this.documentBudget = oneNotePtr.documentBudget;
    }

    public OneNoteHeader deserializeHeader() throws IOException, TikaException {
        OneNoteHeader data = new OneNoteHeader();
        data.guidFileType = deserializeGUID();
        data.guidFile = deserializeGUID();
        data.guidLegacyFileVersion = deserializeGUID();
        data.guidFileFormat = deserializeGUID();
        data.ffvLastCodeThatWroteToThisFile = deserializeLittleEndianInt();
        data.ffvOldestCodeThatHasWrittenToThisFile = deserializeLittleEndianInt();
        data.ffvNewestCodeThatHasWrittenToThisFile = deserializeLittleEndianInt();
        data.ffvOldestCodeThatMayReadThisFile = deserializeLittleEndianInt();
        data.fcrLegacyFreeChunkList = deserializeFileChunkReference64();
        data.fcrLegacyTransactionLog = deserializeFileChunkReference64();
        data.cTransactionsInLog = deserializeLittleEndianInt();
        data.cbLegacyExpectedFileLength = deserializeLittleEndianInt();
        data.rgbPlaceholder = deserializeLittleEndianLong();
        data.fcrLegacyFileNodeListRoot = deserializeFileChunkReference64();
        data.cbLegacyFreeSpaceInFreeChunkList = deserializeLittleEndianInt();
        data.ignoredZeroA = deserializeLittleEndianChar();
        data.ignoredZeroB = deserializeLittleEndianChar();
        data.ignoredZeroC = deserializeLittleEndianChar();
        data.ignoredZeroD = deserializeLittleEndianChar();
        data.guidAncestor = deserializeGUID();
        data.crcName = deserializeLittleEndianInt();
        data.fcrHashedChunkList = deserializeFileChunkReference64x32();
        data.fcrTransactionLog = deserializeFileChunkReference64x32();
        data.fcrFileNodeListRoot = deserializeFileChunkReference64x32();
        data.fcrFreeChunkList = deserializeFileChunkReference64x32();
        data.cbExpectedFileLength = deserializeLittleEndianLong();
        data.cbFreeSpaceInFreeChunkList = deserializeLittleEndianLong();
        data.guidFileVersion = deserializeGUID();
        data.nFileVersionGeneration = deserializeLittleEndianLong();
        data.guidDenyReadFileVersion = deserializeGUID();
        data.grfDebugLogFlags = deserializeLittleEndianInt();
        data.fcrDebugLogA = deserializeFileChunkReference64x32();
        data.fcrDebugLogB = deserializeFileChunkReference64x32();
        data.buildNumberCreated = deserializeLittleEndianInt();
        data.buildNumberLastWroteToFile = deserializeLittleEndianInt();
        data.buildNumberOldestWritten = deserializeLittleEndianInt();
        data.buildNumberNewestWritten = deserializeLittleEndianInt();
        if (data.getGuidFileFormat().toString().equals(PACKAGE_STORAGE_FILE_FORMAT_GUID)) {
            data.legacyOrAlternativePackaging = true;
            return data;
        }
        ByteBuffer reservedBytesAtEndOfHeader =
                ByteBuffer.allocate(NUM_RESERVED_BYTES_AT_END_OF_HEADER);
        deserializeBytes(reservedBytesAtEndOfHeader);
        return data;
    }

    private GUID deserializeGUID() throws IOException {
        byte[] guidBytes = new byte[16];
        for (int i = 0; i < 16; ++i) {
            int next = dif.read();
            if (next < 0) {
                // (byte) -1 would read as 0xFF and silently decode a wrong GUID
                throw new IOException("End of stream reached while reading a GUID");
            }
            guidBytes[i] = (byte) next;
        }
        GUID guid = GUID.fromMicrosoftBytes(guidBytes);
        offset = dif.position();
        return guid;
    }

    private FileChunkReference deserializeFileChunkReference64() throws IOException {
        long stp = deserializeLittleEndianInt();
        long cb = deserializeLittleEndianInt();
        offset = dif.position();
        return new FileChunkReference(stp, cb);
    }

    private FileChunkReference deserializeFileChunkReference64x32() throws IOException {
        long stp = deserializeLittleEndianLong();
        long cb = deserializeLittleEndianInt();
        offset = dif.position();
        return new FileChunkReference(stp, cb);
    }

    private char deserializeLittleEndianChar() throws IOException {
        if (dif.position() != offset) {
            dif.position(offset);
        }
        char res = (char) dif.read();
        ++offset;
        return res;
    }

    private long deserializeLittleEndianInt() throws IOException {
        if (dif.position() != offset) {
            dif.position(offset);
        }
        long res = dif.readLittleEndian(4);
        offset = dif.position();
        return res;
    }

    private long deserializeLittleEndianLong() throws IOException {
        if (dif.position() != offset) {
            dif.position(offset);
        }
        long res = dif.readLittleEndian(8);
        offset = dif.position();
        return res;
    }

    private long deserializeLittleEndianShort() throws IOException {
        if (dif.position() != offset) {
            dif.position(offset);
        }
        int c1 = dif.read();
        int c2 = dif.read();
        long res = (((c1 & 0xff)) + ((c2 & 0xff) << 8));
        offset = dif.position();
        return res;
    }

    private String getIndent() {
        if (!LOG.isDebugEnabled()) {
            // only debug messages use it
            return "";
        }
        StringBuilder retval = new StringBuilder();
        for (int i = 0; i < indentLevel; ++i) {
            retval.append("  ");
        }
        return retval.toString();
    }

    /**
     * Reads the committed FileNode count of each file node list from the transaction log
     * (MS-ONESTORE 2.3.3): the last value per FileNodeListID over the first
     * {@code cTransactionsInLog} transactions.
     */
    Map<Long, Long> deserializeTransactionLog(FileChunkReference first, long cTransactions)
            throws IOException, TikaException {
        Map<Long, Long> counts = new HashMap<>();
        deserializeTransactionLog(first, cTransactions, counts);
        return counts;
    }

    /** As above, into {@code counts}, which keeps the transactions committed before a failure. */
    void deserializeTransactionLog(FileChunkReference first, long cTransactions,
                                   Map<Long, Long> counts) throws IOException, TikaException {
        Map<Long, Long> pending = new HashMap<>();
        Set<Long> seenFragments = new HashSet<>();
        FileChunkReference fragment = first;
        long transactions = 0;
        // entries are 8 bytes; overlapping fragments would otherwise re-read them
        long entriesRemaining = dif.size() / 8;
        while (transactions < cTransactions && !FileChunkReference.nil().equals(fragment)) {
            if (!seenFragments.add(fragment.stp) || fragment.cb < 12 ||
                    fragment.stp + fragment.cb > dif.size()) {
                throw new TikaException("Invalid OneNote transaction log fragment at " +
                        fragment.stp);
            }
            reposition(fragment);
            long entries = (fragment.cb - 12) / 8;
            entriesRemaining -= entries;
            if (entriesRemaining < 0) {
                throw new TikaException("OneNote transaction log is longer than the file");
            }
            for (long i = 0; i < entries && transactions < cTransactions; i++) {
                long srcId = deserializeLittleEndianInt();
                long value = deserializeLittleEndianInt();
                if (srcId == 1) {
                    // sentinel: commits the transaction; value is a CRC
                    counts.putAll(pending);
                    pending.clear();
                    transactions++;
                } else {
                    pending.put(srcId, value);
                }
            }
            // nextFragment follows the last whole entry; a fragment can end in padding
            reposition(fragment.stp + 8 * entries);
            fragment = deserializeFileChunkReference64x32();
        }
    }

    public void reposition(FileChunkReference loc) throws IOException {
        reposition(loc.stp);
        this.end = offset + loc.cb;
    }

    private void reposition(long offset) throws IOException {
        this.offset = offset;
        dif.position(offset);
    }

    /** Parses a file node list's fragments, following each nextFragment until a nil reference. */
    public OneNotePtr internalDeserializeFileNodeList(OneNotePtr ptr, FileNodeList fileNodeList,
                                                      FileNodePtr curPath)
            throws IOException, TikaException {
        OneNotePtr localPtr = new OneNotePtr(ptr);
        FileNodePtrBackPush bp = new FileNodePtrBackPush(curPath);
        // a next-fragment reference pointing at an already-seen fragment would loop forever
        Set<Long> seenFragmentOffsets = new HashSet<>();
        seenFragmentOffsets.add(ptr.offset);
        try {
            while (true) {
                FileChunkReference next = FileChunkReference.nil();
                ptr.deserializeFileNodeListFragment(fileNodeList, next, curPath);
                if (FileChunkReference.nil().equals(next)) {
                    break;
                }
                // later fragments hold only uncommitted nodes, which may be garbage
                Long committed = document.committedFileNodeCounts
                        .get(fileNodeList.fileNodeListHeader.fileNodeListId);
                if (committed != null && fileNodeList.children.size() >= committed) {
                    break;
                }
                if (!seenFragmentOffsets.add(next.stp)) {
                    throw new TikaException(
                            "OneNote file node list fragment cycle detected at offset " +
                                    next.stp);
                }
                localPtr.reposition(next);
                ptr = localPtr;
            }
            return ptr;
        } finally {
            bp.dec();
        }
    }


    /**
     * Deserializes one file-node list and its fragments. The property-value budget is
     * document-wide; copied pointers share it.
     */
    public OneNotePtr deserializeFileNodeList(FileNodeList fileNodeList, FileNodePtr curPath)
            throws IOException, TikaException {
        if (fileNodeListRecursion.depth >= MAX_FILE_NODE_LIST_DEPTH) {
            throw new TikaMemoryLimitException(
                    "OneNote file node list nesting exceeds depth limit " +
                            MAX_FILE_NODE_LIST_DEPTH);
        }
        long listOffset = offset;
        if (!fileNodeListRecursion.parsedListOffsets.add(listOffset)) {
            throw new TikaException("OneNote file node list at offset " + listOffset +
                    " is referenced more than once (cycle or shared list)");
        }
        fileNodeListRecursion.depth++;
        try {
            return internalDeserializeFileNodeList(this, fileNodeList, curPath);
        } finally {
            fileNodeListRecursion.depth--;
        }
    }

    /** Deserializes a FileNodeListFragment; {@code next} receives its nextFragment reference. */
    void deserializeFileNodeListFragment(FileNodeList data, FileChunkReference next,
                                         FileNodePtr curPath) throws IOException, TikaException {
        data.fileNodeListHeader = deserializeFileNodeListHeader();
        Long committed = document.committedFileNodeCounts
                .get(data.fileNodeListHeader.fileNodeListId);
        boolean terminated = false;
        while (offset + 24 <= end) { // while there are at least 24 bytes free
            if (committed != null && data.children.size() >= committed) {
                terminated = true;
                break;
            }
            // 24 = sizeof(nextFragment) [12 bytes] + sizeof(footer) [8 bytes]
            // + 4 bytes for the FileNode header
            CheckedFileNodePushBack pushBack = new CheckedFileNodePushBack(data);
            try {
                long initialOffset = offset;
                FileNode fileNode =
                        deserializeFileNode(data.children.get(data.children.size() - 1), curPath);
                if (initialOffset == offset) {
                    //nothing read; avoid an infinite loop
                    break;
                }
                if (fileNode.id == FndStructureConstants.ChunkTerminatorFND || fileNode.id == 0) {
                    terminated = true;
                    break;
                }
                pushBack.commit();
                if (--documentBudget.fileNodesRemaining < 0) {
                    // lists that overlap or share fragments re-read the same bytes
                    throw new TikaMemoryLimitException(
                            "OneNote file nodes exceed the document's limit");
                }
                document.fileNodeCount++;
                Integer curPathOffset =
                        curPath.nodeListPositions.get(curPath.nodeListPositions.size() - 1);
                curPath.nodeListPositions.set(curPath.nodeListPositions.size() - 1,
                        curPathOffset + 1);
            } finally {
                pushBack.popBackIfNotCommitted();
            }
        }
        reposition(end - 20);
        FileChunkReference nextChunkRef = deserializeFileChunkReference64x32();
        next.cb = nextChunkRef.cb;
        next.stp = nextChunkRef.stp;
        if (terminated) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}Chunk terminator found NextChunkRef.cb={}, NextChunkRef.stp={}," +
                                " Offset={}, End={}", getIndent(), nextChunkRef.cb,
                        nextChunkRef.stp, offset, end);
            }
            // TODO check that next is OK
        }
        long footer = deserializeLittleEndianLong();
        if (footer != FOOTER_CONST) {
            throw new TikaException(
                    "Invalid footer constant. Expected " + FOOTER_CONST + " but was " + footer);
        }
    }

    private FileNode deserializeFileNode(FileNode data, FileNodePtr curPath)
            throws IOException, TikaException {
        OneNotePtr backup = new OneNotePtr(this);
        long reserved;

        data.isFileData = false;
        data.gosid = ExtendedGUID.nil();
        long fileNodeHeader = deserializeLittleEndianInt();
        data.id = fileNodeHeader & 0x3ff;
        if (data.id == 0) {
            return data;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}Start Node {} ({}) - Offset={}, End={}", getIndent(),
                    FndStructureConstants.nameOf(data.id), data.id, offset, end);
        }

        ++indentLevel;

        data.size = (fileNodeHeader >> 10) & 0x1fff;
        // reset the size to only be in scope of this FileNode
        end = backup.offset + data.size;

        long stpFormat = (fileNodeHeader >> 23) & 0x3;
        long cbFormat = (fileNodeHeader >> 25) & 0x3;
        data.baseType = (fileNodeHeader >> 27) & 0xf;
        reserved = (fileNodeHeader >> 31);
        data.ref = FileChunkReference.nil();
        if (data.baseType == 1 || data.baseType == 2) {
            data.ref = deserializeVarFileChunkReference(stpFormat, cbFormat);
        } // otherwise ignore the data ref, since we're a type 0
        if (data.baseType == 1 && !data.ref.equals(FileChunkReference.nil())) {
            OneNotePtr content = new OneNotePtr(this);
            content.reposition(data.ref);
            // would have thrown an error if invalid.
        }
        if (data.id == FndStructureConstants.ObjectDataEncryptionKeyV2FNDX) {
            throw new EncryptedDocumentException("OneNote section is password protected");
        }
        if (data.id == FndStructureConstants.ObjectGroupStartFND) {
            data.idDesc = "oid(group)";
            data.gosid = deserializeExtendedGUID();
        } else if (data.id == FndStructureConstants.ObjectGroupEndFND) {
            // no data
        } else if (data.id == FndStructureConstants.ObjectSpaceManifestRootFND ||
                data.id == FndStructureConstants.ObjectSpaceManifestListStartFND) {
            if (data.id == FndStructureConstants.ObjectSpaceManifestRootFND) {
                data.idDesc = "gosidRoot";
            } else {
                data.idDesc = "gosid";
            }
            // MUST match the gosid of the referencing ObjectSpaceManifestListReferenceFND
            data.gosid = deserializeExtendedGUID();
        } else if (data.id == FndStructureConstants.ObjectSpaceManifestListReferenceFND) {
            data.gosid = deserializeExtendedGUID();
            data.idDesc = "gosid";
            //children parsed in generic base_type 2 parser
        } else if (data.id == FndStructureConstants.RevisionManifestListStartFND) {
            data.gosid = deserializeExtendedGUID();
            data.idDesc = "gosid";
            FileNodePtr parentPath = new FileNodePtr(curPath);
            parentPath.nodeListPositions.remove(parentPath.nodeListPositions.size() - 1);
            document.registerRevisionManifestList(data.gosid, parentPath);

            data.subType.revisionManifestListStart = new RevisionManifestListStart();
            data.subType.revisionManifestListStart.nInstanceIgnored = deserializeLittleEndianInt();
        } else if (data.id == FndStructureConstants.RevisionManifestStart4FND) {
            data.gosid = deserializeExtendedGUID(); // the rid
            data.idDesc = "rid";
            data.subType.revisionManifest = new RevisionManifest();
            data.subType.revisionManifest.ridDependent = deserializeExtendedGUID(); // the rid
            LOG.debug("{}dependent gosid {}", getIndent(),
                    data.subType.revisionManifest.ridDependent);
            data.subType.revisionManifest.timeCreation = deserializeLittleEndianLong();
            data.subType.revisionManifest.revisionRole = deserializeLittleEndianInt();
            data.subType.revisionManifest.odcsDefault = deserializeLittleEndianShort();

            data.gctxid = ExtendedGUID.nil();
            document.registerRevisionManifest(data);
        } else if (data.id == FndStructureConstants.RevisionManifestStart6FND ||
                data.id == FndStructureConstants.RevisionManifestStart7FND) {
            data.gosid = deserializeExtendedGUID(); // the rid
            data.idDesc = "rid";
            data.subType.revisionManifest = new RevisionManifest();
            data.subType.revisionManifest.ridDependent = deserializeExtendedGUID(); // the rid
            LOG.debug("{}dependent gosid {}", getIndent(),
                    data.subType.revisionManifest.ridDependent);
            data.subType.revisionManifest.revisionRole = deserializeLittleEndianInt();
            data.subType.revisionManifest.odcsDefault = deserializeLittleEndianShort();

            data.gctxid = ExtendedGUID.nil();
            if (data.id == FndStructureConstants.RevisionManifestStart7FND) {
                data.gctxid = deserializeExtendedGUID(); // the rid
            }
            document.registerRevisionManifest(data);
        } else if (data.id == FndStructureConstants.GlobalIdTableStartFNDX) {
            data.subType.globalIdTableStartFNDX = new GlobalIdTableStartFNDX();
            data.subType.globalIdTableStartFNDX.reserved = deserializeLittleEndianChar();

        } else if (data.id == FndStructureConstants.GlobalIdTableEntryFNDX) {
            data.subType.globalIdTableEntryFNDX = new GlobalIdTableEntryFNDX();
            data.subType.globalIdTableEntryFNDX.index = deserializeLittleEndianInt();

            data.subType.globalIdTableEntryFNDX.guid = deserializeGUID();

            Revision currentRevision = getCurrentRevision();
            currentRevision.globalId.put(data.subType.globalIdTableEntryFNDX.index,
                    data.subType.globalIdTableEntryFNDX.guid);
        } else if (data.id == FndStructureConstants.GlobalIdTableEntry2FNDX) {
            data.subType.globalIdTableEntry2FNDX = new GlobalIdTableEntry2FNDX();
            data.subType.globalIdTableEntry2FNDX.indexMapFrom = deserializeLittleEndianInt();
            data.subType.globalIdTableEntry2FNDX.indexMapTo = deserializeLittleEndianInt();

            Revision currentRevision = getCurrentRevision();
            Revision dependentRevision = getRevision(currentRevision.dependent);
            GUID compactId = globalIds(dependentRevision).get(
                    data.subType.globalIdTableEntry2FNDX.indexMapFrom);
            if (compactId == null) {
                throw new TikaException("COMPACT_ID_MISSING");
            }
            currentRevision.globalId.put(data.subType.globalIdTableEntry2FNDX.indexMapTo,
                    compactId);
        } else if (data.id == FndStructureConstants.GlobalIdTableEntry3FNDX) {
            data.subType.globalIdTableEntry3FNDX = new GlobalIdTableEntry3FNDX();
            data.subType.globalIdTableEntry3FNDX.indexCopyFromStart = deserializeLittleEndianInt();

            data.subType.globalIdTableEntry3FNDX.entriesToCopy = deserializeLittleEndianInt();

            data.subType.globalIdTableEntry3FNDX.indexCopyToStart = deserializeLittleEndianInt();

            Revision currentRevision = getCurrentRevision();
            Revision dependentRevision = getRevision(currentRevision.dependent);
            int entriesToCopy = checkedCount(
                    data.subType.globalIdTableEntry3FNDX.entriesToCopy, "global ID entries");
            reserveGlobalIdCopies(entriesToCopy);
            for (int i = 0; i < entriesToCopy; ++i) {
                GUID compactId = globalIds(dependentRevision).get(
                        data.subType.globalIdTableEntry3FNDX.indexCopyFromStart + i);
                if (compactId == null) {
                    throw new TikaException("COMPACT_ID_MISSING");
                }
                currentRevision.globalId.put(
                        data.subType.globalIdTableEntry3FNDX.indexCopyToStart + i, compactId);
            }
        } else if (data.id == FndStructureConstants.CanRevise.ObjectRevisionWithRefCountFNDX ||
                data.id == FndStructureConstants.CanRevise.ObjectRevisionWithRefCount2FNDX) {
            data.subType.objectRevisionWithRefCountFNDX = new ObjectRevisionWithRefCountFNDX();
            data.subType.objectRevisionWithRefCountFNDX.oid = deserializeCompactID(); // the oid

            if (data.id == FndStructureConstants.CanRevise.ObjectRevisionWithRefCountFNDX) {
                int ref = deserializeLittleEndianChar();

                data.subType.objectRevisionWithRefCountFNDX.hasOidReferences = ref & 1;
                data.subType.objectRevisionWithRefCountFNDX.hasOsidReferences = ref & 2;
                data.subType.objectRevisionWithRefCountFNDX.cRef = (ref >> 2);
            } else {
                long ref = deserializeLittleEndianInt();

                data.subType.objectRevisionWithRefCountFNDX.hasOidReferences = ref & 1;
                data.subType.objectRevisionWithRefCountFNDX.hasOsidReferences = ref & 2;
                if ((ref >> 2) != 0) {
                    throw new TikaException("Reserved non-zero");
                }
                data.subType.objectRevisionWithRefCountFNDX.cRef = deserializeLittleEndianInt();
            }
            postprocessObjectRevision(data, curPath);
        } else if (data.id == FndStructureConstants.RootObjectReference2FNDX) {
            data.subType.rootObjectReference = new RootObjectReference();
            data.subType.rootObjectReference.oidRoot = deserializeCompactID();

            data.idDesc = "oidRoot";
            data.gosid = data.subType.rootObjectReference.oidRoot.guid;
            data.subType.rootObjectReference.rootObjectReferenceBase.rootRole =
                    deserializeLittleEndianInt();

            LOG.debug("{}Root role {}", getIndent(),
                    data.subType.rootObjectReference.rootObjectReferenceBase.rootRole);
        } else if (data.id == FndStructureConstants.RootObjectReference3FND) {
            data.idDesc = "oidRoot";
            data.gosid = deserializeExtendedGUID();

            data.subType.rootObjectReference = new RootObjectReference();
            data.subType.rootObjectReference.rootObjectReferenceBase.rootRole =
                    deserializeLittleEndianInt();

            LOG.debug("{}Root role {}", getIndent(),
                    data.subType.rootObjectReference.rootObjectReferenceBase.rootRole);
        } else if (data.id == FndStructureConstants.RevisionRoleDeclarationFND ||
                data.id == FndStructureConstants.RevisionRoleAndContextDeclarationFND) {
            data.gosid = deserializeExtendedGUID();

            data.subType.revisionRoleDeclaration = new RevisionRoleDeclaration();
            data.subType.revisionRoleDeclaration.revisionRole = deserializeLittleEndianInt();

            data.gctxid = data.id == FndStructureConstants.RevisionRoleAndContextDeclarationFND ?
                    deserializeExtendedGUID() : ExtendedGUID.nil();
            // FIXME: deal with ObjectDataEncryptionKey
        } else if (data.id == FndStructureConstants.ObjectInfoDependencyOverridesFND) {
            // reference count overrides: nothing for text or metadata
        } else if (data.id == FndStructureConstants.FileDataStoreListReferenceFND) {
            // already processed this
        } else if (data.id == FndStructureConstants.FileDataStoreObjectReferenceFND) {
            FileChunkReference ref = deserializeFileChunkReference64();
            GUID guid = deserializeGUID();
            ExtendedGUID extendedGuid = new ExtendedGUID(guid, 0);
            LOG.trace("found extended guid {}", extendedGuid);
            document.guidToRef.put(extendedGuid, ref);
            // an empty reference is file data OneNote never wrote
            if (data.ref.cb != 0) {
                OneNotePtr fileDataStorePtr = new OneNotePtr(this);
                fileDataStorePtr.reposition(data.ref);

                data.subType.fileDataStoreObjectReference = new FileDataStoreObjectReference();
                data.subType.fileDataStoreObjectReference.ref =
                        fileDataStorePtr.deserializeFileDataStoreObject();
            }

        } else if (data.id == FndStructureConstants.CanRevise.ObjectDeclarationWithRefCountFNDX ||
                data.id == FndStructureConstants.CanRevise.ObjectDeclarationWithRefCount2FNDX ||
                data.id == FndStructureConstants.CanRevise.ObjectDeclaration2RefCountFND ||
                data.id == FndStructureConstants.CanRevise.ObjectDeclaration2LargeRefCountFND ||
                data.id == FndStructureConstants.CanRevise.ReadOnlyObjectDeclaration2RefCountFND ||
                data.id ==
                        FndStructureConstants.CanRevise.ReadOnlyObjectDeclaration2LargeRefCountFND) {
            data.subType.objectDeclarationWithRefCount = new ObjectDeclarationWithRefCount();
            if (data.id == FndStructureConstants.CanRevise.ObjectDeclarationWithRefCountFNDX ||
                    data.id == FndStructureConstants.CanRevise.ObjectDeclarationWithRefCount2FNDX) {
                data.subType.objectDeclarationWithRefCount.body =
                        deserializeObjectDeclarationWithRefCountBody();
            } else { // one of the other 4 that use the ObjectDeclaration2Body
                data.subType.objectDeclarationWithRefCount.body =
                        deserializeObjectDeclaration2Body();
            }
            if (data.id == FndStructureConstants.CanRevise.ObjectDeclarationWithRefCountFNDX ||
                    data.id == FndStructureConstants.CanRevise.ObjectDeclaration2RefCountFND ||
                    data.id ==
                            FndStructureConstants.CanRevise.ReadOnlyObjectDeclaration2RefCountFND) {
                data.subType.objectDeclarationWithRefCount.cRef = deserializeLittleEndianChar();
            } else {
                data.subType.objectDeclarationWithRefCount.cRef = deserializeLittleEndianInt();
            }

            if (data.id == FndStructureConstants.CanRevise.ReadOnlyObjectDeclaration2RefCountFND ||
                    data.id ==
                            FndStructureConstants.CanRevise.ReadOnlyObjectDeclaration2LargeRefCountFND) {
                ByteBuffer md5Buffer = ByteBuffer.allocate(16);
                deserializeBytes(md5Buffer);
                data.subType.objectDeclarationWithRefCount.readOnly =
                        new ObjectDeclarationWithRefCount.ReadOnly();
                data.subType.objectDeclarationWithRefCount.readOnly.md5 = md5Buffer.array();
            }
            data.idDesc = "oid";
            postprocessObjectDeclarationContents(data, curPath);

            LOG.debug("{}Ref Count JCID {}", getIndent(),
                    data.subType.objectDeclarationWithRefCount.body.jcid);
        } else if (
                data.id == FndStructureConstants.CanRevise.ObjectDeclarationFileData3RefCountFND ||
                        data.id ==
                                FndStructureConstants.CanRevise.ObjectDeclarationFileData3LargeRefCountFND) {
            data.subType.objectDeclarationWithRefCount = new ObjectDeclarationWithRefCount();
            data.subType.objectDeclarationWithRefCount.body = new ObjectDeclarationWithRefCountBody();
            data.subType.objectDeclarationWithRefCount.body.oid = deserializeCompactID();

            long jcid = deserializeLittleEndianInt();

            data.subType.objectDeclarationWithRefCount.body.jcid.loadFrom32BitIndex(jcid);

            if (data.id == FndStructureConstants.CanRevise.ObjectDeclarationFileData3RefCountFND) {
                data.subType.objectDeclarationWithRefCount.cRef = deserializeLittleEndianChar();
            } else {
                data.subType.objectDeclarationWithRefCount.cRef = deserializeLittleEndianInt();
            }

            long cch = deserializeLittleEndianInt();

            long roomLeftLong = roomLeft();
            // cch counts UTF-16 characters
            if (2 * cch > roomLeftLong) {
                throw new TikaException(
                        "Data out of bounds - 2 * cch " + 2 * cch + " is > room left = " +
                                roomLeftLong);
            }

            if (cch > dif.size() || cch > Integer.MAX_VALUE / 2L) {
                throw new TikaMemoryLimitException(
                        "CCH=" + cch + " cannot be safely allocated for file size " +
                                dif.size());
            }
            ByteBuffer dataSpaceBuffer = ByteBuffer.allocate((int) cch * 2);
            dif.read(dataSpaceBuffer);
            byte[] dataSpaceBufferBytes = dataSpaceBuffer.array();
            offset += dataSpaceBufferBytes.length;
            if (dataSpaceBufferBytes.length == (IFNDF_GUID_LENGTH * 2 + IFNDF.length) &&
                    Arrays.equals(IFNDF,
                            Arrays.copyOfRange(dataSpaceBufferBytes, 0, IFNDF.length))) {
                data.subType.objectDeclarationWithRefCount.body.file_data_store_reference = true;
                GUID guid = GUID.fromCurlyBraceUTF16Bytes(
                        Arrays.copyOfRange(dataSpaceBufferBytes, IFNDF.length,
                                dataSpaceBufferBytes.length));
                ExtendedGUID extendedGUID = new ExtendedGUID(guid, 0);
                FileChunkReference fileChunk = document.getAssocGuidToRef(extendedGUID);
                if (fileChunk == null) {
                    LOG.debug("{} have not seen GUID {} yet", getIndent(), extendedGUID);
                } else {
                    // TODO - call postprocessObjectDeclarationContents on this object?
                }
            } else {
                LOG.debug("{}Ignoring an external reference {}", getIndent(),
                        new String(dataSpaceBufferBytes, StandardCharsets.UTF_16LE));
            }
        } else if (data.id == FndStructureConstants.ObjectGroupListReferenceFND) {
            data.idDesc = "object_group_id";
            data.gosid = deserializeExtendedGUID(); // the object group id

            // the ref populates the FileNodeList children
        } else if (data.id == FndStructureConstants.DataSignatureGroupDefinitionFND) {
            data.idDesc = "data_sig";
            data.gosid = deserializeExtendedGUID(); // the DataSignatureGroup

        } else if (data.id == FndStructureConstants.RevisionManifestListReferenceFND) {
            document.revisionMap.putIfAbsent(document.currentRevision, new Revision());
            Revision currentRevision = document.revisionMap.get(document.currentRevision);
            currentRevision.manifestList.add(curPath);
        } else {
            LOG.debug(
                    "No fnd needed to be parsed for data.id=0x" + Long.toHexString(data.id) + " (" +
                            FndStructureConstants.nameOf(data.id) + ")");
        }
        // an empty reference (fcrNil or fcrZero) is a child list OneNote never wrote
        if (data.baseType == 2 && data.ref.cb != 0) {
            OneNotePtr subList = new OneNotePtr(this);
            subList.reposition(data.ref);
            data.childFileNodeList = new FileNodeList();
            subList.deserializeFileNodeList(data.childFileNodeList, curPath);
        }

        offset = backup.offset + data.size;
        end = backup.end;

        if (reserved != 1) {
            throw new TikaException("RESERVED_NONZERO");
        }

        if (data.baseType == 1 && !(data.ref.equals(FileChunkReference.nil()))) {
            document.setAssocGuidToRef(data.gosid, data.ref);
            OneNotePtr content = new OneNotePtr(this);
            content.reposition(data.ref);
            if (data.hasGctxid()) {
                LOG.debug("{}gctxid {}", getIndent(), data.gctxid);
            }
        } else if (!data.gosid.equals(ExtendedGUID.nil())) {
            LOG.trace("Non base type == 1 guid {}", data.gosid);
        }
        --indentLevel;
        if (data.gosid.equals(ExtendedGUID.nil())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}End Node {} ({}) - Offset={}, End={}", getIndent(),
                        FndStructureConstants.nameOf(data.id), (int) data.id, offset, end);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}End Node {} ({}) {}:[{}] - Offset={}, End={}", getIndent(),
                        FndStructureConstants.nameOf(data.id), (int) data.id, data.idDesc,
                        data.gosid, offset, end);
            }
        }
        return data;
    }

    private void deserializeBytes(ByteBuffer byteBuffer) throws IOException {
        if (dif.position() != offset) {
            dif.position(offset);
        }
        dif.read(byteBuffer);
        offset = dif.position();
    }

    private ObjectDeclarationWithRefCountBody deserializeObjectDeclarationWithRefCountBody()
            throws IOException, TikaException {
        ObjectDeclarationWithRefCountBody data = new ObjectDeclarationWithRefCountBody();
        data.oid = deserializeCompactID();
        long jci_odcs_etc = deserializeLittleEndianInt();
        deserializeLittleEndianShort(); // reserved

        data.jcid.index = jci_odcs_etc & 0x3ffL;
        // undocumented: OneNote 2007 declares picture (0x16), ink (0x17) and zlib-compressed
        // (0x2A) data this way; it is raw data, not a property set
        if (data.jcid.index == 0x16 || data.jcid.index == 0x17 || data.jcid.index == 0x2A) {
            data.jcid.isFileData = true;
        }

        long must_be_zero = (jci_odcs_etc >> 10) & 0xf;
        data.fHasOidReferences = ((jci_odcs_etc >> 16) & 0x1) != 0;
        data.hasOsidReferences = ((jci_odcs_etc >> 17) & 0x1) != 0;
        // MS-ONESTORE 2.6.15: the reserved fields MUST be ignored; some writers set the
        // trailing 16 bits to 0xFFFF in otherwise well-formed declarations
        if (must_be_zero != 0) {
            throw new TikaException("ObjectDeclarationWithRefCountBody odcs must be zero");
        }
        return data;
    }

    private ObjectDeclarationWithRefCountBody deserializeObjectDeclaration2Body()
            throws IOException, TikaException {
        ObjectDeclarationWithRefCountBody data = new ObjectDeclarationWithRefCountBody();
        data.oid = deserializeCompactID();
        long jcid = deserializeLittleEndianInt();
        data.jcid.loadFrom32BitIndex(jcid);
        long hasRefs = deserializeLittleEndianChar();
        data.fHasOidReferences = (hasRefs & 0x1) != 0;
        data.hasOsidReferences = (hasRefs & 0x2) != 0;
        return data;
    }

    private FileDataStoreObject deserializeFileDataStoreObject() throws IOException, TikaException {
        FileDataStoreObject data = new FileDataStoreObject();
        GUID header = deserializeGUID();
        // TODO: validate the header; its expected value differs per OneNote version
        long len = deserializeLittleEndianLong();
        long unused = deserializeLittleEndianInt();
        long reserved = deserializeLittleEndianLong();
        if (len < 0 || len > end - offset - 16) {
            throw new TikaException("SEGV error");
        }
        if (unused > 0 || reserved > 0) {
            throw new TikaException("SEGV error");
        }
        data.fileData.stp = offset;
        data.fileData.cb = len;
        offset += len;
        while ((offset & 0x7) > 0) {
            // Padding is added to the end of the FileData stream to ensure that it
            // ends on an 8-byte boundary.
            ++offset;
        }
        GUID footer = deserializeGUID();
        // TODO: validate the footer; its expected value differs per OneNote version
        return data;
    }

    private CompactID deserializeCompactID() throws IOException, TikaException {
        CompactID compactID = new CompactID();
        compactID.n = deserializeLittleEndianChar();
        int b1 = deserializeLittleEndianChar();
        int b2 = deserializeLittleEndianChar();
        int b3 = deserializeLittleEndianChar();
        compactID.guidIndex = b1 | (b2 << 8) | (b3 << 16);
        GUID guid = globalIds(getCurrentRevision()).get(compactID.guidIndex);
        if (guid == null) {
            throw new TikaException("COMPACT ID MISSING");
        }
        compactID.guid = new ExtendedGUID(guid, compactID.n);
        return compactID;
    }

    private ExtendedGUID deserializeExtendedGUID() throws IOException {
        GUID guid = deserializeGUID();
        long n = deserializeLittleEndianInt();
        return new ExtendedGUID(guid, n);
    }

    /**
     * Reads a FileNodeChunkReference. stpFormat 0-3: 8, 4, 2 or 4 bytes; cbFormat 0-3: 4, 8, 1 or 2
     * bytes. Formats 2 and 3 are compressed: the value is multiplied by 8.
     */
    FileChunkReference deserializeVarFileChunkReference(long stpFormat, long cbFormat)
            throws IOException, TikaException {
        FileChunkReference data = new FileChunkReference(0, 0);
        long local8;
        long local16;
        long local32;
        switch (Long.valueOf(stpFormat).intValue()) {
            case 0: // 8 bytes, uncompressed
                data.stp = deserializeLittleEndianLong();
                break;
            case 1:
                local32 = deserializeLittleEndianInt();
                data.stp = local32;
                break;
            case 2:
                local16 = deserializeLittleEndianShort();
                data.stp = local16;
                data.stp <<= 3;
                break;
            case 3:
                local32 = deserializeLittleEndianInt();
                data.stp = local32;
                data.stp <<= 3;
                break;
            default:
                throw new TikaException("Unknown STP file node format " + stpFormat);
        }
        switch (Long.valueOf(cbFormat).intValue()) {
            case 0: // 4 bytes, uncompressed
                local32 = deserializeLittleEndianInt();
                data.cb = local32;
                break;
            case 1: // 8 bytes, uncompressed;
                data.cb = deserializeLittleEndianLong();
                break;
            case 2: // 1 byte, compressed
                local8 = deserializeLittleEndianChar();
                data.cb = local8;
                data.cb <<= 3;
                break;
            case 3: // 2 bytes, compressed
                local16 = deserializeLittleEndianShort();
                data.cb = local16;
                data.cb <<= 3;

                break;
            default:
                throw new TikaException("Unknown CB file node format " + cbFormat);
        }
        return data;
    }

    FileNodeListHeader deserializeFileNodeListHeader() throws TikaException, IOException {
        long positionOfThisHeader = offset;
        long uintMagic = deserializeLittleEndianLong();
        long fileNodeListId = deserializeLittleEndianInt();
        long nFragmentSequence = deserializeLittleEndianInt();

        return new FileNodeListHeader(positionOfThisHeader, uintMagic, fileNodeListId,
                nFragmentSequence);
    }

    private void postprocessObjectDeclarationContents(FileNode data, FileNodePtr curPtr)
            throws IOException, TikaException {
        data.gosid = data.subType.objectDeclarationWithRefCount.body.oid.guid;
        document.putObject(data.gosid, new FileNodePtr(curPtr));
        document.objectJcids.put(data.gosid, data.subType.objectDeclarationWithRefCount.body.jcid);
        if (data.subType.objectDeclarationWithRefCount.body.jcid.isObjectSpaceObjectPropSet()) {
            deferPropertySet(data);
        } else {
            if (!data.subType.objectDeclarationWithRefCount.body.jcid.isFileData) {
                throw new TikaException("JCID must be file data when !isObjectSpaceObjectPropSet.");
            }
            data.isFileData = true;
        }
    }

    /** A new revision of a declared object: the declaration's JCID with new properties. */
    private void postprocessObjectRevision(FileNode data, FileNodePtr curPtr)
            throws IOException, TikaException {
        data.gosid = data.subType.objectRevisionWithRefCountFNDX.oid.guid;
        data.idDesc = "oid";
        JCID jcid = document.objectJcids.get(data.gosid);
        if (jcid == null || !jcid.isObjectSpaceObjectPropSet() || data.ref.cb == 0) {
            return;
        }
        data.subType.objectDeclarationWithRefCount = new ObjectDeclarationWithRefCount();
        data.subType.objectDeclarationWithRefCount.body =
                new ObjectDeclarationWithRefCountBody().setJcid(jcid);
        deferPropertySet(data);
        document.putObject(data.gosid, new FileNodePtr(curPtr));
    }

    /**
     * Most declarations are never walked, so their property values are read on first use. The
     * object ID streams are read now: their compact IDs resolve against the global ID table in
     * effect at this point of the file.
     */
    private void deferPropertySet(FileNode data) throws IOException, TikaException {
        OneNotePtr ptr = new OneNotePtr(this);
        ptr.reposition(data.ref);
        data.propertySetStreams = ptr.deserializeObjectSpaceObjectPropSet();
        data.propertySetValues = ptr.offset;
    }

    PropertySet readPropertySet(FileNode node) throws IOException, TikaException {
        OneNotePtr ptr = new OneNotePtr(this);
        ptr.reposition(node.propertySetValues);
        return ptr.deserializePropertySet(new ObjectStreamCounters(), node.propertySetStreams);
    }

    /**
     * An empty global ID table falls back along the dependent chain: inferred from OneNote 2007
     * files, where it yields the current text, not taken from MS-ONESTORE.
     */
    private Map<Long, GUID> globalIds(Revision revision) {
        // memoized along the walked path, so each revision's chain is walked once in total
        List<Revision> path = new ArrayList<>();
        Revision r = revision;
        Map<Long, GUID> table = null;
        for (int i = 0; i <= document.revisionMap.size(); i++) {
            if (!r.globalId.isEmpty()) {
                table = r.globalId;
                break;
            }
            if (r.inheritedGlobalId != null) {
                table = r.inheritedGlobalId;
                break;
            }
            path.add(r);
            Revision dependent = document.revisionMap.get(r.dependent);
            if (dependent == null) {
                break;
            }
            r = dependent;
        }
        if (table == null) {
            table = r.globalId;
        }
        for (Revision p : path) {
            p.inheritedGlobalId = table;
        }
        return table;
    }

    private Revision getCurrentRevision() throws TikaException {
        Revision revision = document.revisionMap.get(document.currentRevision);
        if (revision == null) {
            throw new TikaException("Missing current revision");
        }
        return revision;
    }

    private Revision getRevision(ExtendedGUID revisionId) throws TikaException {
        Revision revision = document.revisionMap.get(revisionId);
        if (revision == null) {
            throw new TikaException("Missing dependent revision");
        }
        return revision;
    }

    private void reserveGlobalIdCopies(int count) throws TikaException {
        if (count > documentBudget.globalIdCopiesRemaining) {
            throw new TikaMemoryLimitException(
                    "OneNote global ID table copies exceed the document's limit");
        }
        documentBudget.globalIdCopiesRemaining -= count;
    }

    /** @return how many property values the document may hold, linear in file size */
    static long propertyValueLimit(long fileSize) {
        return Math.max(MAX_PROPERTY_VALUES, fileSize / MIN_BYTES_PER_PROPERTY_VALUE);
    }

    private int checkedCount(long count, String description)
            throws TikaException, IOException {
        if (count < 0 || count > dif.size()) {
            // impossible for this file: malformed, not over a limit
            throw new TikaException(description + " count " + count + " exceeds file size " +
                    dif.size());
        }
        if (count > MAX_PROPERTY_VALUES) {
            throw new TikaMemoryLimitException(
                    description + " count " + count + " exceeds limit " + MAX_PROPERTY_VALUES);
        }
        return (int) count;
    }

    private int reservePropertyCount(long count, String description)
            throws TikaException, IOException {
        int checked = checkedCount(count, description);
        if (count > documentBudget.propertyValuesRemaining) {
            throw new TikaMemoryLimitException(description + " count " + count +
                    " exceeds remaining property value budget " +
                    documentBudget.propertyValuesRemaining);
        }
        documentBudget.propertyValuesRemaining -= count;
        return checked;
    }

    private PropertySet deserializePropertySet(ObjectStreamCounters counters,
                                               ObjectSpaceObjectPropSet streams)
            throws IOException, TikaException {
        return deserializePropertySet(counters, streams, 0);
    }

    private PropertySet deserializePropertySet(ObjectStreamCounters counters,
                                               ObjectSpaceObjectPropSet streams,
                                               int depth)
            throws IOException, TikaException {
        if (depth >= MAX_PROPERTY_SET_DEPTH) {
            throw new TikaException("Property set nesting exceeds limit " +
                    MAX_PROPERTY_SET_DEPTH);
        }
        PropertySet data = new PropertySet();
        // Reserve the count before allocating any PropertyValue instances.
        int count = reservePropertyCount(deserializeLittleEndianShort(), "property");
        OneNotePropertyId[] ids = new OneNotePropertyId[count];
        for (int i = 0; i < count; ++i) {
            ids[i] = deserializePropertyID();
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}Property {}", getIndent(), ids[i]);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}{} elements in property set:", getIndent(), count);
        }
        data.rgPridsData = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            data.rgPridsData.add(
                    deserializePropertyValueFromPropertyID(ids[i], streams, counters, depth));
        }
        LOG.debug("");
        return data;

    }

    private PropertyValue deserializePropertyValueFromPropertyID(OneNotePropertyId propertyID,
                                                                 ObjectSpaceObjectPropSet streams,
                                                                 ObjectStreamCounters counters,
                                                                 int propertySetDepth)
            throws IOException, TikaException {
        if (propertySetDepth >= MAX_PROPERTY_SET_DEPTH) {
            throw new TikaException("Property value nesting exceeds limit " +
                    MAX_PROPERTY_SET_DEPTH);
        }
        PropertyValue data = new PropertyValue();
        data.propertyId = propertyID;
        char val8;
        long val16;
        long val32 = 0;
        long val64;
        if (LOG.isDebugEnabled()) {
            LOG.debug("\n{}<{}", getIndent(), propertyID);
        }

        ++indentLevel;
        try {
            long type = propertyID.type;
            switch ((int) type) {
                case 0x1:
                    LOG.debug(" [] ");
                    return data;
                case 0x2:
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" PropertyID bool({})", propertyID.inlineBool);
                    }
                    data.scalar = propertyID.inlineBool ? 1 : 0;
                    return data;
                case 0x3:
                    val8 = deserializeLittleEndianChar();
                    data.scalar = val8;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" PropertyID byte({})", data.scalar);
                    }
                    break;
                case 0x4:
                    val16 = deserializeLittleEndianShort();
                    data.scalar = val16;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" uint16 PropertyID short({})", data.scalar);
                    }
                    break;
                case 0x5:
                    val32 = deserializeLittleEndianInt();
                    data.scalar = val32;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" PropertyID int({})", data.scalar);
                    }
                    break;
                case 0x6:
                    val64 = deserializeLittleEndianLong();
                    data.scalar = val64;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" PropertyID long({})", data.scalar);
                    }
                    break;
                case 0x7:
                    val32 = deserializeLittleEndianInt();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" raw data: ({})[", val32);
                    }

                    if (offset + val32 > end) {
                        offset = end;
                        throw new TikaException("Offset is past end of file.");
                    }
                    data.rawData = new FileChunkReference(offset, val32);
                    offset += val32;
                    LOG.debug("]");
                    break;
                case 0x9:
                case 0xb:
                case 0xd:
                    val32 = deserializeLittleEndianInt();
                    // fallthrough
                case 0x8:
                case 0xa:
                case 0xc:
                    if (type == 0x8 || type == 0xa || type == 0xc) {
                        val32 = 1;
                    }
                    List<CompactID> stream = streams.contextIDs.data;
                    String xtype = "contextID";
                    long s_count = counters.context_ids_count;
                    if (type == 0x8 || type == 0x9) {
                        stream = streams.oids.data;
                        s_count = counters.oids_count;
                        xtype = "OIDs";
                    }
                    if (type == 0xa || type == 0xb) {
                        stream = streams.osids.data;
                        s_count = counters.osids_count;
                        xtype = "OSIDS";
                    }
                    if (s_count + val32 > stream.size()) {
                        throw new TikaException("SEGV");
                    }
                    int first = (int) s_count;
                    data.compactIDs = val32 == 1 ? List.of(stream.get(first)) :
                            List.copyOf(stream.subList(first, first + (int) val32));
                    s_count += val32;
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" {}{}", xtype, data.compactIDs);
                    }
                    // each reference property consumes the next entries of its stream
                    if (type == 0x8 || type == 0x9) {
                        counters.oids_count = s_count;
                    } else if (type == 0xa || type == 0xb) {
                        counters.osids_count = s_count;
                    } else {
                        counters.context_ids_count = s_count;
                    }
                    break;
                case 0x10:
                    val32 = deserializeLittleEndianInt();
                    OneNotePropertyId propId = deserializePropertyID();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(" UnifiedSubPropertySet {} {}", val32, propId);
                    }
                    int propertyCount = reservePropertyCount(val32, "unified property");
                    data.propertySet = new PropertySet();
                    data.propertySet.rgPridsData = new ArrayList<>(propertyCount);
                    for (int i = 0; i < propertyCount; ++i) {
                        data.propertySet.rgPridsData.add(
                                deserializePropertyValueFromPropertyID(propId, streams, counters,
                                        propertySetDepth + 1));
                    }
                    break;
                case 0x11:
                    LOG.debug(" SubPropertySet");
                    data.propertySet = deserializePropertySet(counters, streams,
                            propertySetDepth + 1);
                    break;
                default:
                    throw new TikaException("Invalid type: " + type);
            }
            LOG.debug(">");
            return data;
        } finally {
            --indentLevel;
        }
    }

    private OneNotePropertyId deserializePropertyID() throws TikaException, IOException {
        long pid = deserializeLittleEndianInt();
        return new OneNotePropertyId(pid);
    }

    private ObjectSpaceObjectPropSet deserializeObjectSpaceObjectPropSet()
            throws IOException, TikaException {
        ObjectSpaceObjectPropSet data = new ObjectSpaceObjectPropSet();
        data.osids.extendedStreamsPresent = 0;
        data.osids.osidsStreamNotPresent = 1;
        data.contextIDs.extendedStreamsPresent = 0;
        data.contextIDs.osidsStreamNotPresent = 0;
        data.oids = deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
        if (data.oids.osidsStreamNotPresent == 0) {
            data.osids = deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
        }
        if (data.oids.extendedStreamsPresent != 0) {
            data.contextIDs = deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
        }
        return data;
    }

    private ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs()
            throws IOException, TikaException {
        ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs data =
                new ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
        long header = deserializeLittleEndianInt();
        data.count = header & 0xffffff;
        data.osidsStreamNotPresent = ((header >> 31) & 0x1);
        data.extendedStreamsPresent = ((header >> 30) & 0x1);
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}Deserialized Stream Header count: {} OsidsNotPresent {} Extended {}",
                    getIndent(), data.count, data.osidsStreamNotPresent,
                    data.extendedStreamsPresent);
        }
        int count = checkedCount(data.count, "object stream");
        if (count > documentBudget.streamEntriesRemaining) {
            throw new TikaMemoryLimitException(
                    "OneNote object stream entries exceed the document's limit");
        }
        documentBudget.streamEntriesRemaining -= count;
        for (int i = 0; i < count; ++i) {
            CompactID cid;
            cid = deserializeCompactID();
            data.data.add(cid);
        }
        return data;
    }

    long roomLeft() {
        return end - offset;
    }


    public int size() {
        return (int) (end - offset);
    }
}
