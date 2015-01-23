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
import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;
import org.apache.tika.parser.chm.exception.ChmParsingException;

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
     * Sets place holder
     * 
     * @param placeHolder
     */
    private void setPlaceHolder(int placeHolder) {
        this.placeHolder = placeHolder;
    }

    private ChmPmglHeader PMGLheader;
    /**
     * Enumerates chm directory listing entries
     * 
     * @param chmItsHeader
     *            chm itsf PMGLheader
     * @param chmItspHeader
     *            chm itsp PMGLheader
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
            byte[] dir_chunk = null;
            for (int i = startPmgl; i>=0; ) {
                dir_chunk = new byte[(int) chmItspHeader.getBlock_len()];
                int start = i * (int) chmItspHeader.getBlock_len() + dir_offset;
                dir_chunk = ChmCommons
                        .copyOfRange(getData(), start,
                                start +(int) chmItspHeader.getBlock_len());

                PMGLheader = new ChmPmglHeader();
                PMGLheader.parse(dir_chunk, PMGLheader);
                enumerateOneSegment(dir_chunk);
                
                i=PMGLheader.getBlockNext();
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

    public static final boolean startsWith(byte[] data, String prefix) {
        for (int i=0; i<prefix.length(); i++) {
            if (data[i]!=prefix.charAt(i)) {
                return false;
            }
        }
        
        return true;
    }
    /**
     * Enumerates chm directory listing entries in single chm segment
     * 
     * @param dir_chunk
     */
    private void enumerateOneSegment(byte[] dir_chunk) throws ChmParsingException {
//        try {
            if (dir_chunk != null) {
                int header_len;
                if (startsWith(dir_chunk, ChmConstants.CHM_PMGI_MARKER)) {
                    header_len = ChmConstants.CHM_PMGI_LEN;
                    return; //skip PMGI
                }
                else if (startsWith(dir_chunk, ChmConstants.PMGL)) {
                    header_len = ChmConstants.CHM_PMGL_LEN;
                }
                else {
                    throw new ChmParsingException("Bad dir entry block.");
                }

                placeHolder = header_len;
                //setPlaceHolder(header_len);
                while (placeHolder > 0 && placeHolder < dir_chunk.length - PMGLheader.getFreeSpace()
                        /*&& dir_chunk[placeHolder - 1] != 115*/) 
                {
                    //get entry name length
                    int strlen = 0;// = getEncint(data);
                    byte temp;
                    while ((temp=dir_chunk[placeHolder++]) >= 0x80)
                    {
                        strlen <<= 7;
                        strlen += temp & 0x7f;
                    }

                    strlen = (strlen << 7) + temp & 0x7f;
                    
                    if (strlen>dir_chunk.length) {
                        throw new ChmParsingException("Bad data of a string length.");
                    }
                    
                    DirectoryListingEntry dle = new DirectoryListingEntry();
                    dle.setNameLength(strlen);
                    dle.setName(new String(ChmCommons.copyOfRange(
                                dir_chunk, placeHolder,
                                (placeHolder + dle.getNameLength())), IOUtils.UTF_8));

                    checkControlData(dle);
                    checkResetTable(dle);
                    setPlaceHolder(placeHolder
                            + dle.getNameLength());

                    /* Sets entry type */
                    if (placeHolder < dir_chunk.length
                            && dir_chunk[placeHolder] == 0)
                        dle.setEntryType(ChmCommons.EntryType.UNCOMPRESSED);
                    else
                        dle.setEntryType(ChmCommons.EntryType.COMPRESSED);

                    setPlaceHolder(placeHolder + 1);
                    dle.setOffset(getEncint(dir_chunk));
                    dle.setLength(getEncint(dir_chunk));
                    getDirectoryListingEntryList().add(dle);
                }
                
//                int indexWorkData = ChmCommons.indexOf(dir_chunk,
//                        "::".getBytes("UTF-8"));
//                int indexUserData = ChmCommons.indexOf(dir_chunk,
//                        "/".getBytes("UTF-8"));
//
//                if (indexUserData>=0 && indexUserData < indexWorkData)
//                    setPlaceHolder(indexUserData);
//                else if (indexWorkData>=0) {
//                    setPlaceHolder(indexWorkData);
//                }
//                else {
//                    setPlaceHolder(indexUserData);
//                }
//
//                if (placeHolder > 0 && placeHolder < dir_chunk.length - PMGLheader.getFreeSpace()
//                        && dir_chunk[placeHolder - 1] != 115) {// #{
//                    do {
//                        if (dir_chunk[placeHolder - 1] > 0) {
//                            DirectoryListingEntry dle = new DirectoryListingEntry();
//
//                            // two cases: 1. when dir_chunk[placeHolder -
//                            // 1] == 0x73
//                            // 2. when dir_chunk[placeHolder + 1] == 0x2f
//                            doNameCheck(dir_chunk, dle);
//
//                            // dle.setName(new
//                            // String(Arrays.copyOfRange(dir_chunk,
//                            // placeHolder, (placeHolder +
//                            // dle.getNameLength()))));
//                            dle.setName(new String(ChmCommons.copyOfRange(
//                                    dir_chunk, placeHolder,
//                                    (placeHolder + dle.getNameLength())), "UTF-8"));
//                            checkControlData(dle);
//                            checkResetTable(dle);
//                            setPlaceHolder(placeHolder
//                                    + dle.getNameLength());
//
//                            /* Sets entry type */
//                            if (placeHolder < dir_chunk.length
//                                    && dir_chunk[placeHolder] == 0)
//                                dle.setEntryType(ChmCommons.EntryType.UNCOMPRESSED);
//                            else
//                                dle.setEntryType(ChmCommons.EntryType.COMPRESSED);
//
//                            setPlaceHolder(placeHolder + 1);
//                            dle.setOffset(getEncint(dir_chunk));
//                            dle.setLength(getEncint(dir_chunk));
//                            getDirectoryListingEntryList().add(dle);
//                        } else
//                            setPlaceHolder(placeHolder + 1);
//
//                    } while (nextEntry(dir_chunk));
//                }
            }

//        } catch (Exception e) {
//            e.printStackTrace();
//        }
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

        if (placeHolder < data_chunk.length) {
            while ((ob = data_chunk[placeHolder]) < 0) {
                nb[0] = (byte) ((ob & 0x7f));
                bi = bi.shiftLeft(7).add(new BigInteger(nb));
                setPlaceHolder(placeHolder + 1);
            }
            nb[0] = (byte) ((ob & 0x7f));
            bi = bi.shiftLeft(7).add(new BigInteger(nb));
            setPlaceHolder(placeHolder + 1);
        }
        return bi.intValue();
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
