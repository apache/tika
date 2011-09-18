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

import java.math.BigInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.assertion.ChmAssert;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.exception.ChmParsingException;

/**
 * The Header 0000: char[4] 'ITSF' 0004: DWORD 3 (Version number) 0008: DWORD
 * Total header length, including header section table and following data. 000C:
 * DWORD 1 (unknown) 0010: DWORD a timestamp 0014: DWORD Windows Language ID
 * 0018: GUID {7C01FD10-7BAA-11D0-9E0C-00A0-C922-E6EC} 0028: GUID
 * {7C01FD11-7BAA-11D0-9E0C-00A0-C922-E6EC} Note: a GUID is $10 bytes, arranged
 * as 1 DWORD, 2 WORDs, and 8 BYTEs. 0000: QWORD Offset of section from
 * beginning of file 0008: QWORD Length of section Following the header section
 * table is 8 bytes of additional header data. In Version 2 files, this data is
 * not there and the content section starts immediately after the directory.
 * 
 * {@link http
 * ://translated.by/you/microsoft-s-html-help-chm-format-incomplete/original
 * /?show-translation-form=1}
 * 
 */
/* structure of ITSF headers */
public class ChmItsfHeader implements ChmAccessor<ChmItsfHeader> {
    private static final long serialVersionUID = 2215291838533213826L;
    private byte[] signature = new String("ITSF").getBytes(); /* 0 (ITSF) */
    private int version; /* 4 */
    private int header_len; /* 8 */
    private int unknown_000c; /* c */
    private long last_modified; /* 10 */
    private long lang_id; /* 14 */
    private byte[] dir_uuid = new byte[ChmConstants.BYTE_ARRAY_LENGHT]; /* 18 */
    private byte[] stream_uuid = new byte[ChmConstants.BYTE_ARRAY_LENGHT]; /* 28 */
    private long unknown_offset; /* 38 */
    private long unknown_len; /* 40 */
    private long dir_offset; /* 48 */
    private long dir_len; /* 50 */
    private long data_offset; /* 58 (Not present before V3) */

    /* local usage */
    private int dataRemained;
    private int currentPlace = 0;

    /**
     * Prints the values of ChmfHeader
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(new String(getSignature()) + " ");
        sb.append(getVersion() + " ");
        sb.append(getHeaderLen() + " ");
        sb.append(getUnknown_000c() + " ");
        sb.append(getLastModified() + " ");
        sb.append(getLangId() + " ");
        sb.append(getDir_uuid() + " ");
        sb.append(getStream_uuid() + " ");
        sb.append(getUnknownOffset() + " ");
        sb.append(getUnknownLen() + " ");
        sb.append(getDirOffset() + " ");
        sb.append(getDirLen() + " ");
        sb.append(getDataOffset() + " ");
        return sb.toString();
    }

    /**
     * Returns a signature of itsf header
     * 
     * @return itsf header
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Sets itsf header signature
     * 
     * @param signature
     */
    protected void setSignature(byte[] signature) {
        this.signature = signature;
    }

    /**
     * Returns itsf header version
     * 
     * @return itsf version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Sets itsf version
     * 
     * @param version
     */
    protected void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns itsf header length
     * 
     * @return length
     */
    public int getHeaderLen() {
        return header_len;
    }

    /**
     * Sets itsf header length
     * 
     * @param header_len
     */
    protected void setHeaderLen(int header_len) {
        this.header_len = header_len;
    }

    /**
     * Returns unknown_00c value
     * 
     * @return unknown_00c
     */
    public int getUnknown_000c() {
        return unknown_000c;
    }

    /**
     * Sets unknown_00c
     * 
     * @param unknown_000c
     */
    protected void setUnknown_000c(int unknown_000c) {
        this.unknown_000c = unknown_000c;
    }

    /**
     * Returns last modified date of the chm file
     * 
     * @return last modified date as long
     */
    public long getLastModified() {
        return last_modified;
    }

    /**
     * Sets last modified date of the chm file
     * 
     * @param last_modified
     */
    protected void setLastModified(long last_modified) {
        this.last_modified = last_modified;
    }

    /**
     * Returns language ID
     * 
     * @return language_id
     */
    public long getLangId() {
        return lang_id;
    }

    /**
     * Sets language_id
     * 
     * @param lang_id
     */
    protected void setLangId(long lang_id) {
        this.lang_id = lang_id;
    }

