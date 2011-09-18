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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.chm.accessor.ChmDirectoryListingSet;
import org.apache.tika.parser.chm.accessor.ChmItsfHeader;
import org.apache.tika.parser.chm.accessor.ChmItspHeader;
import org.apache.tika.parser.chm.accessor.ChmLzxcControlData;
import org.apache.tika.parser.chm.accessor.ChmLzxcResetTable;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.assertion.ChmAssert;
import org.apache.tika.parser.chm.core.ChmCommons.EntryType;
import org.apache.tika.parser.chm.lzx.ChmBlockInfo;
import org.apache.tika.parser.chm.lzx.ChmLzxBlock;

/**
 * Extracts text from chm file. Enumerates chm entries.
 */
public class ChmExtractor {
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

    /**
     * Returns lzxc control data.
     * 
     * @return ChmLzxcControlData
     */
    private ChmLzxcControlData getChmLzxcControlData() {
        return chmLzxcControlData;
    }

    /**
     * Sets lzxc control data
     * 
     * @param chmLzxcControlData
     */
    private void setChmLzxcControlData(ChmLzxcControlData chmLzxcControlData) {
        this.chmLzxcControlData = chmLzxcControlData;
    }

    private ChmItspHeader getChmItspHeader() {
        return chmItspHeader;
    }

    private void setChmItspHeader(ChmItspHeader chmItspHeader) {
        this.chmItspHeader = chmItspHeader;
    }

    /**
     * Returns lzxc reset table
     * 
     * @return ChmLzxcResetTable
     */
    private ChmLzxcResetTable getChmLzxcResetTable() {
        return chmLzxcResetTable;
    }

    /**
     * Sets lzxc reset table
     * 
     * @param chmLzxcResetTable
     */
    private void setChmLzxcResetTable(ChmLzxcResetTable chmLzxcResetTable) {
        this.chmLzxcResetTable = chmLzxcResetTable;
    }

    /**
     * Returns lzxc block length
     * 
     * @return lzxBlockLength
     */
    private long getLzxBlockLength() {
        return lzxBlockLength;
    }

    /**
     * Sets lzxc block length
     * 
     * @param lzxBlockLength
     */
    private void setLzxBlockLength(long lzxBlockLength) {
        this.lzxBlockLength = lzxBlockLength;
    }

    /**
     * Returns lzxc block offset
     * 
     * @return lzxBlockOffset
     */
    private long getLzxBlockOffset() {
        return lzxBlockOffset;
    }

    /**
     * Sets lzxc block offset
     */
    private void setLzxBlockOffset(long lzxBlockOffset) {
        this.lzxBlockOffset = lzxBlockOffset;
    }

    private int getIndexOfContent() {
        return indexOfContent;
    }

    private void setIndexOfContent(int indexOfContent) {
        this.indexOfContent = indexOfContent;
    }

    private byte[] getData() {
        return data;
    }

    private void setData(byte[] data) {
        this.data = data;
    }

