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

import java.util.concurrent.CancellationException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.core.ChmCommons.IntelState;
import org.apache.tika.parser.chm.core.ChmCommons.LzxState;
import org.apache.tika.parser.chm.exception.ChmParsingException;

public class ChmLzxState {
    /* Class' members */
    private int window; /* the actual decoding window */
    private long window_size; /* window size (32Kb through 2Mb) */
    private int window_position; /* current offset within the window */
    private int main_tree_elements; /* number of main tree elements */
    private LzxState hadStarted; /* have we started decoding at all yet? */
    private int block_type; /* type of this block */
    private int block_length; /* uncompressed length of this block */
    private int block_remaining; /* uncompressed bytes still left to decode */
    private int frames_read; /* the number of CFDATA blocks processed */
    private int intel_file_size; /* magic header value used for transform */
    private long intel_current_possition; /* current offset in transform space */
    private IntelState intel_state; /* have we seen any translatable data yet? */
    private long R0; /* for the LRU offset system */
    private long R1; /* for the LRU offset system */
    private long R2; /* for the LRU offset system */

    // Trees - PRETREE, MAINTREE, LENGTH, ALIGNED
    protected short[] mainTreeLengtsTable;
    protected short[] mainTreeTable;

    protected short[] lengthTreeTable;
    protected short[] lengthTreeLengtsTable;

    protected short[] alignedLenTable;
    protected short[] alignedTreeTable;

    protected short[] getMainTreeTable() {
        return mainTreeTable;
    }

    protected short[] getAlignedTreeTable() {
        return alignedTreeTable;
    }

    protected void setAlignedTreeTable(short[] alignedTreeTable) {
        this.alignedTreeTable = alignedTreeTable;
    }

    protected short[] getLengthTreeTable() throws TikaException {
        if (lengthTreeTable != null)
            return this.lengthTreeTable;
        else
            throw new ChmParsingException("lengthTreeTable is null");
    }

    protected void setLengthTreeTable(short[] lengthTreeTable) {
        this.lengthTreeTable = lengthTreeTable;
    }

    protected void setMainTreeTable(short[] mainTreeTable) {
        this.mainTreeTable = mainTreeTable;
    }

    protected short[] getAlignedLenTable() {
        return this.alignedLenTable;
    }

    protected void setAlignedLenTable(short[] alignedLenTable) {
        this.alignedLenTable = alignedLenTable;
    }

    /**
     * It suits for informative outlook
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("actual decoding window:=" + getWindow()
                + System.getProperty("line.separator"));
        sb.append("window size (32Kb through 2Mb):=" + getWindowSize()
                + System.getProperty("line.separator"));
        sb.append("current offset within the window:=" + getWindowPosition()
                + System.getProperty("line.separator"));
        sb.append("number of main tree elements:=" + getMainTreeElements()
                + System.getProperty("line.separator"));
        sb.append("have we started decoding at all yet?:=" + getHadStarted()
                + System.getProperty("line.separator"));
        sb.append("type of this block:=" + getBlockType()
                + System.getProperty("line.separator"));
        sb.append("uncompressed length of this block:=" + getBlockLength()
                + System.getProperty("line.separator"));
        sb.append("uncompressed bytes still left to decode:="
                + getBlockRemaining() + System.getProperty("line.separator"));
        sb.append("the number of CFDATA blocks processed:=" + getFramesRead()
                + System.getProperty("line.separator"));
        sb.append("magic header value used for transform:="
                + getIntelFileSize() + System.getProperty("line.separator"));
        sb.append("current offset in transform space:="
                + getIntelCurrentPossition()
                + System.getProperty("line.separator"));
        sb.append("have we seen any translatable data yet?:=" + getIntelState()
                + System.getProperty("line.separator"));
        sb.append("R0 for the LRU offset system:=" + getR0()
                + System.getProperty("line.separator"));
        sb.append("R1 for the LRU offset system:=" + getR1()
                + System.getProperty("line.separator"));
        sb.append("R2 for the LRU offset system:=" + getR2()
                + System.getProperty("line.separator"));
        sb.append("main tree length:=" + getMainTreeLengtsTable().length
                + System.getProperty("line.separator"));
        sb.append("secondary tree length:=" + getLengthTreeLengtsTable().length
                + System.getProperty("line.separator"));
        return sb.toString();
    }

    public ChmLzxState(int window) throws TikaException {
        if (window >= 0) {
            int position_slots;
            int win = ChmCommons.getWindowSize(window);
            setWindowSize(1 << win);
            /* LZX supports window sizes of 2^15 (32Kb) through 2^21 (2Mb) */
            if (win < 15 || win > 21)
                throw new ChmParsingException("window less than 15 or window greater than 21");