    /**
     * Returns directory uuid
     * 
     * @return dir_uuid
     */
    public byte[] getDir_uuid() {
        return dir_uuid;
    }

    /**
     * Sets directory uuid
     * 
     * @param dir_uuid
     */
    protected void setDir_uuid(byte[] dir_uuid) {
        this.dir_uuid = dir_uuid;
    }

    /**
     * Returns stream uuid
     * 
     * @return stream_uuid
     */
    public byte[] getStream_uuid() {
        return stream_uuid;
    }

    /**
     * Sets stream uuid
     * 
     * @param stream_uuid
     */
    protected void setStream_uuid(byte[] stream_uuid) {
        this.stream_uuid = stream_uuid;
    }

    /**
     * Returns unknown offset
     * 
     * @return unknown_offset
     */
    public long getUnknownOffset() {
        return unknown_offset;
    }

    /**
     * Sets unknown offset
     * 
     * @param unknown_offset
     */
    protected void setUnknownOffset(long unknown_offset) {
        this.unknown_offset = unknown_offset;
    }

    /**
     * Returns unknown length
     * 
     * @return unknown_length
     */
    public long getUnknownLen() {
        return unknown_len;
    }

    /**
     * Sets unknown length
     * 
     * @param unknown_len
     */
    protected void setUnknownLen(long unknown_len) {
        this.unknown_len = unknown_len;
    }

    /**
     * Returns directory offset
     * 
     * @return directory_offset
     */
    public long getDirOffset() {
        return dir_offset;
    }

    /**
     * Sets directory offset
     * 
     * @param dir_offset
     */
    protected void setDirOffset(long dir_offset) {
        this.dir_offset = dir_offset;
    }

    /**
     * Returns directory length
     * 
     * @return directory_offset
     */
    public long getDirLen() {
        return dir_len;
    }

    /**
     * Sets directory length
     * 
     * @param dir_len
     */
    protected void setDirLen(long dir_len) {
        this.dir_len = dir_len;
    }

    /**
     * Returns data offset
     * 
     * @return data_offset
     */
    public long getDataOffset() {
        return data_offset;
    }

    /**
     * Sets data offset
     * 
     * @param data_offset
     */
    protected void setDataOffset(long data_offset) {
        this.data_offset = data_offset;
    }

    /**
     * Copies 4 first bytes of the byte[]
     * 
     * @param data
     * @param chmItsfHeader
     * @param count
     * @throws TikaException 
     */
    private void unmarshalCharArray(byte[] data, ChmItsfHeader chmItsfHeader,
            int count) throws TikaException {
        ChmAssert.assertChmAccessorParameters(data, chmItsfHeader, count);
        System.arraycopy(data, 0, chmItsfHeader.signature, 0, count);
        this.setCurrentPlace(this.getCurrentPlace() + count);
        this.setDataRemained(this.getDataRemained() - count);
    }

    /**
     * Copies X bytes of source byte[] to the dest byte[]
     * 
     * @param data
     * @param dest
     * @param count
     * @return
     */
    private byte[] unmarshalUuid(byte[] data, byte[] dest, int count) {
        System.arraycopy(data, this.getCurrentPlace(), dest, 0, count);
        this.setCurrentPlace(this.getCurrentPlace() + count);
        this.setDataRemained(this.getDataRemained() - count);
        return dest;
    }

    /**
     * Takes 8 bytes and reverses them
     * 
     * @param data
     * @param dest
     * @return
     * @throws TikaException 
     */
    private long unmarshalUint64(byte[] data, long dest) throws TikaException{
        byte[] temp = new byte[8];
        int i, j;

        if (8 > this.getDataRemained())
            throw new TikaException("8 > this.getDataRemained()");

        for (i = 8, j = 7; i > 0; i--) {
            temp[j--] = data[this.getCurrentPlace()];
            this.setCurrentPlace(this.getCurrentPlace() + 1);
        }

        dest = new BigInteger(temp).longValue();
        this.setDataRemained(this.getDataRemained() - 8);
        return dest;
    }

