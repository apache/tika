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

class FileNodeUnion {
    RevisionManifestListStart revisionManifestListStart = new RevisionManifestListStart();
    RevisionManifest revisionManifest = new RevisionManifest();
    RevisionRoleDeclaration revisionRoleDeclaration = new RevisionRoleDeclaration();
    GlobalIdTableStartFNDX globalIdTableStartFNDX = new GlobalIdTableStartFNDX();
    GlobalIdTableEntryFNDX globalIdTableEntryFNDX = new GlobalIdTableEntryFNDX();
    GlobalIdTableEntry2FNDX globalIdTableEntry2FNDX = new GlobalIdTableEntry2FNDX();
    GlobalIdTableEntry3FNDX globalIdTableEntry3FNDX = new GlobalIdTableEntry3FNDX();
    ObjectRevisionWithRefCountFNDX objectRevisionWithRefCountFNDX =
            new ObjectRevisionWithRefCountFNDX();
    ObjectInfoDependencyOverrides objectInfoDependencyOverrides =
            new ObjectInfoDependencyOverrides();
    ObjectDeclarationWithRefCount objectDeclarationWithRefCount =
            new ObjectDeclarationWithRefCount();
    RootObjectReference rootObjectReference = new RootObjectReference();
    FileDataStoreObjectReference fileDataStoreObjectReference = new FileDataStoreObjectReference();

    public RevisionManifestListStart getRevisionManifestListStart() {
        return revisionManifestListStart;
    }

    public FileNodeUnion setRevisionManifestListStart(
            RevisionManifestListStart revisionManifestListStart) {
        this.revisionManifestListStart = revisionManifestListStart;
        return this;
    }

    public RevisionManifest getRevisionManifest() {
        return revisionManifest;
    }

    public FileNodeUnion setRevisionManifest(RevisionManifest revisionManifest) {
        this.revisionManifest = revisionManifest;
        return this;
    }

    public RevisionRoleDeclaration getRevisionRoleDeclaration() {
        return revisionRoleDeclaration;
    }

    public FileNodeUnion setRevisionRoleDeclaration(
            RevisionRoleDeclaration revisionRoleDeclaration) {
        this.revisionRoleDeclaration = revisionRoleDeclaration;
        return this;
    }

    public GlobalIdTableStartFNDX getGlobalIdTableStartFNDX() {
        return globalIdTableStartFNDX;
    }

    public FileNodeUnion setGlobalIdTableStartFNDX(GlobalIdTableStartFNDX globalIdTableStartFNDX) {
        this.globalIdTableStartFNDX = globalIdTableStartFNDX;
        return this;
    }

    public GlobalIdTableEntryFNDX getGlobalIdTableEntryFNDX() {
        return globalIdTableEntryFNDX;
    }

    public FileNodeUnion setGlobalIdTableEntryFNDX(GlobalIdTableEntryFNDX globalIdTableEntryFNDX) {
        this.globalIdTableEntryFNDX = globalIdTableEntryFNDX;
        return this;
    }

    public GlobalIdTableEntry2FNDX getGlobalIdTableEntry2FNDX() {
        return globalIdTableEntry2FNDX;
    }

    public FileNodeUnion setGlobalIdTableEntry2FNDX(
            GlobalIdTableEntry2FNDX globalIdTableEntry2FNDX) {
        this.globalIdTableEntry2FNDX = globalIdTableEntry2FNDX;
        return this;
    }

    public GlobalIdTableEntry3FNDX getGlobalIdTableEntry3FNDX() {
        return globalIdTableEntry3FNDX;
    }

    public FileNodeUnion setGlobalIdTableEntry3FNDX(
            GlobalIdTableEntry3FNDX globalIdTableEntry3FNDX) {
        this.globalIdTableEntry3FNDX = globalIdTableEntry3FNDX;
        return this;
    }

    public ObjectRevisionWithRefCountFNDX getObjectRevisionWithRefCountFNDX() {
        return objectRevisionWithRefCountFNDX;
    }

    public FileNodeUnion setObjectRevisionWithRefCountFNDX(
            ObjectRevisionWithRefCountFNDX objectRevisionWithRefCountFNDX) {
        this.objectRevisionWithRefCountFNDX = objectRevisionWithRefCountFNDX;
        return this;
    }

    public ObjectInfoDependencyOverrides getObjectInfoDependencyOverrides() {
        return objectInfoDependencyOverrides;
    }

    public FileNodeUnion setObjectInfoDependencyOverrides(
            ObjectInfoDependencyOverrides objectInfoDependencyOverrides) {
        this.objectInfoDependencyOverrides = objectInfoDependencyOverrides;
        return this;
    }

    public ObjectDeclarationWithRefCount getObjectDeclarationWithRefCount() {
        return objectDeclarationWithRefCount;
    }

    public FileNodeUnion setObjectDeclarationWithRefCount(
            ObjectDeclarationWithRefCount objectDeclarationWithRefCount) {
        this.objectDeclarationWithRefCount = objectDeclarationWithRefCount;
        return this;
    }

    public RootObjectReference getRootObjectReference() {
        return rootObjectReference;
    }

    public FileNodeUnion setRootObjectReference(RootObjectReference rootObjectReference) {
        this.rootObjectReference = rootObjectReference;
        return this;
    }

    public FileDataStoreObjectReference getFileDataStoreObjectReference() {
        return fileDataStoreObjectReference;
    }

    public FileNodeUnion setFileDataStoreObjectReference(
            FileDataStoreObjectReference fileDataStoreObjectReference) {
        this.fileDataStoreObjectReference = fileDataStoreObjectReference;
        return this;
    }
}
