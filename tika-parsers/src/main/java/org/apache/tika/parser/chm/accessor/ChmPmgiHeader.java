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

import java.util.Arrays;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.assertion.ChmAssert;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.exception.ChmParsingException;

/**
 * Description Note: not always exists An index chunk has the following format:
 * 0000: char[4] 'PMGI' 0004: DWORD Length of quickref/free area at end of
 * directory chunk 0008: Directory index entries (to quickref/free area) The
 * quickref area in an PMGI is the same as in an PMGL The format of a directory
 * index entry is as follows: BYTE: length of name BYTEs: name (UTF-8 encoded)
 * ENCINT: directory listing chunk which starts with name Encoded Integers aka
 * ENCINT An ENCINT is a variable-length integer. The high bit of each byte
 * indicates "continued to the next byte". Bytes are stored most significant to
 * least significant. So, for example, $EA $15 is (((0xEA&0x7F)<<7)|0x15) =
 * 0x3515.
 * 
 * <p>
 * Note: This class is not in use
 * 
 * {@link http
 * ://translated.by/you/microsoft-s-html-help-chm-format-incomplete/original
 * /?show-translation-form=1 }
 * 
 * 
 */
public class ChmPmgiHeader implements ChmAccessor<ChmPmgiHeader> {
    private static final long serialVersionUID = -2092282339894303701L;
    private byte[] signature = new String(ChmConstants.CHM_PMGI_MARKER).getBytes(); /* 0 (PMGI) */
    private long free_space; /* 4 */

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

    private void unmarshalCharArray(byte[] data, ChmPmgiHeader chmPmgiHeader,
            int count) throws ChmParsingException {
        int index = -1;
        ChmAssert.assertByteArrayNotNull(data);
        ChmAssert.assertChmAccessorNotNull(chmPmgiHeader);
        ChmAssert.assertPositiveInt(count);
        this.setDataRemained(data.length);
        index = ChmCommons.indexOf(data,
                ChmConstants.CHM_PMGI_MARKER.getBytes());
        if (index >= 0)
            System.arraycopy(data, index, chmPmgiHeader.getSignature(), 0, count);
        else{
            //Some chm documents (actually most of them) do not contain
            //PMGI header, in this case, we just notice about it.
        }
        this.setCurrentPlace(this.getCurrentPlace() + count);
        this.setDataRemained(this.getDataRemained() - count);
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

    /**
     * Returns pmgi signature if exists
     * 
     * @return signature
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Sets pmgi signature
     * 
     * @param signature
     */
    protected void setSignature(byte[] signature) {
        this.signature = signature;
    }

    /**
     * Returns pmgi free space
     * 
     * @return free_space
     */
    public long getFreeSpace() {
        return free_space;
    }

    /**
     * Sets pmgi free space
     * 
     * @param free_space
     */
    protected void setFreeSpace(long free_space) {
        this.free_space = free_space;
    }

    /**
     * Returns textual representation of the pmgi header
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("signature:=" + new String(getSignature()) + ", ");
        sb.append("free space:=" + getFreeSpace()
                + System.getProperty("line.separator"));
        return sb.toString();
    }

    // @Override
    public void parse(byte[] data, ChmPmgiHeader chmPmgiHeader) throws TikaException {
        /* we only know how to deal with a 0x8 byte structures */
        if (data.length < ChmConstants.CHM_PMGI_LEN)
            throw new TikaException("we only know how to deal with a 0x8 byte structures");

        /* unmarshal fields */
        chmPmgiHeader.unmarshalCharArray(data, chmPmgiHeader, ChmConstants.CHM_SIGNATURE_LEN);
        chmPmgiHeader.setFreeSpace(chmPmgiHeader.unmarshalUInt32(data, chmPmgiHeader.getFreeSpace()));

        /* check structure */
        if (!Arrays.equals(chmPmgiHeader.getSignature(),
                ChmConstants.CHM_PMGI_MARKER.getBytes()))
            throw new TikaException(
                    "it does not seem to be valid a PMGI signature, check ChmItsp index_root if it was -1, means no PMGI, use PMGL insted");

    }

    /**
     * @param args
     */
    public static void main(String[] args) {

    }
}
