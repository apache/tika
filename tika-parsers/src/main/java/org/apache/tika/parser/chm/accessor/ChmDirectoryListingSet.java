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
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;

/**
 * Holds chm listing entries
 */
public class ChmDirectoryListingSet {
    private List<DirectoryListingEntry> dlel;
    private byte[] data;
    private int placeHolder = -1;
    private long dataOffset = -1;
    private int controlDataIndex = -1;
    private int resetTableIndex = -1;

    private boolean isNotControlDataFound = true;
    private boolean isNotResetTableFound = true;

    /**
     * Constructs chm directory listing set
     * 
     * @param data
     *            byte[]
     * @param chmItsHeader
     * @param chmItspHeader
     * @throws TikaException 
     */
    public ChmDirectoryListingSet(byte[] data, ChmItsfHeader chmItsHeader,
            ChmItspHeader chmItspHeader) throws TikaException {
        setDirectoryListingEntryList(new ArrayList<DirectoryListingEntry>());
        ChmCommons.assertByteArrayNotNull(data);
        setData(data);
        enumerateChmDirectoryListingList(chmItsHeader, chmItspHeader);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("list:=" + getDirectoryListingEntryList().toString()
                + System.getProperty("line.separator"));
        sb.append("number of list items:="
                + getDirectoryListingEntryList().size());
        return sb.toString();
    }

    /**
     * Returns control data index that located in List
     * 
     * @return control data index
     */
    public int getControlDataIndex() {
        return controlDataIndex;
    }

    /**
     * Sets control data index
     * 
     * @param controlDataIndex
     */
    protected void setControlDataIndex(int controlDataIndex) {
        this.controlDataIndex = controlDataIndex;
    }

    /**
     * Return index of reset table
     * 
     * @return reset table index
     */
    public int getResetTableIndex() {
        return resetTableIndex;
    }

    /**
     * Sets reset table index
     * 
     * @param resetTableIndex
     */
    protected void setResetTableIndex(int resetTableIndex) {
        this.resetTableIndex = resetTableIndex;
    }

    /**
     * Gets place holder
     * 
     * @return place holder
     */
    private int getPlaceHolder() {
        return placeHolder;
    }

    /**
     * Sets place holder
     * 
     * @param placeHolder
     */
    private void setPlaceHolder(int placeHolder) {
        this.placeHolder = placeHolder;
    }