    public ChmExtractor(InputStream is) throws TikaException, IOException {
        ChmAssert.assertInputStreamNotNull(is);
        try {
            setData(IOUtils.toByteArray(is));

            /* Creates and parses chm itsf header */
            setChmItsfHeader(new ChmItsfHeader());
            // getChmItsfHeader().parse(Arrays.copyOfRange(getData(), 0,
            // ChmConstants.CHM_ITSF_V3_LEN - 1), getChmItsfHeader());
            getChmItsfHeader().parse(ChmCommons.copyOfRange(getData(), 0,
                            ChmConstants.CHM_ITSF_V3_LEN - 1), getChmItsfHeader());

            /* Creates and parses chm itsp header */
            setChmItspHeader(new ChmItspHeader());
            // getChmItspHeader().parse(Arrays.copyOfRange( getData(), (int)
            // getChmItsfHeader().getDirOffset(),
            // (int) getChmItsfHeader().getDirOffset() +
            // ChmConstants.CHM_ITSP_V1_LEN), getChmItspHeader());
            getChmItspHeader().parse(
                    ChmCommons.copyOfRange(getData(), (int) getChmItsfHeader()
                            .getDirOffset(), (int) getChmItsfHeader().getDirOffset() + 
                            ChmConstants.CHM_ITSP_V1_LEN), getChmItspHeader());

            /* Creates instance of ChmDirListingContainer */
            setChmDirList(new ChmDirectoryListingSet(getData(),
                    getChmItsfHeader(), getChmItspHeader()));

            int indexOfControlData = getChmDirList().getControlDataIndex();
            int indexOfResetData = ChmCommons.indexOfResetTableBlock(getData(),
                    ChmConstants.LZXC.getBytes());
            byte[] dir_chunk = null;
            if (indexOfResetData > 0)
                dir_chunk = ChmCommons.copyOfRange( getData(), indexOfResetData, indexOfResetData  
                        + getChmDirList().getDirectoryListingEntryList().get(indexOfControlData).getLength());
            // dir_chunk = Arrays.copyOfRange(getData(), indexOfResetData,
            // indexOfResetData
            // +
            // getChmDirList().getDirectoryListingEntryList().get(indexOfControlData).getLength());

            /* Creates and parses chm control data */
            setChmLzxcControlData(new ChmLzxcControlData());
            getChmLzxcControlData().parse(dir_chunk, getChmLzxcControlData());

            int indexOfResetTable = getChmDirList().getResetTableIndex();
            setChmLzxcResetTable(new ChmLzxcResetTable());

            int startIndex = (int) getChmDirList().getDataOffset()
                    + getChmDirList().getDirectoryListingEntryList()
                            .get(indexOfResetTable).getOffset();

            // assert startIndex < data.length
            ChmAssert.assertCopyingDataIndex(startIndex, getData().length);

            // dir_chunk = Arrays.copyOfRange(getData(), startIndex, startIndex
            // +
            // getChmDirList().getDirectoryListingEntryList().get(indexOfResetTable).getLength());
            dir_chunk = ChmCommons.copyOfRange(getData(), startIndex, startIndex
                            + getChmDirList().getDirectoryListingEntryList().get(indexOfResetTable).getLength());

            getChmLzxcResetTable().parse(dir_chunk, getChmLzxcResetTable());

            setIndexOfContent(ChmCommons.indexOf(getChmDirList().getDirectoryListingEntryList(), 
                    ChmConstants.CONTENT));
            setLzxBlockOffset((getChmDirList().getDirectoryListingEntryList().get(getIndexOfContent()).getOffset() 
                    + getChmItsfHeader().getDataOffset()));
            setLzxBlockLength(getChmDirList().getDirectoryListingEntryList().get(getIndexOfContent()).getLength());

            setLzxBlocksCache(new ArrayList<ChmLzxBlock>());

        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Enumerates chm entities
     * 
     * @return list of chm entities
     */
    public List<String> enumerateChm() {
        List<String> listOfEntries = new ArrayList<String>();
        for (Iterator<DirectoryListingEntry> it = getChmDirList().getDirectoryListingEntryList().iterator(); it.hasNext();) {
            listOfEntries.add(it.next().getName());
        }
        return listOfEntries;
    }

    /**
     * Decompresses a chm entry
     * 
     * @param directoryListingEntry
     * 
     * @return decompressed data
     * @throws TikaException 
     */
    public byte[][] extractChmEntry(DirectoryListingEntry directoryListingEntry) throws TikaException {
        byte[][] tmp = null;
        byte[] dataSegment = null;
        ChmLzxBlock lzxBlock = null;
        try {
            /* UNCOMPRESSED type is easiest one */
            if (directoryListingEntry.getEntryType() == EntryType.UNCOMPRESSED
                    && directoryListingEntry.getLength() > 0
                    && !ChmCommons.hasSkip(directoryListingEntry)) {
                int dataOffset = (int) (getChmItsfHeader().getDataOffset() + directoryListingEntry
                        .getOffset());
                // dataSegment = Arrays.copyOfRange(getData(), dataOffset,
                // dataOffset + directoryListingEntry.getLength());
                dataSegment = ChmCommons.copyOfRange(getData(), dataOffset,
                        dataOffset + directoryListingEntry.getLength());
            } else if (directoryListingEntry.getEntryType() == EntryType.COMPRESSED
                    && !ChmCommons.hasSkip(directoryListingEntry)) {
                /* Gets a chm block info */
                ChmBlockInfo bb = ChmBlockInfo.getChmBlockInfoInstance(
                        directoryListingEntry, (int) getChmLzxcResetTable()
                                .getBlockLen(), getChmLzxcControlData());
                tmp = new byte[bb.getEndBlock() - bb.getStartBlock() + 1][];

                int i = 0, start = 0, block = 0;

                if ((getLzxBlockLength() < Integer.MAX_VALUE)
                        && (getLzxBlockOffset() < Integer.MAX_VALUE)) {
                    // TODO: Improve the caching
                    // caching ... = O(n^2) - depends on startBlock and endBlock
                    if (getLzxBlocksCache().size() != 0) {
                        for (i = 0; i < getLzxBlocksCache().size(); i++) {
                            lzxBlock = getLzxBlocksCache().get(i);
                            for (int j = bb.getIniBlock(); j <= bb
                                    .getStartBlock(); j++) {
                                if (lzxBlock.getBlockNumber() == j)
                                    if (j > start) {
                                        start = j;
                                        block = i;
                                    }
                                if (start == bb.getStartBlock())
                                    break;
                            }
                        }
                    }

                    if (i == getLzxBlocksCache().size() && i == 0) {
                        start = bb.getIniBlock();

                        dataSegment = ChmCommons.getChmBlockSegment(getData(),
                                getChmLzxcResetTable(), start,
                                (int) getLzxBlockOffset(),
                                (int) getLzxBlockLength());

                        lzxBlock = new ChmLzxBlock(start, dataSegment,
                                getChmLzxcResetTable().getBlockLen(), null);

                        getLzxBlocksCache().add(lzxBlock);
                    } else {
                        lzxBlock = getLzxBlocksCache().get(block);
                    }

                    for (i = start; i <= bb.getEndBlock();) {
                        if (i == bb.getStartBlock() && i == bb.getEndBlock()) {
                            dataSegment = lzxBlock.getContent(
                                    bb.getStartOffset(), bb.getEndOffset());
                            tmp[0] = dataSegment;
                            break;
                        }

                        if (i == bb.getStartBlock()) {
                            dataSegment = lzxBlock.getContent(bb
                                    .getStartOffset());
                            tmp[0] = dataSegment;
                        }

                        if (i > bb.getStartBlock() && i < bb.getEndBlock()) {
                            dataSegment = lzxBlock.getContent();
                            tmp[i - bb.getStartBlock()] = dataSegment;
                        }

                        if (i == bb.getEndBlock()) {
                            dataSegment = lzxBlock.getContent(0,
                                    bb.getEndOffset());
                            tmp[i - bb.getStartBlock()] = dataSegment;
                            break;
                        }

                        i++;

                        if (i % getChmLzxcControlData().getResetInterval() == 0) {
                            lzxBlock = new ChmLzxBlock(i,
                                    ChmCommons.getChmBlockSegment(getData(),
                                            getChmLzxcResetTable(), i,
                                            (int) getLzxBlockOffset(),
                                            (int) getLzxBlockLength()),
                                    getChmLzxcResetTable().getBlockLen(), null);
                        } else {
                            lzxBlock = new ChmLzxBlock(i,
                                    ChmCommons.getChmBlockSegment(getData(),
                                            getChmLzxcResetTable(), i,
                                            (int) getLzxBlockOffset(),
                                            (int) getLzxBlockLength()),
                                    getChmLzxcResetTable().getBlockLen(),
                                    lzxBlock);
                        }

                        getLzxBlocksCache().add(lzxBlock);
                    }

                    if (getLzxBlocksCache().size() > getChmLzxcResetTable()
                            .getBlockCount()) {
                        getLzxBlocksCache().clear();
                    }
                }
            }
        } catch (Exception e) {
            throw new TikaException(e.getMessage());
        }
        return (tmp != null) ? tmp : (new byte[1][]);
    }

    private void setLzxBlocksCache(List<ChmLzxBlock> lzxBlocksCache) {
        this.lzxBlocksCache = lzxBlocksCache;
    }

    private List<ChmLzxBlock> getLzxBlocksCache() {
        return lzxBlocksCache;
    }

    private void setChmDirList(ChmDirectoryListingSet chmDirList) {
        this.chmDirList = chmDirList;
    }

    public ChmDirectoryListingSet getChmDirList() {
        return chmDirList;
    }

    private void setChmItsfHeader(ChmItsfHeader chmItsfHeader) {
        this.chmItsfHeader = chmItsfHeader;
    }

    private ChmItsfHeader getChmItsfHeader() {
        return chmItsfHeader;
    }
}
