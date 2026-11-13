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

import java.io.Serializable;

class OneNoteHeader implements Serializable {

    GUID guidFileType;
    GUID guidFile;
    GUID guidLegacyFileVersion;
    GUID guidFileFormat;
    long ffvLastCodeThatWroteToThisFile;
    long ffvOldestCodeThatHasWrittenToThisFile;
    long ffvNewestCodeThatHasWrittenToThisFile;
    long ffvOldestCodeThatMayReadThisFile;

    FileChunkReference fcrLegacyFreeChunkList;
    FileChunkReference fcrLegacyTransactionLog;
    long cTransactionsInLog;
    long cbLegacyExpectedFileLength;
    long rgbPlaceholder;
    FileChunkReference fcrLegacyFileNodeListRoot;
    long cbLegacyFreeSpaceInFreeChunkList;
    long ignoredZeroA;
    long ignoredZeroB;
    long ignoredZeroC;
    long ignoredZeroD;
    GUID guidAncestor;
    long crcName;
    FileChunkReference fcrHashedChunkList;
    FileChunkReference fcrTransactionLog;
    FileChunkReference fcrFileNodeListRoot;
    FileChunkReference fcrFreeChunkList;
    long cbExpectedFileLength;
    long cbFreeSpaceInFreeChunkList;
    GUID guidFileVersion;
    long nFileVersionGeneration;
    GUID guidDenyReadFileVersion;
    long grfDebugLogFlags;
    FileChunkReference fcrDebugLogA;
    FileChunkReference fcrDebugLogB;
    long buildNumberCreated;
    long buildNumberLastWroteToFile;
    long buildNumberOldestWritten;
    long buildNumberNewestWritten;
    boolean legacyOrAlternativePackaging;

    /** True for MS-ONE/MS-ONESTORE files, false for legacy formats. */
    public boolean isMsOneStoreFormat() {
        return GUID.nil().equals(guidLegacyFileVersion);
    }

    public GUID getGuidFileType() {
        return guidFileType;
    }

    public GUID getGuidFile() {
        return guidFile;
    }

    public GUID getGuidFileFormat() {
        return guidFileFormat;
    }

    public long getcTransactionsInLog() {
        return cTransactionsInLog;
    }

    public long getnFileVersionGeneration() {
        return nFileVersionGeneration;
    }

    public boolean isLegacyOrAlternativePackaging() {
        return legacyOrAlternativePackaging;
    }
}