    /**
     * Enumerates chm directory listing entries
     * 
     * @param chmItsHeader
     *            chm itsf header
     * @param chmItspHeader
     *            chm itsp header
     */
    private void enumerateChmDirectoryListingList(ChmItsfHeader chmItsHeader,
            ChmItspHeader chmItspHeader) {
        try {
            int startPmgl = chmItspHeader.getIndex_head();
            int stopPmgl = chmItspHeader.getUnknown_0024();
            int dir_offset = (int) (chmItsHeader.getDirOffset() + chmItspHeader
                    .getHeader_len());
            setDataOffset(chmItsHeader.getDataOffset());

            /* loops over all pmgls */
            int previous_index = 0;
            byte[] dir_chunk = null;
            for (int i = startPmgl; i <= stopPmgl; i++) {
                int data_copied = ((1 + i) * (int) chmItspHeader.getBlock_len())
                        + dir_offset;
                if (i == 0) {
                    dir_chunk = new byte[(int) chmItspHeader.getBlock_len()];
                    // dir_chunk = Arrays.copyOfRange(getData(), dir_offset,
                    // (((1+i) * (int)chmItspHeader.getBlock_len()) +
                    // dir_offset));
                    dir_chunk = ChmCommons
                            .copyOfRange(getData(), dir_offset,
                                    (((1 + i) * (int) chmItspHeader
                                            .getBlock_len()) + dir_offset));
                    previous_index = data_copied;
                } else {
                    dir_chunk = new byte[(int) chmItspHeader.getBlock_len()];
                    // dir_chunk = Arrays.copyOfRange(getData(), previous_index,
                    // (((1+i) * (int)chmItspHeader.getBlock_len()) +
                    // dir_offset));
                    dir_chunk = ChmCommons
                            .copyOfRange(getData(), previous_index,
                                    (((1 + i) * (int) chmItspHeader
                                            .getBlock_len()) + dir_offset));
                    previous_index = data_copied;
                }
                enumerateOneSegment(dir_chunk);
                dir_chunk = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            setData(null);
        }
    }

    /**
     * Checks control data
     * 
     * @param dle
     *            chm directory listing entry
     */
    private void checkControlData(DirectoryListingEntry dle) {
        if (isNotControlDataFound) {
            if (dle.getName().contains(ChmConstants.CONTROL_DATA)) {
                setControlDataIndex(getDirectoryListingEntryList().size());
                isNotControlDataFound = false;
            }
        }
    }

    /**
     * Checks reset table
     * 
     * @param dle
     *            chm directory listing entry
     */
    private void checkResetTable(DirectoryListingEntry dle) {
        if (isNotResetTableFound) {
            if (dle.getName().contains(ChmConstants.RESET_TABLE)) {
                setResetTableIndex(getDirectoryListingEntryList().size());
                isNotResetTableFound = false;
            }
        }
    }

    /**
     * Enumerates chm directory listing entries in single chm segment
     * 
     * @param dir_chunk
     */
    private void enumerateOneSegment(byte[] dir_chunk) {
        try {
            if (dir_chunk != null) {

                int indexWorkData = ChmCommons.indexOf(dir_chunk,
                        "::".getBytes());
                int indexUserData = ChmCommons.indexOf(dir_chunk,
                        "/".getBytes());

                if (indexUserData < indexWorkData)
                    setPlaceHolder(indexUserData);
                else
                    setPlaceHolder(indexWorkData);

                if (getPlaceHolder() > 0
                        && dir_chunk[getPlaceHolder() - 1] != 115) {// #{
                    do {
                        if (dir_chunk[getPlaceHolder() - 1] > 0) {
                            DirectoryListingEntry dle = new DirectoryListingEntry();

                            // two cases: 1. when dir_chunk[getPlaceHolder() -
                            // 1] == 0x73
                            // 2. when dir_chunk[getPlaceHolder() + 1] == 0x2f
                            doNameCheck(dir_chunk, dle);

                            // dle.setName(new
                            // String(Arrays.copyOfRange(dir_chunk,
                            // getPlaceHolder(), (getPlaceHolder() +
                            // dle.getNameLength()))));
                            dle.setName(new String(ChmCommons.copyOfRange(
                                    dir_chunk, getPlaceHolder(),
                                    (getPlaceHolder() + dle.getNameLength()))));
                            checkControlData(dle);
                            checkResetTable(dle);
                            setPlaceHolder(getPlaceHolder()
                                    + dle.getNameLength());

                            /* Sets entry type */
                            if (getPlaceHolder() < dir_chunk.length
                                    && dir_chunk[getPlaceHolder()] == 0)
                                dle.setEntryType(ChmCommons.EntryType.UNCOMPRESSED);
                            else
                                dle.setEntryType(ChmCommons.EntryType.COMPRESSED);

                            setPlaceHolder(getPlaceHolder() + 1);
                            dle.setOffset(getEncint(dir_chunk));
                            dle.setLength(getEncint(dir_chunk));
                            getDirectoryListingEntryList().add(dle);
                        } else
                            setPlaceHolder(getPlaceHolder() + 1);

                    } while (hasNext(dir_chunk));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a name and name length are correct. If not then handles it as
     * follows: 1. when dir_chunk[getPlaceHolder() - 1] == 0x73 ('/') 2. when
     * dir_chunk[getPlaceHolder() + 1] == 0x2f ('s')
     * 
     * @param dir_chunk
     * @param dle
     */
    private void doNameCheck(byte[] dir_chunk, DirectoryListingEntry dle) {
        if (dir_chunk[getPlaceHolder() - 1] == 0x73) {
            dle.setNameLength(dir_chunk[getPlaceHolder() - 1] & 0x21);
        } else if (dir_chunk[getPlaceHolder() + 1] == 0x2f) {
            dle.setNameLength(dir_chunk[getPlaceHolder()]);
            setPlaceHolder(getPlaceHolder() + 1);
        } else {
            dle.setNameLength(dir_chunk[getPlaceHolder() - 1]);
        }
    }

    /**
     * Checks if it's possible move further on byte[]
     * 
     * @param dir_chunk
     * 
     * @return boolean
     */
    private boolean hasNext(byte[] dir_chunk) {
        while (getPlaceHolder() < dir_chunk.length) {
            if (dir_chunk[getPlaceHolder()] == 47
                    && dir_chunk[getPlaceHolder() + 1] != ':') {
                setPlaceHolder(getPlaceHolder());
                return true;
            } else if (dir_chunk[getPlaceHolder()] == ':'
                    && dir_chunk[getPlaceHolder() + 1] == ':') {
                setPlaceHolder(getPlaceHolder());
                return true;
            } else
                setPlaceHolder(getPlaceHolder() + 1);
        }
        return false;
    }

    /**
     * Returns encrypted integer
     * 
     * @param data_chunk
     * 
     * @return
     */
    private int getEncint(byte[] data_chunk) {
        byte ob;
        BigInteger bi = BigInteger.ZERO;
        byte[] nb = new byte[1];

        if (getPlaceHolder() < data_chunk.length) {
            while ((ob = data_chunk[getPlaceHolder()]) < 0) {
                nb[0] = (byte) ((ob & 0x7f));
                bi = bi.shiftLeft(7).add(new BigInteger(nb));
                setPlaceHolder(getPlaceHolder() + 1);
            }
            nb[0] = (byte) ((ob & 0x7f));
            bi = bi.shiftLeft(7).add(new BigInteger(nb));
            setPlaceHolder(getPlaceHolder() + 1);
        }
        return bi.intValue();
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
    }

    /**
     * Sets chm directory listing entry list
     * 
     * @param dlel
     *            chm directory listing entry list
     */
    public void setDirectoryListingEntryList(List<DirectoryListingEntry> dlel) {
        this.dlel = dlel;
    }

    /**
     * Returns chm directory listing entry list
     * 
     * @return List<DirectoryListingEntry>
     */
    public List<DirectoryListingEntry> getDirectoryListingEntryList() {
        return dlel;
    }

    /**
     * Sets data
     * 
     * @param data
     */
    private void setData(byte[] data) {
        this.data = data;
    }

    /**
     * Returns data
     * 
     * @return
     */
    private byte[] getData() {
        return data;
    }

    /**
     * Sets data offset
     * 
     * @param dataOffset
     */
    private void setDataOffset(long dataOffset) {
        this.dataOffset = dataOffset;
    }

    /**
     * Returns data offset
     * 
     * @return dataOffset
     */
    public long getDataOffset() {
        return dataOffset;
    }
}
