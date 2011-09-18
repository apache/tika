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
package org.apache.tika.parser.chm.accessor;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.assertion.ChmAssert;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.exception.ChmParsingException;

/**
 * Description There are two types of directory chunks -- index chunks, and
 * listing chunks. The index chunk will be omitted if there is only one listing
 * chunk. A listing chunk has the following format: 0000: char[4] 'PMGL' 0004:
 * DWORD Length of free space and/or quickref area at end of directory chunk
 * 0008: DWORD Always 0 000C: DWORD Chunk number of previous listing chunk when
 * reading directory in sequence (-1 if this is the first listing chunk) 0010:
 * DWORD Chunk number of next listing chunk when reading directory in sequence
 * (-1 if this is the last listing chunk) 0014: Directory listing entries (to
 * quickref area) Sorted by filename; the sort is case-insensitive The quickref
 * area is written backwards from the end of the chunk. One quickref entry
 * exists for every n entries in the file, where n is calculated as 1 + (1 <<
 * quickref density). So for density = 2, n = 5 Chunklen-0002: WORD Number of
 * entries in the chunk Chunklen-0004: WORD Offset of entry n from entry 0
 * Chunklen-0008: WORD Offset of entry 2n from entry 0 Chunklen-000C: WORD
 * Offset of entry 3n from entry 0 ... The format of a directory listing entry
 * is as follows BYTE: length of name BYTEs: name (UTF-8 encoded) ENCINT:
 * content section ENCINT: offset ENCINT: length The offset is from the
 * beginning of the content section the file is in, after the section has been
 * decompressed (if appropriate). The length also refers to length of the file
 * in the section after decompression. There are two kinds of file represented
 * in the directory: user data and format related files. The files which are
 * format-related have names which begin with '::', the user data files have
 * names which begin with "/".
 * 
 * {@link http
 * ://translated.by/you/microsoft-s-html-help-chm-format-incomplete/original
 * /?show-translation-form=1 }
 * 
 * @author olegt
 * 
 */
public class ChmPmglHeader implements ChmAccessor<ChmPmglHeader> {
    private static final long serialVersionUID = -6139486487475923593L;
    private byte[] signature = new String(ChmConstants.PMGL).getBytes(); /*
                                                                          * 0
                                                                          * (PMGL
                                                                          * )
                                                                          */
    private long free_space; /* 4 */
    private long unknown_0008; /* 8 */
    private int block_prev; /* c */
    private int block_next; /* 10 */

    /* local usage */
    private int dataRemained;
    private int currentPlace = 0;

    private int getDataRemained() {
        return dataRemained;
    }

    private void setDataRemained(int dataRemained) {
        this.dataRemained = dataRemained;
    }

    private int getCurrentPlace() {
        return currentPlace;
    }

    private void setCurrentPlace(int currentPlace) {
        this.currentPlace = currentPlace;
    }

    public long getFreeSpace() {
        return free_space;
    }

    public void setFreeSpace(long free_space) {
        this.free_space = free_space;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("signatute:=" + new String(getSignature()) + ", ");
        sb.append("free space:=" + getFreeSpace() + ", ");
        sb.append("unknown0008:=" + getUnknown0008() + ", ");
        sb.append("prev block:=" + getBlockPrev() + ", ");
        sb.append("next block:=" + getBlockNext()
                + System.getProperty("line.separator"));
        return sb.toString();
    }

    protected void unmarshalCharArray(byte[] data, ChmPmglHeader chmPmglHeader,
            int count) throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        this.setDataRemained(data.length);
        System.arraycopy(data, 0, chmPmglHeader.signature, 0, count);
        this.setCurrentPlace(this.getCurrentPlace() + count);
        this.setDataRemained(this.getDataRemained() - count);
    }

    private int unmarshalInt32(byte[] data, int dest) throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        if (4 > this.getDataRemained())
            throw new TikaException("4 > dataLenght");
        dest = data[this.getCurrentPlace()]
                | data[this.getCurrentPlace() + 1] << 8
                | data[this.getCurrentPlace() + 2] << 16
                | data[this.getCurrentPlace() + 3] << 24;

        this.setCurrentPlace(this.getCurrentPlace() + 4);
        this.setDataRemained(this.getDataRemained() - 4);
        return dest;
    }

    private long unmarshalUInt32(byte[] data, long dest) throws ChmParsingException {
        ChmAssert.assertByteArrayNotNull(data);
        if (4 > getDataRemained())
            throw new ChmParsingException("4 > dataLenght");
        dest = data[this.getCurrentPlace()]
                | data[this.getCurrentPlace() + 1] << 8
                | data[this.getCurrentPlace() + 2] << 16
                | data[this.getCurrentPlace() + 3] << 24;

        setDataRemained(this.getDataRemained() - 4);
        this.setCurrentPlace(this.getCurrentPlace() + 4);
        return dest;
    }

    // @Override
    public void parse(byte[] data, ChmPmglHeader chmPmglHeader) throws TikaException {
        if (data.length < ChmConstants.CHM_PMGL_LEN)
            throw new TikaException(ChmPmglHeader.class.getName()
                    + " we only know how to deal with a 0x14 byte structures");

        /* unmarshal fields */
        chmPmglHeader.unmarshalCharArray(data, chmPmglHeader,
                ChmConstants.CHM_SIGNATURE_LEN);
        chmPmglHeader.setFreeSpace(chmPmglHeader.unmarshalUInt32(data,
                chmPmglHeader.getFreeSpace()));
        chmPmglHeader.setUnknown0008(chmPmglHeader.unmarshalUInt32(data,
                chmPmglHeader.getUnknown0008()));
        chmPmglHeader.setBlockPrev(chmPmglHeader.unmarshalInt32(data,
                chmPmglHeader.getBlockPrev()));
        chmPmglHeader.setBlockNext(chmPmglHeader.unmarshalInt32(data,
                chmPmglHeader.getBlockNext()));

        /* check structure */
        if (!new String(chmPmglHeader.getSignature()).equals(ChmConstants.PMGL))
            throw new ChmParsingException(ChmPmglHeader.class.getName()
                    + " pmgl != pmgl.signature");

    }

    public byte[] getSignature() {
        return signature;
    }

    protected void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public long getUnknown0008() {
        return unknown_0008;
    }

    protected void setUnknown0008(long unknown_0008) {
        this.unknown_0008 = unknown_0008;
    }

    public int getBlockPrev() {
        return block_prev;
    }

    protected void setBlockPrev(int block_prev) {
        this.block_prev = block_prev;
    }

    public int getBlockNext() {
        return block_next;
    }

    protected void setBlockNext(int block_next) {
        this.block_next = block_next;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

    }
}
