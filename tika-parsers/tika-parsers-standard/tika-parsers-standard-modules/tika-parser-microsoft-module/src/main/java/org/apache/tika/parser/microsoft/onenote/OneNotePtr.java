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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.EndianUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main class used during parsing. This will contain an offset position and end
 * position for reading bytes from the byte stream.
 * <p>
 * It contains all the deserialize methods used to read the different data elements from a one
 * note file.
 * <p>
 * You can construct a new one note pointer and it will reposition the byte channel and will
 * read until
 */
class OneNotePtr {

    public static final long FOOTER_CONST = 0x8BC215C38233BA4BL;
    public static final String UNKNOWN = "unknown";
    public static final int IFNDF_GUID_LENGTH = 38; // 36 char guid with a { and a } char.
    public static final int NUM_RESERVED_BYTES_AT_END_OF_HEADER = 728;
    private static final Logger LOG = LoggerFactory.getLogger(OneNoteParser.class);
    private static final byte[] IFNDF =
            new byte[] {60, 0, 105, 0, 102, 0, 110, 0, 100, 0, 102, 0, 62, 0};
    private static final String PACKAGE_STORAGE_FILE_FORMAT_GUID = "{638DE92F-A6D4-4BC1-9A36-B3FC2511A5B7}";

    int indentLevel = 0;

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
    }

    public OneNotePtr(OneNotePtr oneNotePtr) {
        this.document = oneNotePtr.document;
        this.dif = oneNotePtr.dif;
        this.offset = oneNotePtr.offset;
        this.end = oneNotePtr.end;
        this.indentLevel = oneNotePtr.indentLevel;
    }

    public OneNoteHeader deserializeHeader() throws IOException, TikaException {
        OneNoteHeader data = new OneNoteHeader();
        data.setGuidFileType(deserializeGUID())
                .setGuidFile(deserializeGUID())
                .setGuidLegacyFileVersion(deserializeGUID())
                .setGuidFileFormat(deserializeGUID())
                .setFfvLastCodeThatWroteToThisFile(deserializeLittleEndianInt())
                .setFfvOldestCodeThatHasWrittenToThisFile(deserializeLittleEndianInt())
                .setFfvNewestCodeThatHasWrittenToThisFile(deserializeLittleEndianInt())
                .setFfvOldestCodeThatMayReadThisFile(deserializeLittleEndianInt())
                .setFcrLegacyFreeChunkList(deserializeFileChunkReference64())
                .setFcrLegacyTransactionLog(deserializeFileChunkReference64())
                .setcTransactionsInLog(deserializeLittleEndianInt())
                .setCbExpectedFileLength(deserializeLittleEndianInt())
                .setRgbPlaceholder(deserializeLittleEndianLong())
                .setFcrLegacyFileNodeListRoot(deserializeFileChunkReference64())
                .setCbLegacyFreeSpaceInFreeChunkList(deserializeLittleEndianInt())
                .setIgnoredZeroA(deserializeLittleEndianChar())
                .setIgnoredZeroB(deserializeLittleEndianChar())
                .setIgnoredZeroC(deserializeLittleEndianChar())
                .setIgnoredZeroD(deserializeLittleEndianChar()).setGuidAncestor(deserializeGUID())
                .setCrcName(deserializeLittleEndianInt())
                .setFcrHashedChunkList(deserializeFileChunkReference64x32())
                .setFcrTransactionLog(deserializeFileChunkReference64x32())
                .setFcrFileNodeListRoot(deserializeFileChunkReference64x32())
                .setFcrFreeChunkList(deserializeFileChunkReference64x32())
                .setCbExpectedFileLength(deserializeLittleEndianLong())
                .setCbFreeSpaceInFreeChunkList(deserializeLittleEndianLong())
                .setGuidFileVersion(deserializeGUID())
                .setnFileVersionGeneration(deserializeLittleEndianLong())
                .setGuidDenyReadFileVersion(deserializeGUID())
                .setGrfDebugLogFlags(deserializeLittleEndianInt())
                .setFcrDebugLogA(deserializeFileChunkReference64x32())
                .setFcrDebugLogB(deserializeFileChunkReference64x32())
                .setBuildNumberCreated(deserializeLittleEndianInt())
                .setBuildNumberLastWroteToFile(deserializeLittleEndianInt())
                .setBuildNumberOldestWritten(deserializeLittleEndianInt())
                .setBuildNumberNewestWritten(deserializeLittleEndianInt());
        if (data.getGuidFileFormat().toString().equals(PACKAGE_STORAGE_FILE_FORMAT_GUID)) {
            return data.setLegacyOrAlternativePackaging(true);
        }
        ByteBuffer reservedBytesAtEndOfHeader =
                ByteBuffer.allocate(NUM_RESERVED_BYTES_AT_END_OF_HEADER);
        deserializeBytes(reservedBytesAtEndOfHeader);
        return data;
    }

    private GUID deserializeGUID() throws IOException {
        int[] guid = new int[16];
        for (int i = 0; i < 16; ++i) {
            guid[i] = dif.read();
        }
        int[] guid2 = new int[16];
        // re-order [0,1,2,3] to little endian
        guid2[0] = guid[3];
        guid2[1] = guid[2];
        guid2[2] = guid[1];
        guid2[3] = guid[0];
        // re-order [4,5,6,7] to little endian
        guid2[4] = guid[5];
        guid2[5] = guid[4];
        guid2[6] = guid[7];
        guid2[7] = guid[6];
        // the rest is already in right order.
        guid2[8] = guid[8];
        guid2[9] = guid[9];
        guid2[10] = guid[10];
        guid2[11] = guid[11];
        guid2[12] = guid[12];
        guid2[13] = guid[13];
        guid2[14] = guid[14];
        guid2[15] = guid[15];

        offset = dif.position();
        return new GUID(guid2);
    }

    private byte[] deserializedReservedHeader() throws IOException {
        if (dif.position() != offset) {
            dif.position(offset);
        }
        ByteBuffer data = ByteBuffer.allocate(728);

        dif.read(data);

        offset = dif.position();
        return data.array();
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
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        dif.read(byteBuffer);
        long res = EndianUtils.readSwappedUnsignedInteger(byteBuffer.array(), 0);
        offset = dif.position();
        return res;
    }

    private long deserializeLittleEndianLong() throws IOException {
        if (dif.position() != offset) {
            dif.position(offset);
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        dif.read(byteBuffer);
        long res = EndianUtils.readSwappedLong(byteBuffer.array(), 0);
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
        StringBuilder retval = new StringBuilder();
        for (int i = 0; i < indentLevel; ++i) {
            retval.append("  ");
        }
        return retval.toString();
    }

    public void reposition(FileChunkReference loc) throws IOException {
        reposition(loc.stp);
        this.end = offset + loc.cb;
    }

    private void reposition(long offset) throws IOException {
        this.offset = offset;
        dif.position(offset);
    }

    /**
     * Keep parsing file node list fragments until a nil file chunk reference is encountered.
     * <p>
     * A file node list can be divided into one or more FileNodeListFragment
     * structures. Each fragment can specify whether there are more fragments in the list and
     * the location of the next fragment. Each fragment specifies a sub-sequence
     * of FileNode structures from the file node list.
     * <p>
     * When specifying the structure of a specific file node list in this document, the division
     * of the list into fragments is ignored and FileNode structures with FileNode.FileNodeID
     * field values equal to 0x0FF ("ChunkTerminatorFND") are not specified.
     *
     * @param ptr          The current OneNotePtr we are at currently.
     * @param fileNodeList The file node list to populate as we parse.
     * @param curPath      The current FileNodePtr.
     * @return The resulting one note pointer after node lists are all parsed.
     */
    public OneNotePtr internalDeserializeFileNodeList(OneNotePtr ptr, FileNodeList fileNodeList,
                                                      FileNodePtr curPath)
            throws IOException, TikaException {
        OneNotePtr localPtr = new OneNotePtr(document, dif);
        FileNodePtrBackPush bp = new FileNodePtrBackPush(curPath);
        try {
            while (true) {
                FileChunkReference next = FileChunkReference.nil();
                ptr.deserializeFileNodeListFragment(fileNodeList, next, curPath);
                if (FileChunkReference.nil().equals(next)) {
                    break;
                }
                localPtr.reposition(next);
                ptr = localPtr;
            }
            return ptr;
        } finally {
            bp.dec();
        }
    }


    public OneNotePtr deserializeFileNodeList(FileNodeList fileNodeList, FileNodePtr curPath)
            throws IOException, TikaException {
        return internalDeserializeFileNodeList(this, fileNodeList, curPath);
    }

    /**
     * Deserializes a FileNodeListFragment.
     * <p>
     * The FileNodeListFragment structure specifies a sequence of file nodes from a file node
     * list. The size of the FileNodeListFragment structure is specified by the structure that
     * references it.
     * <p>
     * All fragments in the same file node list MUST have the same FileNodeListFragment.header
     * .FileNodeListID field.
     *
     * @param data    List of file nodes that we collect while deserializing.
     * @param next    The next file chunk we are referencing.
     * @param curPath The current FileNodePtr.
     */
    void deserializeFileNodeListFragment(FileNodeList data, FileChunkReference next,
                                         FileNodePtr curPath) throws IOException, TikaException {
        data.fileNodeListHeader = deserializeFileNodeListHeader();
        boolean terminated = false;
        while (offset + 24 <= end) { // while there are at least 24 bytes free
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
                FileNode dereference = curPath.dereference(document);
                FileNode lastChild = data.children.get(data.children.size() - 1);
                assert dereference
                        .equals(lastChild); // is this correct? or should we be
                // checking the pointer?
                Integer curPathOffset =
                        curPath.nodeListPositions.get(curPath.nodeListPositions.size() - 1);
                curPath.nodeListPositions
                        .set(curPath.nodeListPositions.size() - 1, curPathOffset + 1);
            } finally {
                pushBack.popBackIfNotCommitted();
            }
        }
        reposition(end - 20);
        FileChunkReference nextChunkRef = deserializeFileChunkReference64x32();
        next.cb = nextChunkRef.cb;
        next.stp = nextChunkRef.stp;
        if (terminated) {
            LOG.debug(
                    "{}Chunk terminator found NextChunkRef.cb={}, NextChunkRef.stp={}," +
                            " Offset={}, End={}",
                    getIndent(), nextChunkRef.cb, nextChunkRef.stp, offset, end);
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
        LOG.debug("{}Start Node {} ({}) - Offset={}, End={}", getIndent(),
                FndStructureConstants.nameOf(data.id), data.id, offset, end);

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
            // Specifies the identity of the object space being specified by this object
            // space manifest list. MUST match the ObjectSpaceManifestListReferenceFND.gosid
            // field of the FileNode structure that referenced
            // this file node list.
            data.gosid = deserializeExtendedGUID();
            //LOG.debug("{}gosid {}", getIndent(), data.gosid.toString().c_str());
        } else if (data.id == FndStructureConstants.ObjectSpaceManifestListReferenceFND) {
            data.gosid = deserializeExtendedGUID();
            data.idDesc = "gosid";
            //LOG.debug("{}gosid {}", getIndent(),data.gosid.toString().c_str());
            //children parsed in generic base_type 2 parser
        } else if (data.id == FndStructureConstants.RevisionManifestListStartFND) {
            data.gosid = deserializeExtendedGUID();
            data.idDesc = "gosid";
            FileNodePtr parentPath = new FileNodePtr(curPath);
            parentPath.nodeListPositions.remove(parentPath.nodeListPositions.size() - 1);
            document.registerRevisionManifestList(data.gosid, parentPath);

            //LOG.debug("{}gosid {}", getIndent(),data.gosid.toString().c_str());
            data.subType.revisionManifestListStart.nInstanceIgnored = deserializeLittleEndianInt();
        } else if (data.id == FndStructureConstants.RevisionManifestStart4FND) {
            data.gosid = deserializeExtendedGUID(); // the rid
            data.idDesc = "rid";
            //LOG.debug("{}gosid {}", getIndent(), data.gosid.toString().c_str());
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
            //LOG.debug("{}gosid {}", getIndent(), data.gosid.toString().c_str());
            data.subType.revisionManifest.ridDependent = deserializeExtendedGUID(); // the rid
            LOG.debug("{}dependent gosid {}", getIndent(),
                    data.subType.revisionManifest.ridDependent);
            data.subType.revisionManifest.revisionRole = deserializeLittleEndianInt();
            data.subType.revisionManifest.odcsDefault = deserializeLittleEndianShort();

            data.gctxid = ExtendedGUID.nil();
            if (data.id == FndStructureConstants.RevisionManifestStart7FND) {
                data.gctxid = deserializeExtendedGUID(); // the rid
            }
            document.registerAdditionalRevisionRole(data.gosid,
                    data.subType.revisionManifest.revisionRole, data.gctxid);
            document.registerRevisionManifest(data);
        } else if (data.id == FndStructureConstants.GlobalIdTableStartFNDX) {
            data.subType.globalIdTableStartFNDX.reserved = deserializeLittleEndianChar();

        } else if (data.id == FndStructureConstants.GlobalIdTableEntryFNDX) {
            data.subType.globalIdTableEntryFNDX.index = deserializeLittleEndianInt();

            data.subType.globalIdTableEntryFNDX.guid = deserializeGUID();

            document.revisionMap.get(document.currentRevision).globalId
                    .put(data.subType.globalIdTableEntryFNDX.index,
                            data.subType.globalIdTableEntryFNDX.guid);
        } else if (data.id == FndStructureConstants.GlobalIdTableEntry2FNDX) {
            data.subType.globalIdTableEntry2FNDX.indexMapFrom = deserializeLittleEndianInt();
            data.subType.globalIdTableEntry2FNDX.indexMapTo = deserializeLittleEndianInt();

            ExtendedGUID dependentRevision =
                    document.revisionMap.get(document.currentRevision).dependent;
            // Get the compactId from the revisionMap's globalId map.
            GUID compactId = document.revisionMap.get(dependentRevision).globalId
                    .get(data.subType.globalIdTableEntry2FNDX.indexMapFrom);
            if (compactId == null) {
                throw new TikaException("COMPACT_ID_MISSING");
            }
            document.revisionMap.get(document.currentRevision).globalId
                    .put(data.subType.globalIdTableEntry2FNDX.indexMapTo, compactId);
        } else if (data.id == FndStructureConstants.GlobalIdTableEntry3FNDX) {
            data.subType.globalIdTableEntry3FNDX.indexCopyFromStart = deserializeLittleEndianInt();

            data.subType.globalIdTableEntry3FNDX.entriesToCopy = deserializeLittleEndianInt();

            data.subType.globalIdTableEntry3FNDX.indexCopyToStart = deserializeLittleEndianInt();

            ExtendedGUID dependent_revision =
                    document.revisionMap.get(document.currentRevision).dependent;
            for (int i = 0; i < data.subType.globalIdTableEntry3FNDX.entriesToCopy; ++i) {
                Map<Long, GUID> globalIdMap = document.revisionMap.get(dependent_revision).globalId;
                GUID compactId = globalIdMap
                        .get(data.subType.globalIdTableEntry3FNDX.indexCopyFromStart + i);
                if (compactId == null) {
                    throw new TikaException("COMPACT_ID_MISSING");
                }
                document.revisionMap.get(document.currentRevision).globalId
                        .put(data.subType.globalIdTableEntry3FNDX.indexCopyToStart + i, compactId);
            }
        } else if (data.id == FndStructureConstants.CanRevise.ObjectRevisionWithRefCountFNDX ||
                data.id == FndStructureConstants.CanRevise.ObjectRevisionWithRefCount2FNDX) {
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
        } else if (data.id == FndStructureConstants.RootObjectReference2FNDX) {
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

            data.subType.rootObjectReference.rootObjectReferenceBase.rootRole =
                    deserializeLittleEndianInt();

            LOG.debug("{}Root role {}", getIndent(),
                    data.subType.rootObjectReference.rootObjectReferenceBase.rootRole);
        } else if (data.id == FndStructureConstants.RevisionRoleDeclarationFND ||
                data.id == FndStructureConstants.RevisionRoleAndContextDeclarationFND) {
            data.gosid = deserializeExtendedGUID();

            data.subType.revisionRoleDeclaration.revisionRole = deserializeLittleEndianInt();

            if (data.id == FndStructureConstants.RevisionRoleAndContextDeclarationFND) {
                data.gctxid = deserializeExtendedGUID();

            }
            document.registerAdditionalRevisionRole(data.gosid,
                    data.subType.revisionRoleDeclaration.revisionRole, data.gctxid);
            // FIXME: deal with ObjectDataEncryptionKey
        } else if (data.id == FndStructureConstants.ObjectInfoDependencyOverridesFND) {
            OneNotePtr content = new OneNotePtr(this);
            if (!data.ref.equals(FileChunkReference.nil())) {
                content.reposition(data.ref); // otherwise it's positioned right at this node
            }
            data.subType.objectInfoDependencyOverrides.data =
                    content.deserializeObjectInfoDependencyOverrideData();
        } else if (data.id == FndStructureConstants.FileDataStoreListReferenceFND) {
            // already processed this
        } else if (data.id == FndStructureConstants.FileDataStoreObjectReferenceFND) {
            FileChunkReference ref = deserializeFileChunkReference64();
            GUID guid = deserializeGUID();
            ExtendedGUID extendedGuid = new ExtendedGUID(guid, 0);
            LOG.trace("found extended guid {}", extendedGuid);
            document.guidToRef.put(extendedGuid, ref);
            OneNotePtr fileDataStorePtr = new OneNotePtr(this);
            fileDataStorePtr.reposition(data.ref);

            data.subType.fileDataStoreObjectReference.ref =
                    fileDataStorePtr.deserializeFileDataStoreObject();

        } else if (data.id == FndStructureConstants.CanRevise.ObjectDeclarationWithRefCountFNDX ||
                data.id == FndStructureConstants.CanRevise.ObjectDeclarationWithRefCount2FNDX ||
                data.id == FndStructureConstants.CanRevise.ObjectDeclaration2RefCountFND ||
                data.id == FndStructureConstants.CanRevise.ObjectDeclaration2LargeRefCountFND ||
                data.id == FndStructureConstants.CanRevise.ReadOnlyObjectDeclaration2RefCountFND ||
                data.id ==
                        FndStructureConstants.CanRevise
                                .ReadOnlyObjectDeclaration2LargeRefCountFND) {
            data.subType.objectDeclarationWithRefCount.body.file_data_store_reference = false;
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
                            FndStructureConstants.CanRevise
                                    .ReadOnlyObjectDeclaration2LargeRefCountFND) {
                ByteBuffer md5Buffer = ByteBuffer.allocate(16);
                deserializeBytes(md5Buffer);
                data.subType.objectDeclarationWithRefCount.readOnly.md5 = md5Buffer.array();
            }
            data.idDesc = "oid";
            postprocessObjectDeclarationContents(data, curPath);

            LOG.debug("{}Ref Count JCID {}", getIndent(),
                    data.subType.objectDeclarationWithRefCount.body.jcid);
        } else if (
                data.id == FndStructureConstants.CanRevise.ObjectDeclarationFileData3RefCountFND ||
                        data.id ==
                                FndStructureConstants.CanRevise
                                        .ObjectDeclarationFileData3LargeRefCountFND) {
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
            if (cch > roomLeftLong) { // not a valid guid
                throw new TikaException(
                        "Data out of bounds - cch " + cch + " is > room left = " + roomLeftLong);
            }

            if (cch > dif.size()) {
                throw new TikaMemoryLimitException(
                        "CCH=" + cch + " was found that was greater" + " than file size " +
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
        } else if (data.id == FndStructureConstants.ObjectGroupStartFND) {
            data.idDesc = "object_group_id";
            data.gosid = deserializeExtendedGUID(); // the oid

        } else if (data.id == FndStructureConstants.ObjectGroupEndFND) {
            // nothing to see here
        } else if (data.id == FndStructureConstants.DataSignatureGroupDefinitionFND) {
            data.idDesc = "data_sig";
            data.gosid = deserializeExtendedGUID(); // the DataSignatureGroup

        } else if (data.id == FndStructureConstants.RevisionManifestListReferenceFND) {
            document.revisionMap.putIfAbsent(document.currentRevision, new Revision());
            Revision currentRevision = document.revisionMap.get(document.currentRevision);
            currentRevision.manifestList.add(curPath);
        } else {
            LOG.debug(
                    "No fnd needed to be parsed for data.id=0x" +
                            Long.toHexString(data.id) + " (" +
                            FndStructureConstants.nameOf(data.id) + ")");
        }
        if (data.baseType == 2) {
            // Generic baseType == 2 parser - means we have children to parse.
            OneNotePtr subList = new OneNotePtr(this);
            // position the subList pointer to the data.ref and deserialize recursively.
            subList.reposition(data.ref);
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
            LOG.debug("{}End Node {} ({}) - Offset={}, End={}", getIndent(),
                    FndStructureConstants.nameOf(data.id), (int) data.id, offset, end);
        } else {
            LOG.debug("{}End Node {} ({}) {}:[{}] - Offset={}, End={}", getIndent(),
                    FndStructureConstants.nameOf(data.id), (int) data.id, data.idDesc, data.gosid,
                    offset, end);
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
        long reserved = deserializeLittleEndianShort();

        data.jcid.index = jci_odcs_etc & 0x3ffL;

        long must_be_zero = (jci_odcs_etc >> 10) & 0xf;
        long must_be_zeroA = ((jci_odcs_etc >> 14) & 0x3);
        data.fHasOidReferences = ((jci_odcs_etc >> 16) & 0x1) != 0;
        data.hasOsidReferences = ((jci_odcs_etc >> 17) & 0x1) != 0;
        if (jci_odcs_etc >> 18L > 0) {
            throw new TikaException("RESERVED_NONZERO");
        }
        if (reserved != 0 || must_be_zeroA != 0 || must_be_zero != 0) {
            throw new TikaException("RESERVED_NONZERO");
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

    /**
     * The FileDataStoreObject structure specifies the data for a file data object.
     *
     * @return
     * @throws IOException
     */
    private FileDataStoreObject deserializeFileDataStoreObject()
            throws IOException, TikaException {
        FileDataStoreObject data = new FileDataStoreObject();
        GUID header = deserializeGUID();
        // TODO - the expected header is different per version of one note.
//    if (!header.equals(FILE_DATA_STORE_OBJ_HEADER)) {
//      throw new TikaException("Unexpected file data store object header: " + header);
//    }
        long len = deserializeLittleEndianLong();
        long unused = deserializeLittleEndianInt();
        long reserved = deserializeLittleEndianLong();
        if (offset + len + 16 > end) {
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
        // TODO - the expected footer is per version of one note.
//    if (!footer.equals(FILE_DATA_STORE_OBJ_FOOTER)) {
//      throw new TikaException("Unexpected file data store object footer: " + footer);
//    }
        return data;
    }

    private ObjectInfoDependencyOverrideData deserializeObjectInfoDependencyOverrideData()
            throws IOException {
        ObjectInfoDependencyOverrideData objectInfoDependencyOverrideData =
                new ObjectInfoDependencyOverrideData();
        long num_8bit_overrides = deserializeLittleEndianInt();
        long num_32bit_overrides = deserializeLittleEndianInt();
        long crc = deserializeLittleEndianInt();
        for (int i = 0; i < num_8bit_overrides; ++i) {
            int local = deserializeLittleEndianChar();
            objectInfoDependencyOverrideData.overrides1.add(local);
        }
        for (int i = 0; i < num_32bit_overrides; ++i) {
            long local = deserializeLittleEndianInt();
            objectInfoDependencyOverrideData.overrides2.add(local);
        }
        return objectInfoDependencyOverrideData;
    }

    private CompactID deserializeCompactID() throws IOException, TikaException {
        CompactID compactID = new CompactID();
        compactID.n = deserializeLittleEndianChar();
        compactID.guidIndex = deserializeInt24();
        compactID.guid = ExtendedGUID.nil();
        compactID.guid.n = compactID.n;
        long index = compactID.guidIndex;
        Map<Long, GUID> globalIdMap = document.revisionMap.get(document.currentRevision).globalId;
        GUID guid = globalIdMap.get(index);
        if (guid != null) {
            compactID.guid.guid = guid;
        } else {
            throw new TikaException("COMPACT ID MISSING");
        }
        return compactID;
    }

    private long deserializeInt24() throws IOException {
        int b1 = deserializeLittleEndianChar();
        int b2 = deserializeLittleEndianChar();
        int b3 = deserializeLittleEndianChar();

        return new Int24(b1, b2, b3).value();
    }

    private ExtendedGUID deserializeExtendedGUID() throws IOException {
        GUID guid = deserializeGUID();
        long n = deserializeLittleEndianInt();
        return new ExtendedGUID(guid, n);
    }

    /**
     * Depending on stpFormat and cbFormat, will deserialize a FileChunkReference.
     *
     * @param stpFormat An unsigned integer that specifies the size and format of the
     *                  FileNodeChunkReference.stp field specified by the fnd field if this
     *                  FileNode structure has a
     *                  value of the BaseType field equal to 1 or 2. MUST be ignored if the
     *                  value of the BaseType field
     *                  of this FileNode structure is equal to 0. The meaning of the StpFormat
     *                  field is given by the
     *                  following table.
     *                  Value Meaning
     *                  0 8 bytes, uncompressed.
     *                  1 4 bytes, uncompressed.
     *                  2 2 bytes, compressed.
     *                  3 4 bytes, compressed.
     *                  The value of an uncompressed file pointer specifies a location in the
     *                  file. To uncompress a
     *                  compressed file pointer, multiply the value by 8.
     * @param cbFormat  An unsigned integer that specifies the size and format of the
     *                  FileNodeChunkReference.cb field specified by the fnd field if this
     *                  FileNode structure has a
     *                  BaseType field value equal to 1 or 2. MUST be 0 and MUST be ignored if
     *                  BaseType of this
     *                  FileNode structure is equal to 0. The meaning of CbFormat is given by
     *                  the following table.
     *                  Value Meaning
     *                  0 4 bytes, uncompressed.
     *                  1 8 bytes, uncompressed.
     *                  2 1 byte, compressed.
     *                  3 2 bytes, compressed.
     *                  The value of an uncompressed byte count specifies the size, in bytes, of
     *                  the data referenced by a
     *                  FileNodeChunkReference structure. To uncompress a compressed byte count,
     *                  multiply the value by 8.
     * @return
     * @throws IOException
     */
    FileChunkReference deserializeVarFileChunkReference(long stpFormat, long cbFormat)
            throws IOException, TikaException {
        FileChunkReference data = new FileChunkReference(0, 0);
        long local8;
        long local16;
        long local32;
        switch (new Long(stpFormat).intValue()) {
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
        switch (new Long(cbFormat).intValue()) {
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

    FileNodeListHeader deserializeFileNodeListHeader() throws IOException {
        long positionOfThisHeader = offset;
        long uintMagic = deserializeLittleEndianLong();
        long fileNodeListId = deserializeLittleEndianInt();
        long nFragmentSequence = deserializeLittleEndianInt();

        return new FileNodeListHeader(positionOfThisHeader, uintMagic, fileNodeListId,
                nFragmentSequence);
    }

    /**
     * For an object declaration file node, after parsing all the fnd variables, now we will process
     * the object declaration's contents.
     *
     * @param data   The FileNode containing all the fnd variable's data.
     * @param curPtr The current pointer.
     * @throws IOException
     */
    private void postprocessObjectDeclarationContents(FileNode data, FileNodePtr curPtr)
            throws IOException, TikaException {
        data.gosid = data.subType.objectDeclarationWithRefCount.body.oid.guid;
        document.guidToObject.put(data.gosid, new FileNodePtr(curPtr));
        if (data.subType.objectDeclarationWithRefCount.body.jcid.isObjectSpaceObjectPropSet()) {
            OneNotePtr objectSpacePropSetPtr = new OneNotePtr(this);
            objectSpacePropSetPtr.reposition(data.ref);
            data.subType.objectDeclarationWithRefCount.objectRef =
                    objectSpacePropSetPtr.deserializeObjectSpaceObjectPropSet();
            ObjectStreamCounters streamCounters = new ObjectStreamCounters();
            data.propertySet = objectSpacePropSetPtr.deserializePropertySet(streamCounters,
                    data.subType.objectDeclarationWithRefCount.objectRef);
        } else {
            if (!data.subType.objectDeclarationWithRefCount.body.jcid.isFileData) {
                throw new TikaException("JCID must be file data when !isObjectSpaceObjectPropSet.");
            }
            // this is FileData
            data.isFileData = true;
            if (LOG.isDebugEnabled()) {
                OneNotePtr content = new OneNotePtr(this);
                content.reposition(data.ref);
                LOG.debug("{}Raw:", getIndent());
                content.dumpHex();
                LOG.debug("");
            }
        }
    }

    private PropertySet deserializePropertySet(ObjectStreamCounters counters,
                                               ObjectSpaceObjectPropSet streams)
            throws IOException, TikaException {
        PropertySet data = new PropertySet();
        long count = deserializeLittleEndianShort();
        data.rgPridsData =
                Stream.generate(PropertyValue::new).limit((int) count).collect(Collectors.toList());
        for (int i = 0; i < count; ++i) {
            data.rgPridsData.get(i).propertyId = deserializePropertyID();
            LOG.debug("{}Property {}", getIndent(), data.rgPridsData.get(i).propertyId);
        }
        LOG.debug("{}{} elements in property set:", getIndent(), count);
        for (int i = 0; i < count; ++i) {
            data.rgPridsData.set(i,
                    deserializePropertyValueFromPropertyID(data.rgPridsData.get(i).propertyId,
                            streams, counters));
        }
        LOG.debug("");
        return data;

    }

    private PropertyValue deserializePropertyValueFromPropertyID(OneNotePropertyId propertyID,
                                                                 ObjectSpaceObjectPropSet streams,
                                                                 ObjectStreamCounters counters)
            throws IOException, TikaException {
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
                    LOG.debug(" PropertyID bool({})", propertyID.inlineBool);
                    data.scalar = propertyID.inlineBool ? 1 : 0;
                    return data;
                case 0x3:
                    val8 = deserializeLittleEndianChar();
                    data.scalar = val8;
                    LOG.debug(" PropertyID byte({})", data.scalar);
                    break;
                case 0x4:
                    val16 = deserializeLittleEndianShort();
                    data.scalar = val16;
                    LOG.debug(" uint16 PropertyID short({})", data.scalar);
                    break;
                case 0x5:
                    val32 = deserializeLittleEndianInt();
                    data.scalar = val32;
                    LOG.debug(" PropertyID int({})", data.scalar);
                    break;
                case 0x6:
                    val64 = deserializeLittleEndianLong();
                    data.scalar = val64;
                    LOG.debug(" PropertyID long({})", data.scalar);
                    break;
                case 0x7:
                    // If the value of the PropertyID.type element is "0x7" and the property
                    // specifies an array of elements, the value of
                    // the
                    // prtFourBytesOfLengthFollowedByData.cb element MUST be the sum of the
                    // sizes, in bytes, of each element in the array.
                    // Exceptions include:
                    // * The RgOutlineIndentDistance element, where the value of the
                    // prtFourBytesOfLengthFollowedByData.cb element
                    // MUST be: 4 + (4 × RgOutlineIndentDistance.count).
                    // * The TableColumnsLocked element, where the value of the
                    // prtFourBytesOfLengthFollowedByData.cb
                    // element MUST be: 1 + (TableColumnsLocked.cColumns + 7) / 8.
                    // * The TableColumnWidths element, where the value of the
                    // prtFourBytesOfLengthFollowedByData.cb
                    // element MUST be: 1 + (4 × TableColumnWidths.cColumns).

                    val32 = deserializeLittleEndianInt();
                    LOG.debug(" raw data: ({})[", val32);
                {
                    data.rawData.stp = offset;
                    data.rawData.cb = 0;
                    if (offset + val32 > end) {
                        data.rawData.cb = end - offset;
                        offset = end;
                        throw new TikaException("Offset is past end of file.");
                    }
                    data.rawData.cb = val32;
                    offset += val32;
                    if (LOG.isDebugEnabled()) {
                        OneNotePtr content = new OneNotePtr(this);
                        content.reposition(data.rawData);
                        content.dumpHex();
                    }
                }
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
                    for (int i = 0; i < val32; ++i, ++s_count) {
                        int index = (int) s_count;
                        if (index < stream.size()) {
                            data.compactIDs.add(stream.get(index));
                            LOG.debug(" {}[{}]", xtype,
                                    data.compactIDs.get(data.compactIDs.size() - 1));
                        } else {
                            throw new TikaException("SEGV");
                        }
                    }
                    break;
                case 0x10:
                    val32 = deserializeLittleEndianInt();
                    OneNotePropertyId propId = deserializePropertyID();
                    LOG.debug(" UnifiedSubPropertySet {} {}", val32, propId);
                    data.propertySet.rgPridsData =
                            Stream.generate(PropertyValue::new).limit((int) val32)
                                    .collect(Collectors.toList());
                    for (int i = 0; i < val32; ++i) {
                        try {
                            data.propertySet.rgPridsData.set(i,
                                    deserializePropertyValueFromPropertyID(propId, streams,
                                            counters));
                        } catch (IOException e) {
                            return data;
                        }
                    }
                    break;
                case 0x11:
                    LOG.debug(" SubPropertySet");
                    data.propertySet = deserializePropertySet(counters, streams);
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

    private OneNotePropertyId deserializePropertyID() throws IOException {
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
        //uint64_t cur_offset = offset;
        //LOG.debug("starting deserialization %lx(%lx) / %lx", offset, offset - cur_offset, end);
        data.oids = deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
        //LOG.debug("mid deserialization %lx(%lx) / %lx", offset, offset - cur_offset, end);
        if (data.oids.osidsStreamNotPresent == 0) {
            data.osids = deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
        }
        //LOG.debug("lat deserialization %lx(%lx) / %lx", offset, offset - cur_offset, end);
        if (data.oids.extendedStreamsPresent != 0) {
            data.contextIDs = deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
        }
        return data;
    }

    private ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs
    deserializeObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs()
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
        for (int i = 0; i < data.count; ++i) {
            CompactID cid;
            cid = deserializeCompactID();
            data.data.add(cid);
        }
        return data;
    }

    long roomLeft() {
        return end - offset;
    }

    public void dumpHex() throws TikaMemoryLimitException, IOException {
        if (end - offset > dif.size()) {
            throw new TikaMemoryLimitException(
                    "Exceeded memory limit when trying to dumpHex - " + "" + (end - offset) +
                            " > " + dif.size());
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate((int) (end - offset));
        LOG.debug(Hex.encodeHexString(byteBuffer.array()));
    }

    public int size() {
        return (int) (end - offset);
    }
}
