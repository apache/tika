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

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Basic unit of data in a file node list: header fields plus a data field (fnd) that may be
 * empty, hold data, reference another block of the file, or both.
 */
class FileNode {
    private static final Logger LOG = LoggerFactory.getLogger(FileNode.class);

    long id;
    long size;

    /**
     * 0: no reference; 1: fnd starts with a FileNodeChunkReference to data; 2: fnd starts with
     * a FileNodeChunkReference to a file node list.
     */
    long baseType;

    /**
     * The gosid for ObjectSpaceManifestRoot/Start/List, RevisionManifestListStart and the
     * ObjectGroup nodes; the rid for RevisionManifestStart4FND; DataSignatureGroup for
     * RevisionManifestEndFND.
     */
    ExtendedGUID gosid;

    // only present for RevisionManifestStart7FND and RevisionRoleAndContextDeclarationFND
    ExtendedGUID gctxid;
    GUID fileDataStoreReference;
    FileChunkReference ref;
    PropertySet propertySet;
    boolean isFileData;

    /** For ObjectGroupListReference, the children. */
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

    public long getSize() {
        return size;
    }

    public FileNode setSize(long size) {
        this.size = size;
        return this;
    }

    public FileNode setGosid(ExtendedGUID gosid) {
        this.gosid = gosid;
        return this;
    }

    @Override
    public String toString() {
        return new StringBuilder().append("FileNodeID=0x").append(Long.toHexString(id))
                .append(", gosid=").append(gosid).append(", baseType=0x")
                .append(Long.toHexString(baseType)).toString();
    }
}
