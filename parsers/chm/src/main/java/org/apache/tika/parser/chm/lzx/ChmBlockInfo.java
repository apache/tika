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
package org.apache.tika.parser.chm.lzx;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.accessor.ChmLzxcControlData;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.exception.ChmParsingException;

/**
 * A container that contains chm block information such as: i. initial block is
 * using to reset main tree ii. start block is using for knowing where to start
 * iii. end block is using for knowing where to stop iv. start offset is using
 * for knowing where to start reading v. end offset is using for knowing where
 * to stop reading
 * 
 */
public class ChmBlockInfo {
    /* class members */
    private int iniBlock;
    private int startBlock;
    private int endBlock;
    private int startOffset;
    private int endOffset;

    private static ChmBlockInfo chmBlockInfo = null;

    private ChmBlockInfo() {

    }

    /**
     * Returns an information related to the chmBlockInfo
     * 
     * @param dle
     *            - DirectoryListingEntry
     * @param bytesPerBlock
     *            - int, = chmLzxcResetTable.block_length
     * @param clcd
     *            - ChmLzxcControlData
     * @param chmBlockInfo
     *            - ChmBlockInfo
     * 
     * @return ChmBlockInfo
     * @throws TikaException 
     */
    protected ChmBlockInfo getChmBlockInfo(DirectoryListingEntry dle,
            int bytesPerBlock, ChmLzxcControlData clcd,
            ChmBlockInfo chmBlockInfo) throws TikaException {
        if (!validateParameters(dle, bytesPerBlock, clcd, chmBlockInfo))
            throw new ChmParsingException("Please check you parameters");

        chmBlockInfo.setStartBlock(dle.getOffset() / bytesPerBlock);
        chmBlockInfo.setEndBlock((dle.getOffset() + dle.getLength())
                / bytesPerBlock);
        chmBlockInfo.setStartOffset(dle.getOffset() % bytesPerBlock);
        chmBlockInfo.setEndOffset((dle.getOffset() + dle.getLength())
                % bytesPerBlock);
        // potential problem with casting long to int
        chmBlockInfo
                .setIniBlock((chmBlockInfo.startBlock - chmBlockInfo.startBlock)
                        % (int) clcd.getResetInterval());
        return chmBlockInfo;
    }

    public static ChmBlockInfo getChmBlockInfoInstance(
            DirectoryListingEntry dle, int bytesPerBlock,
            ChmLzxcControlData clcd) {
        setChmBlockInfo(new ChmBlockInfo());
        getChmBlockInfo().setStartBlock(dle.getOffset() / bytesPerBlock);
        getChmBlockInfo().setEndBlock(
                (dle.getOffset() + dle.getLength()) / bytesPerBlock);
        getChmBlockInfo().setStartOffset(dle.getOffset() % bytesPerBlock);
        getChmBlockInfo().setEndOffset(
                (dle.getOffset() + dle.getLength()) % bytesPerBlock);
        // potential problem with casting long to int
        getChmBlockInfo().setIniBlock(
                (getChmBlockInfo().startBlock - getChmBlockInfo().startBlock)
                        % (int) clcd.getResetInterval());
        return getChmBlockInfo();
    }

    /**
     * Returns textual representation of ChmBlockInfo
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("iniBlock:=" + getIniBlock() + ", ");
        sb.append("startBlock:=" + getStartBlock() + ", ");
        sb.append("endBlock:=" + getEndBlock() + ", ");
        sb.append("startOffset:=" + getStartOffset() + ", ");
        sb.append("endOffset:=" + getEndOffset()
                + System.getProperty("line.separator"));
        return sb.toString();
    }

    private boolean validateParameters(DirectoryListingEntry dle,
            int bytesPerBlock, ChmLzxcControlData clcd,
            ChmBlockInfo chmBlockInfo) {
        int goodParameter = 0;
        if (dle != null)
            ++goodParameter;
        if (bytesPerBlock > 0)
            ++goodParameter;
        if (clcd != null)
            ++goodParameter;
        if (chmBlockInfo != null)
            ++goodParameter;
        return (goodParameter == 4);
    }

    public static void main(String[] args) {
    }

    /**
     * Returns an initial block index
     * 
     * @return int
     */
    public int getIniBlock() {
        return iniBlock;
    }

    /**
     * Sets the initial block index
     * 
     * @param iniBlock
     *            - int
     */
    private void setIniBlock(int iniBlock) {
        this.iniBlock = iniBlock;
    }

    /**
     * Returns the start block index
     * 
     * @return int
     */
    public int getStartBlock() {
        return startBlock;
    }

    /**
     * Sets the start block index
     * 
     * @param startBlock
     *            - int
     */
    private void setStartBlock(int startBlock) {
        this.startBlock = startBlock;
    }

    /**
     * Returns the end block index
     * 
     * @return - int
     */
    public int getEndBlock() {
        return endBlock;
    }

    /**
     * Sets the end block index
     * 
     * @param endBlock
     *            - int
     */
    private void setEndBlock(int endBlock) {
        this.endBlock = endBlock;
    }

    /**
     * Returns the start offset index
     * 
     * @return - int
     */
    public int getStartOffset() {
        return startOffset;
    }

    /**
     * Sets the start offset index
     * 
     * @param startOffset
     *            - int
     */
    private void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    /**
     * Returns the end offset index
     * 
     * @return - int
     */
    public int getEndOffset() {
        return endOffset;
    }

    /**
     * Sets the end offset index
     * 
     * @param endOffset
     *            - int
     */
    private void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    public static void setChmBlockInfo(ChmBlockInfo chmBlockInfo) {
        ChmBlockInfo.chmBlockInfo = chmBlockInfo;
    }

    public static ChmBlockInfo getChmBlockInfo() {
        return chmBlockInfo;
    }
}
