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

    /**
     * Determine if this OneNote file pre-dates the open specs published by
     * microsoft.
     *
     * @return True if file is based on the MS-ONE and MS-ONESTORE specs. False otherwise.
     */
    public boolean isLegacy() {
        return !GUID.nil().equals(guidLegacyFileVersion);
    }

    public GUID getGuidFileType() {
        return guidFileType;
    }

    public OneNoteHeader setGuidFileType(GUID guidFileType) {
        this.guidFileType = guidFileType;
        return this;
    }

    public GUID getGuidFile() {
        return guidFile;
    }

    public OneNoteHeader setGuidFile(GUID guidFile) {
        this.guidFile = guidFile;
        return this;
    }

    public GUID getGuidLegacyFileVersion() {
        return guidLegacyFileVersion;
    }

    public OneNoteHeader setGuidLegacyFileVersion(GUID guidLegacyFileVersion) {
        this.guidLegacyFileVersion = guidLegacyFileVersion;
        return this;
    }

    public GUID getGuidFileFormat() {
        return guidFileFormat;
    }

    public OneNoteHeader setGuidFileFormat(GUID guidFileFormat) {
        this.guidFileFormat = guidFileFormat;
        return this;
    }

    public long getFfvLastCodeThatWroteToThisFile() {
        return ffvLastCodeThatWroteToThisFile;
    }

    public OneNoteHeader setFfvLastCodeThatWroteToThisFile(long ffvLastCodeThatWroteToThisFile) {
        this.ffvLastCodeThatWroteToThisFile = ffvLastCodeThatWroteToThisFile;
        return this;
    }

    public long getFfvOldestCodeThatHasWrittenToThisFile() {
        return ffvOldestCodeThatHasWrittenToThisFile;
    }

    public OneNoteHeader setFfvOldestCodeThatHasWrittenToThisFile(
            long ffvOldestCodeThatHasWrittenToThisFile) {
        this.ffvOldestCodeThatHasWrittenToThisFile = ffvOldestCodeThatHasWrittenToThisFile;
        return this;
    }

    public long getFfvNewestCodeThatHasWrittenToThisFile() {
        return ffvNewestCodeThatHasWrittenToThisFile;
    }

    public OneNoteHeader setFfvNewestCodeThatHasWrittenToThisFile(
            long ffvNewestCodeThatHasWrittenToThisFile) {
        this.ffvNewestCodeThatHasWrittenToThisFile = ffvNewestCodeThatHasWrittenToThisFile;
        return this;
    }

    public long getFfvOldestCodeThatMayReadThisFile() {
        return ffvOldestCodeThatMayReadThisFile;
    }

    public OneNoteHeader setFfvOldestCodeThatMayReadThisFile(
            long ffvOldestCodeThatMayReadThisFile) {
        this.ffvOldestCodeThatMayReadThisFile = ffvOldestCodeThatMayReadThisFile;
        return this;
    }

    public FileChunkReference getFcrLegacyFreeChunkList() {
        return fcrLegacyFreeChunkList;
    }

    public OneNoteHeader setFcrLegacyFreeChunkList(FileChunkReference fcrLegacyFreeChunkList) {
        this.fcrLegacyFreeChunkList = fcrLegacyFreeChunkList;
        return this;
    }

    public FileChunkReference getFcrLegacyTransactionLog() {
        return fcrLegacyTransactionLog;
    }

    public OneNoteHeader setFcrLegacyTransactionLog(FileChunkReference fcrLegacyTransactionLog) {
        this.fcrLegacyTransactionLog = fcrLegacyTransactionLog;
        return this;
    }

    public long getcTransactionsInLog() {
        return cTransactionsInLog;
    }

    public OneNoteHeader setcTransactionsInLog(long cTransactionsInLog) {
        this.cTransactionsInLog = cTransactionsInLog;
        return this;
    }

    public long getCbLegacyExpectedFileLength() {
        return cbLegacyExpectedFileLength;
    }

    public OneNoteHeader setCbLegacyExpectedFileLength(long cbLegacyExpectedFileLength) {
        this.cbLegacyExpectedFileLength = cbLegacyExpectedFileLength;
        return this;
    }

    public long getRgbPlaceholder() {
        return rgbPlaceholder;
    }

    public OneNoteHeader setRgbPlaceholder(long rgbPlaceholder) {
        this.rgbPlaceholder = rgbPlaceholder;
        return this;
    }

    public FileChunkReference getFcrLegacyFileNodeListRoot() {
        return fcrLegacyFileNodeListRoot;
    }

    public OneNoteHeader setFcrLegacyFileNodeListRoot(
            FileChunkReference fcrLegacyFileNodeListRoot) {
        this.fcrLegacyFileNodeListRoot = fcrLegacyFileNodeListRoot;
        return this;
    }

    public long getCbLegacyFreeSpaceInFreeChunkList() {
        return cbLegacyFreeSpaceInFreeChunkList;
    }

    public OneNoteHeader setCbLegacyFreeSpaceInFreeChunkList(
            long cbLegacyFreeSpaceInFreeChunkList) {
        this.cbLegacyFreeSpaceInFreeChunkList = cbLegacyFreeSpaceInFreeChunkList;
        return this;
    }

    public long getIgnoredZeroA() {
        return ignoredZeroA;
    }

    public OneNoteHeader setIgnoredZeroA(long ignoredZeroA) {
        this.ignoredZeroA = ignoredZeroA;
        return this;
    }

    public long getIgnoredZeroB() {
        return ignoredZeroB;
    }

    public OneNoteHeader setIgnoredZeroB(long ignoredZeroB) {
        this.ignoredZeroB = ignoredZeroB;
        return this;
    }

    public long getIgnoredZeroC() {
        return ignoredZeroC;
    }

    public OneNoteHeader setIgnoredZeroC(long ignoredZeroC) {
        this.ignoredZeroC = ignoredZeroC;
        return this;
    }

    public long getIgnoredZeroD() {
        return ignoredZeroD;
    }

    public OneNoteHeader setIgnoredZeroD(long ignoredZeroD) {
        this.ignoredZeroD = ignoredZeroD;
        return this;
    }

    public GUID getGuidAncestor() {
        return guidAncestor;
    }

    public OneNoteHeader setGuidAncestor(GUID guidAncestor) {
        this.guidAncestor = guidAncestor;
        return this;
    }

    public long getCrcName() {
        return crcName;
    }

    public OneNoteHeader setCrcName(long crcName) {
        this.crcName = crcName;
        return this;
    }

    public FileChunkReference getFcrHashedChunkList() {
        return fcrHashedChunkList;
    }

    public OneNoteHeader setFcrHashedChunkList(FileChunkReference fcrHashedChunkList) {
        this.fcrHashedChunkList = fcrHashedChunkList;
        return this;
    }

    public FileChunkReference getFcrTransactionLog() {
        return fcrTransactionLog;
    }

    public OneNoteHeader setFcrTransactionLog(FileChunkReference fcrTransactionLog) {
        this.fcrTransactionLog = fcrTransactionLog;
        return this;
    }

    public FileChunkReference getFcrFileNodeListRoot() {
        return fcrFileNodeListRoot;
    }

    public OneNoteHeader setFcrFileNodeListRoot(FileChunkReference fcrFileNodeListRoot) {
        this.fcrFileNodeListRoot = fcrFileNodeListRoot;
        return this;
    }

    public FileChunkReference getFcrFreeChunkList() {
        return fcrFreeChunkList;
    }

    public OneNoteHeader setFcrFreeChunkList(FileChunkReference fcrFreeChunkList) {
        this.fcrFreeChunkList = fcrFreeChunkList;
        return this;
    }

    public long getCbExpectedFileLength() {
        return cbExpectedFileLength;
    }

    public OneNoteHeader setCbExpectedFileLength(long cbExpectedFileLength) {
        this.cbExpectedFileLength = cbExpectedFileLength;
        return this;
    }

    public long getCbFreeSpaceInFreeChunkList() {
        return cbFreeSpaceInFreeChunkList;
    }

    public OneNoteHeader setCbFreeSpaceInFreeChunkList(long cbFreeSpaceInFreeChunkList) {
        this.cbFreeSpaceInFreeChunkList = cbFreeSpaceInFreeChunkList;
        return this;
    }

    public GUID getGuidFileVersion() {
        return guidFileVersion;
    }

    public OneNoteHeader setGuidFileVersion(GUID guidFileVersion) {
        this.guidFileVersion = guidFileVersion;
        return this;
    }

    public long getnFileVersionGeneration() {
        return nFileVersionGeneration;
    }

    public OneNoteHeader setnFileVersionGeneration(long nFileVersionGeneration) {
        this.nFileVersionGeneration = nFileVersionGeneration;
        return this;
    }

    public GUID getGuidDenyReadFileVersion() {
        return guidDenyReadFileVersion;
    }

    public OneNoteHeader setGuidDenyReadFileVersion(GUID guidDenyReadFileVersion) {
        this.guidDenyReadFileVersion = guidDenyReadFileVersion;
        return this;
    }

    public long getGrfDebugLogFlags() {
        return grfDebugLogFlags;
    }

    public OneNoteHeader setGrfDebugLogFlags(long grfDebugLogFlags) {
        this.grfDebugLogFlags = grfDebugLogFlags;
        return this;
    }

    public FileChunkReference getFcrDebugLogA() {
        return fcrDebugLogA;
    }

    public OneNoteHeader setFcrDebugLogA(FileChunkReference fcrDebugLogA) {
        this.fcrDebugLogA = fcrDebugLogA;
        return this;
    }

    public FileChunkReference getFcrDebugLogB() {
        return fcrDebugLogB;
    }

    public OneNoteHeader setFcrDebugLogB(FileChunkReference fcrDebugLogB) {
        this.fcrDebugLogB = fcrDebugLogB;
        return this;
    }

    public long getBuildNumberCreated() {
        return buildNumberCreated;
    }

    public OneNoteHeader setBuildNumberCreated(long buildNumberCreated) {
        this.buildNumberCreated = buildNumberCreated;
        return this;
    }

    public long getBuildNumberLastWroteToFile() {
        return buildNumberLastWroteToFile;
    }

    public OneNoteHeader setBuildNumberLastWroteToFile(long buildNumberLastWroteToFile) {
        this.buildNumberLastWroteToFile = buildNumberLastWroteToFile;
        return this;
    }

    public long getBuildNumberOldestWritten() {
        return buildNumberOldestWritten;
    }

    public OneNoteHeader setBuildNumberOldestWritten(long buildNumberOldestWritten) {
        this.buildNumberOldestWritten = buildNumberOldestWritten;
        return this;
    }

    public long getBuildNumberNewestWritten() {
        return buildNumberNewestWritten;
    }

    public OneNoteHeader setBuildNumberNewestWritten(long buildNumberNewestWritten) {
        this.buildNumberNewestWritten = buildNumberNewestWritten;
        return this;
    }
}
