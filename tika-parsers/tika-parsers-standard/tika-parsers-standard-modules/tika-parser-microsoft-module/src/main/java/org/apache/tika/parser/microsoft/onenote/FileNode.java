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
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaMemoryLimitException;

/**
 * A FileNode structure is the basic unit for holding and referencing data in the file.
 * FileNode structures are organized into file node lists
 * <p>
 * A FileNode structure is divided into header fields and a data field, fnd. The header fields
 * specify what type of FileNode structure it
 * is,
 * and what format the fnd field is in.
 * <p>
 * The fnd field can be empty, or it can contain data directly, or it can contain a reference to
 * another block of the file by
 * byte position and byte count, or it can contain both data and a reference.
 */
class FileNode {
    private static final Logger LOG = LoggerFactory.getLogger(FileNode.class);

    /**
     * An unsigned integer that specifies the type of this FileNode structure. The meaning of
     * this value is specified by the fnd field.
     */
    long id;
    long size;

    /**
     * An unsigned integer that specifies whether the structure specified by fnd contains a
     * FileNodeChunkReference structure.
     * 0 - This FileNode structure does not reference other data. The data structure specified
     * by fnd MUST NOT contain a
     * FileNodeChunkReference structure. The StpFormat and CbFormat fields MUST be ignored.
     * 1 - This FileNode structure contains a reference to data. The first field in the data
     * structure specified by an fnd field MUST be a
     * FileNodeChunkReference structure that specifies the location and size of the referenced
     * data.
     * The type of the FileNodeChunkReference structure is specified by the StpFormat and
     * CbFormat fields.
     * 2 - This FileNode structure contains a reference to a file node list.
     * The first field in the data structure specified by the fnd field MUST be a
     * FileNodeChunkReference structure that specifies the
     * location and size of a file node list. The type of the FileNodeChunkReference is
     * specified by the StpFormat and CbFormat fields.
     */
    long baseType;

    /**
     * The ExtendedGUID for this FileNode.
     * Specified for ObjectSpaceManifestRoot
     * ObjectSpaceManifestStart
     * ObjectSpaceManifestList
     * RevisionManifestListStart
     * ObjectGroupStartFND
     * ObjectGroupID
     * ObjectGroupListReferenceFND
     * <p>
     * RID for RevisionManifestStart4FND
     * DataSignatureGroup for RevisionManifestEndFND
     */
    ExtendedGUID gosid;

    // only present for RevisionManfiest7FND and RevisionRoleAndContextDeclaration
    ExtendedGUID gctxid;
    GUID fileDataStoreReference;
    FileChunkReference ref;
    PropertySet propertySet;
    boolean isFileData;

    /**
     * For ObjectGroupListReference, the children.
     */
    FileNodeList childFileNodeList = new FileNodeList();

    FileNodeUnion subType = new FileNodeUnion();

