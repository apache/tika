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

/**
 * The format of a directory listing entry is as follows: BYTE: length of name
 * BYTEs: name (UTF-8 encoded) ENCINT: content section ENCINT: offset ENCINT:
 * length The offset is from the beginning of the content section the file is
 * in, after the section has been decompressed (if appropriate). The length also
 * refers to length of the file in the section after decompression. There are
 * two kinds of file represented in the directory: user data and format related
 * files. The files which are format-related have names which begin with '::',
 * the user data files have names which begin with "/".
 * 
 */
public class DirectoryListingEntry {
    /* Length of the entry name */
    private int name_length;
    /* Entry name or directory name */
    private String name;
    /* Entry type */
    private ChmCommons.EntryType entryType;
    /* Entry offset */
    private int offset;
    /* Entry size */
    private int length;

    public DirectoryListingEntry() {

    }

    /**
     * Constructs directoryListingEntry
     * 
     * @param name_length
     *            int
     * @param name
     *            String
     * @param isCompressed
     *            ChmCommons.EntryType
     * @param offset
     *            int
     * @param length
     *            int
     * @throws TikaException 
     */
    public DirectoryListingEntry(int name_length, String name,
            ChmCommons.EntryType isCompressed, int offset, int length) throws TikaException {
        ChmAssert.assertDirectoryListingEntry(name_length, name, isCompressed, offset, length);
        setNameLength(name_length);
        setName(name);
        setEntryType(isCompressed);
        setOffset(offset);
        setLength(length);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name_length:=" + getNameLength() + System.getProperty("line.separator"));
        sb.append("name:=" + getName() + System.getProperty("line.separator"));
        sb.append("entryType:=" + getEntryType() + System.getProperty("line.separator"));
        sb.append("offset:=" + getOffset() + System.getProperty("line.separator"));
        sb.append("length:=" + getLength());
        return sb.toString();
    }

    /**
     * Returns an entry name length
     * 
     * @return int
     */
    public int getNameLength() {
        return name_length;
    }

    /**
     * Sets an entry name length
     * 
     * @param name_length
     *            int
     */
    protected void setNameLength(int name_length) {
        this.name_length = name_length;
    }

    /**
     * Returns an entry name
     * 
     * @return String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets entry name
     * 
     * @param name
     *            String
     */
    protected void setName(String name) {
        this.name = name;
    }

    /**
     * Returns ChmCommons.EntryType (COMPRESSED or UNCOMPRESSED)
     * 
     * @return ChmCommons.EntryType
     */
    public ChmCommons.EntryType getEntryType() {
        return entryType;
    }

    protected void setEntryType(ChmCommons.EntryType entryType) {
        this.entryType = entryType;
    }

    public int getOffset() {
        return offset;
    }

    protected void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLength() {
        return length;
    }

    protected void setLength(int length) {
        this.length = length;
    }

    public static void main(String[] args) {
    }
}