            /* Calculates required position slots */
            if (win == 20)
                position_slots = 42;
            else if (win == 21)
                position_slots = 50;
            else
                position_slots = win << 1;

            setR0(1);
            setR1(1);
            setR2(1);
            setMainTreeElements(512);
            setHadStarted(LzxState.NOT_STARTED_DECODING);
            setFramesRead(0);
            setBlockRemaining(0);
            setBlockType(ChmConstants.LZX_BLOCKTYPE_INVALID);
            setIntelCurrentPossition(0);
            setIntelState(IntelState.NOT_STARTED);
            setWindowPosition(0);
            setMainTreeLengtsTable(new short[getMainTreeElements()]);
            setLengthTreeLengtsTable(new short[ChmConstants.LZX_NUM_SECONDARY_LENGTHS]);
        } else
            throw new CancellationException(
                    "window size should be more than zero");
    }

    protected void setWindow(int window) {
        this.window = window;
    }

    protected int getWindow() {
        return window;
    }

    protected void setWindowSize(long window_size) {
        this.window_size = window_size;
    }

    protected long getWindowSize() {
        return window_size;
    }

    protected void setWindowPosition(int window_position) {
        this.window_position = window_position;
    }

    protected int getWindowPosition() {
        return window_position;
    }

    protected void setMainTreeElements(int main_tree_elements) {
        this.main_tree_elements = main_tree_elements;
    }

    protected int getMainTreeElements() {
        return main_tree_elements;
    }

    protected void setHadStarted(LzxState hadStarted) {
        this.hadStarted = hadStarted;
    }

    protected LzxState getHadStarted() {
        return hadStarted;
    }

    protected void setBlockType(int block_type) {
        this.block_type = block_type;
    }

    public int getBlockType() {
        return block_type;
    }

    protected void setBlockLength(int block_length) {
        this.block_length = block_length;
    }

    protected int getBlockLength() {
        return block_length;
    }

    protected void setBlockRemaining(int block_remaining) {
        this.block_remaining = block_remaining;
    }

    protected int getBlockRemaining() {
        return block_remaining;
    }

    protected void setFramesRead(int frames_read) {
        this.frames_read = frames_read;
    }

    protected void increaseFramesRead() {
        this.frames_read = getFramesRead() + 1;
    }

    protected int getFramesRead() {
        return frames_read;
    }

    protected void setIntelFileSize(int intel_file_size) {
        this.intel_file_size = intel_file_size;
    }

    protected int getIntelFileSize() {
        return intel_file_size;
    }

    protected void setIntelCurrentPossition(long intel_current_possition) {
        this.intel_current_possition = intel_current_possition;
    }

    protected long getIntelCurrentPossition() {
        return intel_current_possition;
    }

    protected void setIntelState(IntelState intel_state) {
        this.intel_state = intel_state;
    }

    protected IntelState getIntelState() {
        return intel_state;
    }

    protected void setR0(long r0) {
        R0 = r0;
    }

    protected long getR0() {
        return R0;
    }

    protected void setR1(long r1) {
        R1 = r1;
    }

    protected long getR1() {
        return R1;
    }

    protected void setR2(long r2) {
        R2 = r2;
    }

    protected long getR2() {
        return R2;
    }

    public static void main(String[] args) {
    }

    public void setMainTreeLengtsTable(short[] mainTreeLengtsTable) {
        this.mainTreeLengtsTable = mainTreeLengtsTable;
    }

    public short[] getMainTreeLengtsTable() {
        return mainTreeLengtsTable;
    }

    public void setLengthTreeLengtsTable(short[] lengthTreeLengtsTable) {
        this.lengthTreeLengtsTable = lengthTreeLengtsTable;
    }

    public short[] getLengthTreeLengtsTable() {
        return lengthTreeLengtsTable;
    }
}
