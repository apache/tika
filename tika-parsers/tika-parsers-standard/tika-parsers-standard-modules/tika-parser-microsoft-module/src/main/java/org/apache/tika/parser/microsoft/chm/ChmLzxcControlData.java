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
package org.apache.tika.parser.microsoft.chm;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.tika.exception.TikaException;

/**
 * ::DataSpace/Storage/<SectionName>/ControlData This file contains $20 bytes of
 * information on the compression. The information is partially known: 0000:
 * DWORD 6 (unknown) 0004: ASCII 'LZXC' Compression type identifier 0008: DWORD
 * 2 (Possibly numeric code for LZX) 000C: DWORD The Huffman reset interval in
 * $8000-byte blocks 0010: DWORD The window size in $8000-byte blocks 0014:
 * DWORD unknown (sometimes 2, sometimes 1, sometimes 0) 0018: DWORD 0 (unknown)
 * 001C: DWORD 0 (unknown)
 */
public class ChmLzxcControlData implements ChmAccessor<ChmLzxcControlData> {
    private static final long serialVersionUID = -7897854774939631565L;
    /* class' members */
    private long size; /* 0 */
    private byte[] signature;
    private long version; /* 8 */
    private long resetInterval; /* c */
    private long windowSize; /* 10 */
    private long windowsPerReset; /* 14 */
    private long unknown_18; /* 18 */

    /* local usage */
    private int dataRemained;
    private int currentPlace = 0;

    public ChmLzxcControlData() {
        signature = ChmConstants.LZXC.getBytes(UTF_8); /*
         * 4
         * (LZXC
         * )
         */
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
    }

    /**
     * Returns a remained data
     *
     * @return dataRemained
     */
    private int getDataRemained() {
        return dataRemained;
    }

    /**
     * Sets a remained data
     *
     * @param dataRemained
     */
    private void setDataRemained(int dataRemained) {
        this.dataRemained = dataRemained;
    }

    /**
     * Returns a place holder
     *
     * @return current_place
     */
    private int getCurrentPlace() {
        return currentPlace;
    }

    /**
     * Sets a place holder
     *
     * @param currentPlace
     */
    private void setCurrentPlace(int currentPlace) {
        this.currentPlace = currentPlace;
    }

    /**
     * Returns a size of control data
     *
     * @return size
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets a size of control data
     *
     * @param size
     */
    protected void setSize(long size) {
        this.size = size;
    }

    /**
     * Returns a signature of control data block
     *
     * @return signature
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Sets a signature of control data block
     *
     * @param signature
     */
    protected void setSignature(byte[] signature) {
        this.signature = signature;
    }

    /**
     * Returns a version of control data block
     *
     * @return version
     */
    public long getVersion() {
        return version;
    }

    /**
     * Sets version of control data block
     *
     * @param version
     */
    protected void setVersion(long version) {
        this.version = version;
    }

    /**
     * Returns reset interval
     *
     * @return reset_interval
     */
    public long getResetInterval() {
        return resetInterval;
    }

    /**
     * Sets a reset interval
     *
     * @param resetInterval
     */
    protected void setResetInterval(long resetInterval) {
        this.resetInterval = resetInterval;
    }

    /**
     * Returns a window size
     *
     * @return window_size
     */
    public long getWindowSize() {
        return windowSize;
    }

