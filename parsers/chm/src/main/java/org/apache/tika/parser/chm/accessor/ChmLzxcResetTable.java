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
import java.util.Arrays;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.assertion.ChmAssert;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.exception.ChmParsingException;

/**
 * LZXC reset table For ensuring a decompression. Reads the block named
 * "::DataSpace/Storage/<SectionName>/Transform/{7FC28940-9D31-11D0-9B27-00A0C91E9C7C}/InstanceData/ResetTable"
 * .
 * 
 * {@link http
 * ://translated.by/you/microsoft-s-html-help-chm-format-incomplete/original
 * /?page=2 }
 * 
 */
public class ChmLzxcResetTable implements ChmAccessor<ChmLzxcResetTable> {
    private static final long serialVersionUID = -8209574429411707460L;
    /* class members */
    private long version; // 0000: DWORD 2 unknown (possibly a version number)
    private long block_count; // 0004: DWORD Number of entries in reset table
    private long unknown; // 0008: DWORD 8 unknown
    private long table_offset; // 000C: DWORD $28 Length of table header (area
                               // before table entries)
    private long uncompressed_len; // 0010: QWORD Uncompressed Length
    private long compressed_len; // 0018: QWORD Compressed Length
    private long block_len; // 0020: QWORD 0x8000 block size for locations below
    private long[] block_address;

    /* local usage */
    private int dataRemained;
    private int currentPlace = 0;

    private int getDataRemained() {
        return dataRemained;
    }

    private void setDataRemained(int dataRemained) {
        this.dataRemained = dataRemained;
    }

    /**
     * Returns block addresses
     * 
     * @return block addresses
     */
    public long[] getBlockAddress() {
        return block_address;
    }

    /**
     * Sets block addresses
     * 
     * @param block_address
     */
    public void setBlockAddress(long[] block_address) {
        this.block_address = block_address;
    }

    private int getCurrentPlace() {
        return currentPlace;
    }

    private void setCurrentPlace(int currentPlace) {
        this.currentPlace = currentPlace;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("version:=" + getVersion()
                + System.getProperty("line.separator"));
        sb.append("block_count:=" + getBlockCount()
                + System.getProperty("line.separator"));
        sb.append("unknown:=" + getUnknown()
                + System.getProperty("line.separator"));
        sb.append("table_offset:=" + getTableOffset()
                + System.getProperty("line.separator"));
        sb.append("uncompressed_len:=" + getUncompressedLen()
                + System.getProperty("line.separator"));
        sb.append("compressed_len:=" + getCompressedLen()
                + System.getProperty("line.separator"));
        sb.append("block_len:=" + getBlockLen()
                + System.getProperty("line.separator"));
        sb.append("block_addresses:=" + Arrays.toString(getBlockAddress()));
        return sb.toString();
    }

    /**
     * Enumerates chm block addresses
     * 
     * @param data
     * 
     * @return byte[] of addresses
     * @throws TikaException 
     */
    private long[] enumerateBlockAddresses(byte[] data) throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        /* we have limit of number of blocks to be extracted */
        if (getBlockCount() > 5000)
            setBlockCount(5000);

        if (getBlockCount() < 0 && (getDataRemained() / 8) > 0)
            setBlockCount(getDataRemained() / 8);

