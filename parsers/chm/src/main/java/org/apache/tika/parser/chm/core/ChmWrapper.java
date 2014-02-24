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

package org.apache.tika.parser.chm.core;

import java.util.List;

import org.apache.tika.parser.chm.accessor.ChmDirectoryListingSet;
import org.apache.tika.parser.chm.accessor.ChmItsfHeader;
import org.apache.tika.parser.chm.accessor.ChmItspHeader;
import org.apache.tika.parser.chm.accessor.ChmLzxcControlData;
import org.apache.tika.parser.chm.accessor.ChmLzxcResetTable;
import org.apache.tika.parser.chm.lzx.ChmLzxBlock;

public class ChmWrapper {
    private List<ChmLzxBlock> lzxBlocksCache = null;
    private ChmDirectoryListingSet chmDirList = null;
    private ChmItsfHeader chmItsfHeader = null;
    private ChmItspHeader chmItspHeader = null;
    private ChmLzxcResetTable chmLzxcResetTable = null;
    private ChmLzxcControlData chmLzxcControlData = null;
    private byte[] data = null;
    private int indexOfContent;
    private long lzxBlockOffset;
    private long lzxBlockLength;
    private int indexOfResetData;
    private int indexOfResetTable;
    private int startIndex;

    protected int getStartIndex() {
        return startIndex;
    }

    protected void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    protected int getIndexOfResetTable() {
        return indexOfResetTable;
    }

    protected void setIndexOfResetTable(int indexOfResetTable) {
        this.indexOfResetTable = indexOfResetTable;
    }

    protected List<ChmLzxBlock> getLzxBlocksCache() {
        return lzxBlocksCache;
    }

    protected void setLzxBlocksCache(List<ChmLzxBlock> lzxBlocksCache) {
        this.lzxBlocksCache = lzxBlocksCache;
    }

    protected ChmDirectoryListingSet getChmDirList() {
        return chmDirList;
    }

    protected void setChmDirList(ChmDirectoryListingSet chmDirList) {
        this.chmDirList = chmDirList;
    }

    protected ChmItsfHeader getChmItsfHeader() {
        return chmItsfHeader;
    }

    protected void setChmItsfHeader(ChmItsfHeader chmItsfHeader) {
        this.chmItsfHeader = chmItsfHeader;
    }

    protected ChmLzxcResetTable getChmLzxcResetTable() {
        return chmLzxcResetTable;
    }

    protected void setChmLzxcResetTable(ChmLzxcResetTable chmLzxcResetTable) {
        this.chmLzxcResetTable = chmLzxcResetTable;
    }

    protected ChmLzxcControlData getChmLzxcControlData() {
        return chmLzxcControlData;
    }

    protected void setChmLzxcControlData(ChmLzxcControlData chmLzxcControlData) {
        this.chmLzxcControlData = chmLzxcControlData;
    }

    protected byte[] getData() {
        return data;
    }

    protected void setData(byte[] data) {
        this.data = data;
    }

    protected int getIndexOfContent() {
        return indexOfContent;
    }

    protected void setIndexOfContent(int indexOfContent) {
        this.indexOfContent = indexOfContent;
    }

    protected long getLzxBlockOffset() {
        return lzxBlockOffset;
    }

    protected void setLzxBlockOffset(long lzxBlockOffset) {
        this.lzxBlockOffset = lzxBlockOffset;
    }

    protected long getLzxBlockLength() {
        return lzxBlockLength;
    }

    protected void setLzxBlockLength(long lzxBlockLength) {
        this.lzxBlockLength = lzxBlockLength;
    }

    protected void setChmItspHeader(ChmItspHeader chmItspHeader) {
        this.chmItspHeader = chmItspHeader;
    }

    protected ChmItspHeader getChmItspHeader() {
        return chmItspHeader;
    }

    protected void setIndexOfResetData(int indexOfResetData) {
        this.indexOfResetData = indexOfResetData;
    }

    protected int getIndexOfResetData() {
        return indexOfResetData;
    }
}
