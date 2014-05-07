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
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.exception.ChmParsingException;

/**
 * Directory header The directory starts with a header; its format is as
 * follows: 0000: char[4] 'ITSP' 0004: DWORD Version number 1 0008: DWORD Length
 * of the directory header 000C: DWORD $0a (unknown) 0010: DWORD $1000 Directory
 * chunk size 0014: DWORD "Density" of quickref section, usually 2 0018: DWORD
 * Depth of the index tree - 1 there is no index, 2 if there is one level of
 * PMGI chunks 001C: DWORD Chunk number of root index chunk, -1 if there is none
 * (though at least one file has 0 despite there being no index chunk, probably
 * a bug) 0020: DWORD Chunk number of first PMGL (listing) chunk 0024: DWORD
 * Chunk number of last PMGL (listing) chunk 0028: DWORD -1 (unknown) 002C:
 * DWORD Number of directory chunks (total) 0030: DWORD Windows language ID
 * 0034: GUID {5D02926A-212E-11D0-9DF9-00A0C922E6EC} 0044: DWORD $54 (This is
 * the length again) 0048: DWORD -1 (unknown) 004C: DWORD -1 (unknown) 0050:
 * DWORD -1 (unknown)
 * 
 * {@link http
 * ://translated.by/you/microsoft-s-html-help-chm-format-incomplete/original
 * /?show-translation-form=1}
 * 
 */
public class ChmItspHeader implements ChmAccessor<ChmItspHeader> {
    // TODO: refactor all unmarshals
    private static final long serialVersionUID = 1962394421998181341L;
    private byte[] signature = new String(ChmConstants.ITSP).getBytes(); /*
                                                                          * 0
                                                                          * (ITSP
                                                                          * )
                                                                          */
    private int version; /* 4 */
    private int header_len; /* 8 */
    private int unknown_000c; /* c */
    private long block_len; /* 10 */
    private int blockidx_intvl; /* 14 */
    private int index_depth; /* 18 */
    private int index_root; /* 1c */
    private int index_head; /* 20 */
    private int unknown_0024; /* 24 */
    private long num_blocks; /* 28 */
    private int unknown_002c; /* 2c */
    private long lang_id; /* 30 */
    private byte[] system_uuid = new byte[ChmConstants.BYTE_ARRAY_LENGHT]; /* 34 */
    private byte[] unknown_0044 = new byte[ChmConstants.BYTE_ARRAY_LENGHT]; /* 44 */