        long[] addresses = new long[(int) getBlockCount()];
        int rem = getDataRemained() / 8;
        for (int i = 0; i < rem; i++) {
            long num = -1;

            try {
                addresses[i] = unmarshalUint64(data, num);
            } catch (Exception e) {
                throw new TikaException(e.getMessage());
            }
        }
        return addresses;
    }

    /**
     * Validates parameters such as byte[] and chm lzxc reset table
     * 
     * @param data
     * @param chmLzxcResetTable
     * 
     * @return boolean
     * @throws TikaException 
     */
    private boolean validateParamaters(byte[] data,
            ChmLzxcResetTable chmLzxcResetTable) throws TikaException {
        int goodParameter = 0;
        ChmAssert.assertByteArrayNotNull(data);
        ++goodParameter;
        ChmAssert.assertChmAccessorNotNull(chmLzxcResetTable);
        ++goodParameter;
        return (goodParameter == 2);
    }

    private long unmarshalUInt32(byte[] data, long dest) throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        dest = data[this.getCurrentPlace()]
                | data[this.getCurrentPlace() + 1] << 8
                | data[this.getCurrentPlace() + 2] << 16
                | data[this.getCurrentPlace() + 3] << 24;

        setDataRemained(this.getDataRemained() - 4);
        this.setCurrentPlace(this.getCurrentPlace() + 4);
        return dest;
    }

    private long unmarshalUint64(byte[] data, long dest) throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        byte[] temp = new byte[8];
        int i, j;// counters

        for (i = 8, j = 7; i > 0; i--) {
            if (data.length > this.getCurrentPlace()) {
                temp[j--] = data[this.getCurrentPlace()];
                this.setCurrentPlace(this.getCurrentPlace() + 1);
            } else
                throw new TikaException("data is too small to calculate address block");
        }
        dest = new BigInteger(temp).longValue();
        this.setDataRemained(this.getDataRemained() - 8);
        return dest;
    }

    /**
     * Returns the version
     * 
     * @return - long
     */
    public long getVersion() {
        return version;
    }

    /**
     * Sets the version
     * 
     * @param version
     *            - long
     */
    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * Gets a block count
     * 
     * @return - int
     */
    public long getBlockCount() {
        return block_count;
    }

    /**
     * Sets a block count
     * 
     * @param block_count
     *            - long
     */
    public void setBlockCount(long block_count) {
        this.block_count = block_count;
    }

    /**
     * Gets unknown
     * 
     * @return - long
     */
    public long getUnknown() {
        return unknown;
    }

    /**
     * Sets an unknown
     * 
     * @param unknown
     *            - long
     */
    public void setUnknown(long unknown) {
        this.unknown = unknown;
    }

    /**
     * Gets a table offset
     * 
     * @return - long
     */
    public long getTableOffset() {
        return table_offset;
    }

    /**
     * Sets a table offset
     * 
     * @param table_offset
     *            - long
     */
    public void setTableOffset(long table_offset) {
        this.table_offset = table_offset;
    }

    /**
     * Gets uncompressed length
     * 
     * @return - {@link BigInteger }
     */
    public long getUncompressedLen() {
        return uncompressed_len;
    }

    /**
     * Sets uncompressed length
     * 
     * @param uncompressed_len
     *            - {@link BigInteger}
     */
    public void setUncompressedLen(long uncompressed_len) {
        this.uncompressed_len = uncompressed_len;
    }

    /**
     * Gets compressed length
     * 
     * @return - {@link BigInteger}
     */
    public long getCompressedLen() {
        return compressed_len;
    }

    /**
     * Sets compressed length
     * 
     * @param compressed_len
     *            - {@link BigInteger}
     */
    public void setCompressedLen(long compressed_len) {
        this.compressed_len = compressed_len;
    }

    /**
     * Gets a block length
     * 
     * @return - {@link BigInteger}
     */
    public long getBlockLen() {
        return block_len;
    }

    /**
     * Sets a block length
     * 
     * @param block_len
     *            - {@link BigInteger}
     */
    public void setBlockLlen(long block_len) {
        this.block_len = block_len;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

    }

    // @Override
    public void parse(byte[] data, ChmLzxcResetTable chmLzxcResetTable) throws TikaException {
        setDataRemained(data.length);
        if (validateParamaters(data, chmLzxcResetTable)) {
            /* unmarshal fields */
            chmLzxcResetTable.setVersion(unmarshalUInt32(data, chmLzxcResetTable.getVersion()));
            chmLzxcResetTable.setBlockCount(unmarshalUInt32(data, chmLzxcResetTable.getBlockCount()));
            chmLzxcResetTable.setUnknown(unmarshalUInt32(data, chmLzxcResetTable.getUnknown()));
            chmLzxcResetTable.setTableOffset(unmarshalUInt32(data, chmLzxcResetTable.getTableOffset()));
            chmLzxcResetTable.setUncompressedLen(unmarshalUint64(data, chmLzxcResetTable.getUncompressedLen()));
            chmLzxcResetTable.setCompressedLen(unmarshalUint64(data, chmLzxcResetTable.getCompressedLen()));
            chmLzxcResetTable.setBlockLlen(unmarshalUint64(data, chmLzxcResetTable.getBlockLen()));
            chmLzxcResetTable.setBlockAddress(enumerateBlockAddresses(data));
        }

        /* checks chmLzxcResetTable */
        if (chmLzxcResetTable.getVersion() != ChmConstants.CHM_VER_2)
            throw new ChmParsingException(
                    "does not seem currect version of chmLzxcResetTable");
    }
}