    private int unmarshalInt32(byte[] data, int dest) throws TikaException{
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

    private long unmarshalUInt32(byte[] data, long dest) throws TikaException{
        ChmAssert.assertByteArrayNotNull(data);
        if (4 > getDataRemained())
            throw new TikaException("4 > dataLenght");
        dest = data[this.getCurrentPlace()]
                | data[this.getCurrentPlace() + 1] << 8
                | data[this.getCurrentPlace() + 2] << 16
                | data[this.getCurrentPlace() + 3] << 24;

        setDataRemained(this.getDataRemained() - 4);
        this.setCurrentPlace(this.getCurrentPlace() + 4);
        return dest;
    }

    public static void main(String[] args) {
    }

    /**
     * Sets data remained to be processed
     * 
     * @param dataRemained
     */
    private void setDataRemained(int dataRemained) {
        this.dataRemained = dataRemained;
    }

    /**
     * Returns data remained
     * 
     * @return data_remainned
     */
    private int getDataRemained() {
        return dataRemained;
    }

    /**
     * Sets current place in the byte[]
     * 
     * @param currentPlace
     */
    private void setCurrentPlace(int currentPlace) {
        this.currentPlace = currentPlace;
    }

    /**
     * Returns current place in the byte[]
     * 
     * @return current place
     */
    private int getCurrentPlace() {
        return currentPlace;
    }

    // @Override
    public void parse(byte[] data, ChmItsfHeader chmItsfHeader) throws TikaException {
        if (data.length < ChmConstants.CHM_ITSF_V2_LEN
                || data.length > ChmConstants.CHM_ITSF_V3_LEN)
            throw new TikaException("we only know how to deal with the 0x58 and 0x60 byte structures");

        chmItsfHeader.setDataRemained(data.length);
        chmItsfHeader.unmarshalCharArray(data, chmItsfHeader, ChmConstants.CHM_SIGNATURE_LEN);
        chmItsfHeader.setVersion(chmItsfHeader.unmarshalInt32(data, chmItsfHeader.getVersion()));
        chmItsfHeader.setHeaderLen(chmItsfHeader.unmarshalInt32(data, chmItsfHeader.getHeaderLen()));
        chmItsfHeader.setUnknown_000c(chmItsfHeader.unmarshalInt32(data, chmItsfHeader.getUnknown_000c()));
        chmItsfHeader.setLastModified(chmItsfHeader.unmarshalUInt32(data, chmItsfHeader.getLastModified()));
        chmItsfHeader.setLangId(chmItsfHeader.unmarshalUInt32(data, chmItsfHeader.getLangId()));
        chmItsfHeader.setDir_uuid(chmItsfHeader.unmarshalUuid(data, chmItsfHeader.getDir_uuid(), 16));
        chmItsfHeader.setStream_uuid(chmItsfHeader.unmarshalUuid(data, chmItsfHeader.getStream_uuid(), 16));
        chmItsfHeader.setUnknownOffset(chmItsfHeader.unmarshalUint64(data, chmItsfHeader.getUnknownOffset()));
        chmItsfHeader.setUnknownLen(chmItsfHeader.unmarshalUint64(data, chmItsfHeader.getUnknownLen()));
        chmItsfHeader.setDirOffset(chmItsfHeader.unmarshalUint64(data, chmItsfHeader.getDirOffset()));
        chmItsfHeader.setDirLen(chmItsfHeader.unmarshalUint64(data, chmItsfHeader.getDirLen()));

        if (!new String(chmItsfHeader.getSignature()).equals(ChmConstants.ITSF))
            throw new TikaException("seems not valid file");
        if (chmItsfHeader.getVersion() == ChmConstants.CHM_VER_2) {
            if (chmItsfHeader.getHeaderLen() < ChmConstants.CHM_ITSF_V2_LEN)
                throw new TikaException("something wrong with header");
        } else if (chmItsfHeader.getVersion() == ChmConstants.CHM_VER_3) {
            if (chmItsfHeader.getHeaderLen() < ChmConstants.CHM_ITSF_V3_LEN)
                throw new TikaException("unknown v3 header lenght");
        } else
            throw new ChmParsingException("unsupported chm format");

        /*
         * now, if we have a V3 structure, unmarshal the rest, otherwise,
         * compute it
         */
        if (chmItsfHeader.getVersion() == ChmConstants.CHM_VER_3) {
            if (chmItsfHeader.getDataRemained() >= 0)
                chmItsfHeader.setDataOffset(chmItsfHeader.getDirOffset()
                        + chmItsfHeader.getDirLen());
            else
                throw new TikaException("cannot set data offset, no data remained");
        } else
            chmItsfHeader.setDataOffset(chmItsfHeader.getDirOffset()
                    + chmItsfHeader.getDirLen());
    }
}