    /* local usage */
    private int dataRemained;
    private int currentPlace = 0;

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ signature:=" + new String(getSignature())
                + System.getProperty("line.separator"));
        sb.append("version:=\t" + getVersion()
                + System.getProperty("line.separator"));
        sb.append("header_len:=\t" + getHeader_len()
                + System.getProperty("line.separator"));
        sb.append("unknown_00c:=\t" + getUnknown_000c()
                + System.getProperty("line.separator"));
        sb.append("block_len:=\t" + getBlock_len() + " [directory chunk size]"
                + System.getProperty("line.separator"));
        sb.append("blockidx_intvl:=" + getBlockidx_intvl()
                + ", density of quickref section, usually 2"
                + System.getProperty("line.separator"));
        sb.append("index_depth:=\t"
                + getIndex_depth()
                + ", depth of the index tree - 1 there is no index, 2 if there is one level of PMGI chunk"
                + System.getProperty("line.separator"));
        sb.append("index_root:=\t" + getIndex_root()
                + ", chunk number of root index chunk, -1 if there is none"
                + System.getProperty("line.separator"));
        sb.append("index_head:=\t" + getIndex_head()
                + ", chunk number of first PMGL (listing) chunk"
                + System.getProperty("line.separator"));
        sb.append("unknown_0024:=\t" + getUnknown_0024()
                + ", chunk number of last PMGL (listing) chunk"
                + System.getProperty("line.separator"));
        sb.append("num_blocks:=\t" + getNum_blocks() + ", -1 (unknown)"
                + System.getProperty("line.separator"));
        sb.append("unknown_002c:=\t" + getUnknown_002c()
                + ", number of directory chunks (total)"
                + System.getProperty("line.separator"));
        sb.append("lang_id:=\t" + getLang_id() + " - "
                + ChmCommons.getLanguage(getLang_id())
                + System.getProperty("line.separator"));
        sb.append("system_uuid:=" + getSystem_uuid()
                + System.getProperty("line.separator"));
        sb.append("unknown_0044:=" + getUnknown_0044() + " ]");
        return sb.toString();
    }

    /**
     * Copies 4 bits from data[]
     * 
     * @param data
     * @param chmItspHeader
     * @param count
     * @throws TikaException 
     */
    private void unmarshalCharArray(byte[] data, ChmItspHeader chmItspHeader,
            int count) throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        ChmAssert.assertChmAccessorNotNull(chmItspHeader);
        this.setDataRemained(data.length);
        System.arraycopy(data, 0, chmItspHeader.signature, 0, count);
        this.setCurrentPlace(this.getCurrentPlace() + count);
        this.setDataRemained(this.getDataRemained() - count);
    }

    private int unmarshalInt32(byte[] data, int dataLenght, int dest) throws TikaException {
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

    private long unmarshalUInt32(byte[] data, int dataLenght, long dest) throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        if (4 > dataLenght)
            throw new TikaException("4 > dataLenght");
        dest = data[this.getCurrentPlace()]
                | data[this.getCurrentPlace() + 1] << 8
                | data[this.getCurrentPlace() + 2] << 16
                | data[this.getCurrentPlace() + 3] << 24;

        setDataRemained(this.getDataRemained() - 4);
        this.setCurrentPlace(this.getCurrentPlace() + 4);
        return dest;
    }

    private byte[] unmarshalUuid(byte[] data, int dataLenght, byte[] dest,
            int count) {
        System.arraycopy(data, this.getCurrentPlace(), dest, 0, count);
        this.setCurrentPlace(this.getCurrentPlace() + count);
        this.setDataRemained(this.getDataRemained() - count);
        return dest;
    }

    /**
     * Returns how many bytes remained
     * 
     * @return int
     */
    private int getDataRemained() {
        return dataRemained;
    }

    /**
     * Sets how many bytes remained
     * 
     * @param dataRemained
     */
    private void setDataRemained(int dataRemained) {
        this.dataRemained = dataRemained;
    }

    /**
     * Returns a place holder
     * 
     * @return current place
     */
    private int getCurrentPlace() {
        return currentPlace;
    }

    /**
     * Sets current place
     * 
     * @param currentPlace
     */
    private void setCurrentPlace(int currentPlace) {
        this.currentPlace = currentPlace;
    }

    /**
     * Returns a signature of the header
     * 
     * @return itsp signature
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Sets itsp signature
     * 
     * @param signature
     */
    protected void setSignature(byte[] signature) {
        this.signature = signature;
    }

    /**
     * Returns version of itsp header
     * 
     * @return version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets a version of itsp header
     * 
     * @param version
     */
    protected void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns header length
     * 
     * @return header length
     */
    public int getHeader_len() {
        return header_len;
    }

    /**
     * Sets itsp header length
     * 
     * @param header_len
     */
    protected void setHeader_len(int header_len) {
        this.header_len = header_len;
    }

    /**
     * Returns 000c unknown bytes
     */
    public int getUnknown_000c() {
        return unknown_000c;
    }

    /**
     * Sets 000c unknown bytes Unknown means here that those guys who cracked
     * the chm format do not know what's it purposes for
     * 
     * @param unknown_000c
     */
    protected void setUnknown_000c(int unknown_000c) {
        this.unknown_000c = unknown_000c;
    }

    /**
     * Returns block's length
     * 
     * @return block_length
     */
    public long getBlock_len() {
        return block_len;
    }

    /**
     * Sets block length
     * 
     * @param block_len
     */
    protected void setBlock_len(long block_len) {
        this.block_len = block_len;
    }

    /**
     * Returns block index interval
     * 
     * @return blockidx_intvl
     */
    public int getBlockidx_intvl() {
        return blockidx_intvl;
    }

    /**
     * Sets block index interval
     * 
     * @param blockidx_intvl
     */
    protected void setBlockidx_intvl(int blockidx_intvl) {
        this.blockidx_intvl = blockidx_intvl;
    }

    /**
     * Returns an index depth
     * 
     * @return index_depth
     */
    public int getIndex_depth() {
        return index_depth;
    }

    /**
     * Sets an index depth
     * 
     * @param index_depth
     */
    protected void setIndex_depth(int index_depth) {
        this.index_depth = index_depth;
    }

    /**
     * Returns index root
     * 
     * @return index_root
     */
    public int getIndex_root() {
        return index_root;
    }

    /**
     * Sets an index root
     * 
     * @param index_root
     */
    protected void setIndex_root(int index_root) {
        this.index_root = index_root;
    }

    /**
     * Returns an index head
     * 
     * @return index_head
     */
    public int getIndex_head() {
        return index_head;
    }

    /**
     * Sets an index head
     * 
     * @param index_head
     */
    protected void setIndex_head(int index_head) {
        this.index_head = index_head;
    }

    /**
     * Returns 0024 unknown bytes
     * 
     * @return unknown_0024
     */
    public int getUnknown_0024() {
        return unknown_0024;
    }

    /**
     * Sets 0024 unknown bytes
     * 
     * @param unknown_0024
     */
    protected void setUnknown_0024(int unknown_0024) {
        this.unknown_0024 = unknown_0024;
    }

    /**
     * Returns number of blocks
     * 
     * @return num_blocks
     */
    public long getNum_blocks() {
        return num_blocks;
    }

    /**
     * Sets number of blocks containing in the chm file
     * 
     * @param num_blocks
     */
    protected void setNum_blocks(long num_blocks) {
        this.num_blocks = num_blocks;
    }

    /**
     * Returns 002c unknown bytes
     * 
     * @return unknown_002c
     */
    public int getUnknown_002c() {
        return unknown_002c;
    }

    /**
     * Sets 002c unknown bytes
     * 
     * @param unknown_002c
     */
    protected void setUnknown_002c(int unknown_002c) {
        this.unknown_002c = unknown_002c;
    }

    /**
     * Returns language id
     * 
     * @return lang_id
     */
    public long getLang_id() {
        return lang_id;
    }

    /**
     * Sets language id
     * 
     * @param lang_id
     */
    protected void setLang_id(long lang_id) {
        this.lang_id = lang_id;
    }

    /**
     * Returns system uuid
     * 
     * @return system_uuid
     */
    public byte[] getSystem_uuid() {
        return system_uuid;
    }

    /**
     * Sets system uuid
     * 
     * @param system_uuid
     */
    protected void setSystem_uuid(byte[] system_uuid) {
        this.system_uuid = system_uuid;
    }

    /**
     * Returns 0044 unknown bytes
     * 
     * @return unknown_0044
     */
    public byte[] getUnknown_0044() {
        return unknown_0044;
    }

    /**
     * Sets 0044 unknown bytes
     * 
     * @param unknown_0044
     */
    protected void setUnknown_0044(byte[] unknown_0044) {
        this.unknown_0044 = unknown_0044;
    }

    // @Override
    public void parse(byte[] data, ChmItspHeader chmItspHeader) throws TikaException {
        /* we only know how to deal with the 0x58 and 0x60 byte structures */
        if (data.length != ChmConstants.CHM_ITSP_V1_LEN)
            throw new ChmParsingException("we only know how to deal with the 0x58 and 0x60 byte structures");

        /* unmarshal common fields */
        chmItspHeader.unmarshalCharArray(data, chmItspHeader, ChmConstants.CHM_SIGNATURE_LEN);
        // ChmCommons.unmarshalCharArray(data, chmItspHeader,
        // ChmConstants.CHM_SIGNATURE_LEN);
        chmItspHeader.setVersion(chmItspHeader.unmarshalInt32(data,
                chmItspHeader.getDataRemained(), chmItspHeader.getVersion()));
        chmItspHeader
                .setHeader_len(chmItspHeader.unmarshalInt32(data,
                        chmItspHeader.getDataRemained(),
                        chmItspHeader.getHeader_len()));
        chmItspHeader.setUnknown_000c(chmItspHeader.unmarshalInt32(data,
                chmItspHeader.getDataRemained(),
                chmItspHeader.getUnknown_000c()));
        chmItspHeader.setBlock_len(chmItspHeader.unmarshalUInt32(data,
                chmItspHeader.getDataRemained(), chmItspHeader.getBlock_len()));
        chmItspHeader.setBlockidx_intvl(chmItspHeader.unmarshalInt32(data,
                chmItspHeader.getDataRemained(),
                chmItspHeader.getBlockidx_intvl()));
        chmItspHeader
                .setIndex_depth(chmItspHeader.unmarshalInt32(data,
                        chmItspHeader.getDataRemained(),
                        chmItspHeader.getIndex_depth()));
        chmItspHeader
                .setIndex_root(chmItspHeader.unmarshalInt32(data,
                        chmItspHeader.getDataRemained(),
                        chmItspHeader.getIndex_root()));
        chmItspHeader
                .setIndex_head(chmItspHeader.unmarshalInt32(data,
                        chmItspHeader.getDataRemained(),
                        chmItspHeader.getIndex_head()));
        chmItspHeader.setUnknown_0024(chmItspHeader.unmarshalInt32(data,
                chmItspHeader.getDataRemained(),
                chmItspHeader.getUnknown_0024()));
        chmItspHeader
                .setNum_blocks(chmItspHeader.unmarshalUInt32(data,
                        chmItspHeader.getDataRemained(),
                        chmItspHeader.getNum_blocks()));
        chmItspHeader.setUnknown_002c((chmItspHeader.unmarshalInt32(data,
                chmItspHeader.getDataRemained(),
                chmItspHeader.getUnknown_002c())));
        chmItspHeader.setLang_id(chmItspHeader.unmarshalUInt32(data,
                chmItspHeader.getDataRemained(), chmItspHeader.getLang_id()));
        chmItspHeader
                .setSystem_uuid(chmItspHeader.unmarshalUuid(data,
                        chmItspHeader.getDataRemained(),
                        chmItspHeader.getSystem_uuid(),
                        ChmConstants.BYTE_ARRAY_LENGHT));
        chmItspHeader
                .setUnknown_0044(chmItspHeader.unmarshalUuid(data,
                        chmItspHeader.getDataRemained(),
                        chmItspHeader.getUnknown_0044(),
                        ChmConstants.BYTE_ARRAY_LENGHT));

        /* Checks validity of the itsp header */
        if (!new String(chmItspHeader.getSignature()).equals(ChmConstants.ITSP))
            throw new ChmParsingException("seems not valid signature");

        if (chmItspHeader.getVersion() != ChmConstants.CHM_VER_1)
            throw new ChmParsingException("!=ChmConstants.CHM_VER_1");

        if (chmItspHeader.getHeader_len() != ChmConstants.CHM_ITSP_V1_LEN)
            throw new ChmParsingException("!= ChmConstants.CHM_ITSP_V1_LEN");
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
    }
}