    /**
     * Sets a window size
     *
     * @param windowSize
     */
    protected void setWindowSize(long windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * Returns windows per reset
     *
     * @return
     */
    public long getWindowsPerReset() {
        return windowsPerReset;
    }

    /**
     * Sets windows per reset
     *
     * @param windowsPerReset
     */
    protected void setWindowsPerReset(long windowsPerReset) {
        this.windowsPerReset = windowsPerReset;
    }

    /**
     * Returns unknown 18 bytes
     *
     * @return unknown_18
     */
    public long getUnknown_18() {
        return unknown_18;
    }

    /**
     * Sets unknown 18 bytes
     *
     * @param unknown_18
     */
    protected void setUnknown_18(long unknown_18) {
        this.unknown_18 = unknown_18;
    }

    private long unmarshalUInt32(byte[] data, long dest) throws ChmParsingException {
        assert (data != null && data.length > 0);
        if (4 > getDataRemained()) {
            throw new ChmParsingException("4 > dataLenght");
        }
        dest = data[this.getCurrentPlace()] | data[this.getCurrentPlace() + 1] << 8 |
                data[this.getCurrentPlace() + 2] << 16 | data[this.getCurrentPlace() + 3] << 24;

        setDataRemained(this.getDataRemained() - 4);
        this.setCurrentPlace(this.getCurrentPlace() + 4);
        return dest;
    }

    private void unmarshalCharArray(byte[] data, ChmLzxcControlData chmLzxcControlData, int count)
            throws TikaException {
        ChmAssert.assertByteArrayNotNull(data);
        ChmAssert.assertChmAccessorNotNull(chmLzxcControlData);
        ChmAssert.assertPositiveInt(count);
        System.arraycopy(data, 4, chmLzxcControlData.getSignature(), 0, count);
        this.setCurrentPlace(this.getCurrentPlace() + count);
        this.setDataRemained(this.getDataRemained() - count);
    }

    /**
     * Returns textual representation of ChmLzxcControlData
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("size(unknown):=")
                .append(this.getSize())
                .append(", ");
        sb.append("signature(Compression type identifier):=")
                .append(new String(this.getSignature(), UTF_8))
                .append(", ");
        sb.append("version(Possibly numeric code for LZX):=")
                .append(this.getVersion())
                .append(System.getProperty("line.separator"));
        sb.append("resetInterval(The Huffman reset interval):=")
                .append(this.getResetInterval())
                .append(", ");
        sb.append("windowSize:=")
                .append(this.getWindowSize())
                .append(", ");
        sb.append("windowsPerReset(unknown (sometimes 2, sometimes 1, sometimes 0):=")
                .append(this.getWindowsPerReset())
                .append(", ");
        sb.append("unknown_18:=")
                .append(this.getUnknown_18())
                .append(System.getProperty("line.separator"));
        return sb.toString();
    }

    // @Override
    public void parse(byte[] data, ChmLzxcControlData chmLzxcControlData) throws TikaException {
        if (data == null || (data.length < ChmConstants.CHM_LZXC_MIN_LEN)) {
            throw new ChmParsingException("we want at least 0x18 bytes");
        }
        chmLzxcControlData.setDataRemained(data.length);
        chmLzxcControlData.setSize(unmarshalUInt32(data, chmLzxcControlData.getSize()));
        chmLzxcControlData
                .unmarshalCharArray(data, chmLzxcControlData, ChmConstants.CHM_SIGNATURE_LEN);
        chmLzxcControlData.setVersion(unmarshalUInt32(data, chmLzxcControlData.getVersion()));
        chmLzxcControlData
                .setResetInterval(unmarshalUInt32(data, chmLzxcControlData.getResetInterval()));
        chmLzxcControlData.setWindowSize(unmarshalUInt32(data, chmLzxcControlData.getWindowSize()));
        chmLzxcControlData
                .setWindowsPerReset(unmarshalUInt32(data, chmLzxcControlData.getWindowsPerReset()));

        if (data.length >= ChmConstants.CHM_LZXC_V2_LEN) {
            chmLzxcControlData
                    .setUnknown_18(unmarshalUInt32(data, chmLzxcControlData.getUnknown_18()));
        } else {
            chmLzxcControlData.setUnknown_18(0);
        }

        if (chmLzxcControlData.getVersion() == 2) {
            chmLzxcControlData.setWindowSize(getWindowSize() * ChmConstants.CHM_WINDOW_SIZE_BLOCK);
        }

        if (chmLzxcControlData.getWindowSize() == 0 || chmLzxcControlData.getResetInterval() == 0) {
            throw new ChmParsingException("window size / resetInterval should be more than zero");
        }

        if (chmLzxcControlData.getWindowSize() == 1) {
            throw new ChmParsingException("window size / resetInterval should be more than 1");
        }

        /* checks a signature */
        if (!new String(chmLzxcControlData.getSignature(), UTF_8).equals(ChmConstants.LZXC)) {
            throw new ChmParsingException("the signature does not seem to be correct");
        }
    }
}