    String idDesc;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileNode fileNode = (FileNode) o;
        return id == fileNode.id && size == fileNode.size && baseType == fileNode.baseType &&
                isFileData == fileNode.isFileData && Objects.equals(gosid, fileNode.gosid) &&
                Objects.equals(gctxid, fileNode.gctxid) &&
                Objects.equals(fileDataStoreReference, fileNode.fileDataStoreReference) &&
                Objects.equals(ref, fileNode.ref) &&
                Objects.equals(propertySet, fileNode.propertySet) &&
                Objects.equals(childFileNodeList, fileNode.childFileNodeList) &&
                Objects.equals(subType, fileNode.subType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, size, baseType, gosid, gctxid, fileDataStoreReference, ref,
                propertySet, isFileData, childFileNodeList, subType);
    }

    public boolean hasGctxid() {
        return id == FndStructureConstants.RevisionRoleAndContextDeclarationFND ||
                id == FndStructureConstants.RevisionManifestStart7FND;
    }

    public long getId() {
        return id;
    }

    public FileNode setId(long id) {
        this.id = id;
        return this;
    }

    public long getSize() {
        return size;
    }

    public FileNode setSize(long size) {
        this.size = size;
        return this;
    }

    public long getBaseType() {
        return baseType;
    }

    public FileNode setBaseType(long baseType) {
        this.baseType = baseType;
        return this;
    }

    public ExtendedGUID getGosid() {
        return gosid;
    }

    public FileNode setGosid(ExtendedGUID gosid) {
        this.gosid = gosid;
        return this;
    }

    public ExtendedGUID getGctxid() {
        return gctxid;
    }

    public FileNode setGctxid(ExtendedGUID gctxid) {
        this.gctxid = gctxid;
        return this;
    }

    public GUID getFileDataStoreReference() {
        return fileDataStoreReference;
    }

    public FileNode setFileDataStoreReference(GUID fileDataStoreReference) {
        this.fileDataStoreReference = fileDataStoreReference;
        return this;
    }

    public FileChunkReference getRef() {
        return ref;
    }

    public FileNode setRef(FileChunkReference ref) {
        this.ref = ref;
        return this;
    }

    public PropertySet getPropertySet() {
        return propertySet;
    }

    public FileNode setPropertySet(PropertySet propertySet) {
        this.propertySet = propertySet;
        return this;
    }

    public boolean isFileData() {
        return isFileData;
    }

    public FileNode setFileData(boolean fileData) {
        isFileData = fileData;
        return this;
    }

    public FileNodeList getChildFileNodeList() {
        return childFileNodeList;
    }

    public FileNode setChildFileNodeList(FileNodeList childFileNodeList) {
        this.childFileNodeList = childFileNodeList;
        return this;
    }

    public FileNodeUnion getSubType() {
        return subType;
    }

    public FileNode setSubType(FileNodeUnion subType) {
        this.subType = subType;
        return this;
    }

    public void print(OneNoteDocument document, OneNotePtr pointer, int indentLevel)
            throws IOException, TikaMemoryLimitException {
        boolean shouldPrintHeader = FndStructureConstants.nameOf(id).contains("ObjectDec");
        if (gosid.equals(ExtendedGUID.nil()) && shouldPrintHeader) {
            LOG.debug("{}[beg {}]:{}", IndentUtil.getIndent(indentLevel + 1),
                    FndStructureConstants.nameOf(id), gosid);
        }
        propertySet.print(document, pointer, indentLevel + 1);
        if (!childFileNodeList.children.isEmpty()) {
            if (shouldPrintHeader) {
                LOG.debug("{}children", IndentUtil.getIndent(indentLevel + 1));
            }
            for (FileNode child : childFileNodeList.children) {
                child.print(document, pointer, indentLevel + 1);
            }
        }
        if (id == FndStructureConstants.RevisionRoleDeclarationFND ||
                id == FndStructureConstants.RevisionRoleAndContextDeclarationFND) {
            LOG.debug("{}[Revision Role {}]", IndentUtil.getIndent(indentLevel + 1),
                    subType.revisionRoleDeclaration.revisionRole);

        }
        if (id == FndStructureConstants.RevisionManifestStart4FND ||
                id == FndStructureConstants.RevisionManifestStart6FND ||
                id == FndStructureConstants.RevisionManifestStart7FND) {
            LOG.debug("{}[revisionRole {}]", IndentUtil.getIndent(indentLevel + 1),
                    subType.revisionManifest.revisionRole);

        }
        if ((!gctxid.equals(ExtendedGUID.nil()) ||
                id == FndStructureConstants.RevisionManifestStart7FND) && shouldPrintHeader) {
            LOG.debug("{}[gctxid {}]", IndentUtil.getIndent(indentLevel + 1), gctxid);
        }
        if (!gosid.equals(ExtendedGUID.nil()) && shouldPrintHeader) {
            LOG.debug("{}[end {}]:{}", IndentUtil.getIndent(indentLevel + 1),
                    FndStructureConstants.nameOf(id), gosid);

        }
    }

    /**
     * A description of what this GUID id means in this context.
     *
     * @return A description of what this GUID id means in this context.
     */
    public String getIdDesc() {
        return idDesc;
    }

    @Override
    public String toString() {
        return new StringBuilder().append("FileNodeID=0x").append(Long.toHexString(id))
                .append(", gosid=").append(gosid).append(", baseType=0x")
                .append(Long.toHexString(baseType)).toString();
    }
}
