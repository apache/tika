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

/**
 * Some types of FileNodes have an "fnd" variable.
 * FND stands for "File Node Data"
 * <p>
 * These are the different types of FND variables there are.
 * <p>
 * The value of each constant corresponds to the FileNodeID property for the file node.
 */
final class FndStructureConstants {
    static final long ObjectSpaceManifestRootFND = 0x04;
    static final long ObjectSpaceManifestListReferenceFND = 0x08;
    static final long ObjectSpaceManifestListStartFND = 0x0c;
    static final long RevisionManifestListReferenceFND = 0x10;
    static final long RevisionManifestListStartFND = 0x14;
    static final long RevisionManifestStart4FND = 0x1b;
    static final long RevisionManifestEndFND = 0x1c;
    static final long RevisionManifestStart6FND = 0x1e;
    static final long RevisionManifestStart7FND = 0x1f;
    static final long GlobalIdTableStartFNDX = 0x21;
    static final long GlobalIdTableStart2FND = 0x22;
    static final long GlobalIdTableEntryFNDX = 0x24;
    static final long GlobalIdTableEntry2FNDX = 0x25;
    static final long GlobalIdTableEntry3FNDX = 0x26;
    static final long GlobalIdTableEndFNDX = 0x28;
    static final long RootObjectReference2FNDX = 0x59;
    static final long RootObjectReference3FND = 0x5a; // each root object must have a differe
    static final long RevisionRoleDeclarationFND = 0x5c;
    static final long RevisionRoleAndContextDeclarationFND = 0x5d;
    static final long ObjectDataEncryptionKeyV2FNDX = 0x7c;
    static final long ObjectInfoDependencyOverridesFND = 0x84;
    static final long DataSignatureGroupDefinitionFND = 0x8c;
    static final long FileDataStoreListReferenceFND = 0x90;
    static final long FileDataStoreObjectReferenceFND = 0x94;
    static final long ObjectGroupListReferenceFND = 0xb0;
    static final long ObjectGroupStartFND = 0xb4;
    static final long ObjectGroupEndFND = 0xb8;
    static final long HashedChunkDescriptor2FND = 0xc2;
    static final long ChunkTerminatorFND = 0xff;

    private FndStructureConstants() {
        // no op
    }

    static String nameOf(long type) {
        int typeAsInt = (int) type;
        switch (typeAsInt) {
            case (int) ObjectSpaceManifestRootFND:
                return "ObjectSpaceManifestRootFND";
            case (int) ObjectSpaceManifestListReferenceFND:
                return "ObjectSpaceManifestListReferenceFND";
            case (int) ObjectSpaceManifestListStartFND:
                return "ObjectSpaceManifestListStartFND";
            case (int) RevisionManifestListReferenceFND:
                return "RevisionManifestListReferenceFND";
            case (int) RevisionManifestListStartFND:
                return "RevisionManifestListStartFND";
            case (int) RevisionManifestStart4FND:
                return "RevisionManifestStart4FND";
            case (int) RevisionManifestEndFND:
                return "RevisionManifestEndFND";
            case (int) RevisionManifestStart6FND:
                return "RevisionManifestStart6FND";
            case (int) RevisionManifestStart7FND:
                return "RevisionManifestStart7FND";
            case (int) GlobalIdTableStartFNDX:
                return "GlobalIdTableStartFNDX";
            case (int) GlobalIdTableStart2FND:
                return "GlobalIdTableStart2FND";
            case (int) GlobalIdTableEntryFNDX:
                return "GlobalIdTableEntryFNDX";
            case (int) GlobalIdTableEntry2FNDX:
                return "GlobalIdTableEntry2FNDX";
            case (int) GlobalIdTableEntry3FNDX:
                return "GlobalIdTableEntry3FNDX";
            case (int) GlobalIdTableEndFNDX:
                return "GlobalIdTableEndFNDX";
            case (int) CanRevise.ObjectDeclarationWithRefCountFNDX:
                return "ObjectDeclarationWithRefCountFNDX";
            case (int) CanRevise.ObjectDeclarationWithRefCount2FNDX:
                return "ObjectDeclarationWithRefCount2FNDX";
            case (int) CanRevise.ObjectRevisionWithRefCountFNDX:
                return "ObjectRevisionWithRefCountFNDX";
            case (int) CanRevise.ObjectRevisionWithRefCount2FNDX:
                return "ObjectRevisionWithRefCount2FNDX";
            case (int) CanRevise.ObjectDeclaration2RefCountFND:
                return "ObjectDeclaration2RefCountFND";
            case (int) CanRevise.ObjectDeclaration2LargeRefCountFND:
                return "ObjectDeclaration2LargeRefCountFND";
            case (int) CanRevise.ReadOnlyObjectDeclaration2RefCountFND:
                return "ReadOnlyObjectDeclaration2RefCountFND";
            case (int) CanRevise.ReadOnlyObjectDeclaration2LargeRefCountFND:
                return "ReadOnlyObjectDeclaration2LargeRefCountFND";
            case (int) CanRevise.ObjectDeclarationFileData3RefCountFND:
                return "ObjectDeclarationFileData3RefCountFND";
            case (int) CanRevise.ObjectDeclarationFileData3LargeRefCountFND:
                return "ObjectDeclarationFileData3LargeRefCountFND";
            case (int) RootObjectReference2FNDX:
                return "RootObjectReference2FNDX";
            case (int) RootObjectReference3FND:
                return "RootObjectReference3FND";
            case (int) RevisionRoleDeclarationFND:
                return "RevisionRoleDeclarationFND";
            case (int) RevisionRoleAndContextDeclarationFND:
                return "RevisionRoleAndContextDeclarationFND";
            case (int) ObjectDataEncryptionKeyV2FNDX:
                return "ObjectDataEncryptionKeyV2FNDX";
            case (int) ObjectInfoDependencyOverridesFND:
                return "ObjectInfoDependencyOverridesFND";
            case (int) DataSignatureGroupDefinitionFND:
                return "DataSignatureGroupDefinitionFND";
            case (int) FileDataStoreListReferenceFND:
                return "FileDataStoreListReferenceFND";
            case (int) FileDataStoreObjectReferenceFND:
                return "FileDataStoreObjectReferenceFND";
            case (int) ObjectGroupListReferenceFND:
                return "ObjectGroupListReferenceFND";
            case (int) ObjectGroupStartFND:
                return "ObjectGroupStartFND";
            case (int) ObjectGroupEndFND:
                return "ObjectGroupEndFND";
            case (int) HashedChunkDescriptor2FND:
                return "HashedChunkDescriptor2FND";

            case (int) ChunkTerminatorFND:
                return "ChunkTerminatorFND";
            default:
                return "UnknownFND";
        }
    }

    public static final class CanRevise {
        static final long ObjectDeclarationWithRefCountFNDX = 0x2d;
        static final long ObjectDeclarationWithRefCount2FNDX = 0x2e;
        static final long ObjectRevisionWithRefCountFNDX = 0x041;
        static final long ObjectRevisionWithRefCount2FNDX = 0x42;
        static final long ObjectDeclaration2RefCountFND = 0x0A4;
        static final long ObjectDeclaration2LargeRefCountFND = 0xA5;
        static final long ReadOnlyObjectDeclaration2RefCountFND = 0xc4;
        static final long ReadOnlyObjectDeclaration2LargeRefCountFND = 0xc5;
        static final long ObjectDeclarationFileData3RefCountFND = 0x72;
        static final long ObjectDeclarationFileData3LargeRefCountFND = 0x73;

        private CanRevise() {
            // no op
        }
    }
}
