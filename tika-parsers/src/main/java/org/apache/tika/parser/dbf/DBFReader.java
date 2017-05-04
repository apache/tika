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
package org.apache.tika.parser.dbf;

import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This reads many dbase3 file variants (not DBASE 7, yet!).
 * This parses the header on open.  The client
 * should get a row and then iterate until next() returns null.
 * Be careful to deepCopy the row (if caching) because the row
 * is mutable and will change as the reader iterates over new rows.
 * <p>
 * This is based on: <a href="http://web.archive.org/web/20150323061445/http://ulisse.elettra.trieste.it/services/doc/dbase/DBFstruct.htm">
 * http://ulisse.elettra.trieste.it/services/doc/dbase/DBFstruct.htm</a>
 * <p>
 * This is designed to separate out Tika-specific code so that it can
 * be copied/pasted as a standalone if desired.
 */

class DBFReader {

    public static final int MAX_FIELD_LENGTH = 66000;
    public static boolean STRICT = false;


    enum Version {

        FOXBASE(0x02, "FoxBASE", ""),
        FOXBASE_PLUS(0x03, "FoxBASE_plus", ""),
        VISUAL_FOXPRO(0x30, "Visual_FoxPro", ""),
        VISUAL_FOXPRO_AUTOINCREMENT(0x31, "Visual_FoxPro", "autoincrement"),
        VISUAL_FOXPRO_VAR(0x32, "Visual_FoxPro", "Varchar_or_Varbinary"),
        DBASE_IV_SQL_TABLE(0x43, "dBASE_IV_SQL", "table"),
        DBASE_IV_SQL_SYSTEM(0x63, "dBASE_IV_SQL", "system"),
        FOX_BASE_PLUS_WITH_MEMO(0x83, "FoxBASE_plus", "memo"),
        DBASE_IV_WITH_MEMO(0x8B, "dBASE_IV", "memo"),
        DBASE_IV_SQL_TABLE_WITH_MEMO(0xCB, "dBASE_IV_SQL", "table_with_memo"),
        FOXPRO_2x_WITH_MEMO(0xF5, "FoxPro_2.x", "memo"),
        HIPER_SIZ_WITH_SMT_MEMO(0xE5, "HiPer-Siz", "SMT_memo"),
        FOXBASE2(0xFB, "FoxBASE", "");

        private final int id;
        private final String format;
        private final String type;

        Version(int id, String format, String type) {
            this.id = id;
            this.format = format;
            this.type = type;
        }

        int getId() {
            return id;
        }

        String getFormat() {
            return format;
        }

        String getType() {
            return type;
        }

        String getFullMimeString() {
            StringBuilder sb = new StringBuilder();
            sb.append("application/x-dbf; ").append("format=").append(getFormat());
            if (!"".equals(type)) {
                sb.append("; type=").append(getType());
            }
            return sb.toString();
        }
    }

    ;

    private static final Map<Integer, Version> VERSION_MAP = new ConcurrentHashMap<>();

    static {
        for (Version version : Version.values()) {
            VERSION_MAP.put(version.id, version);
        }
    }

    static DBFReader open(InputStream is) throws IOException, TikaException {
        return new DBFReader(is);
    }

    //can return null!
    static Version getVersion(int b) {
        return VERSION_MAP.get(b);
    }

    private final DBFFileHeader header;
    private final InputStream is;
    private DBFRow currRow = null;
    private Charset charset = StandardCharsets.US_ASCII;

    private DBFReader(InputStream is) throws IOException, TikaException {
        header = DBFFileHeader.parse(is);
        this.is = is;
        currRow = new DBFRow(header);
    }


    /**
     * Iterate through the rows with this.
     * <p>
     * Be careful: the reader reuses the row!  Make sure to call deep copy
     * if you are buffering rows.
     *
     * @return
     * @throws IOException
     * @throws TikaException
     */
    DBFRow next() throws IOException, TikaException {
        if (fillRow(currRow)) {
            return currRow;
        }
        return null;
    }

    //returns whether or not some content was read.
    //it might not be complete!
    private boolean fillRow(DBFRow row) throws IOException, TikaException {
        if (row == null) {
            return false;
        }
        DBFCell[] cells = row.cells;
        int isDeletedByte = is.read();
        boolean isDeleted = false;
        if (isDeletedByte == 32) {
            //all ok
        } else if (isDeletedByte == 42) {//asterisk
            isDeleted = true;
        } else if (isDeletedByte == 26) {//marker for end of dbf file
            return false;
        } else if (isDeletedByte == -1) {//truncated file
            if (DBFReader.STRICT) {
                throw new IOException("EOF reached too early");
            }
            return false;
        } else {
            throw new TikaException("Expecting space or asterisk at beginning of record, not:" + isDeletedByte);
        }
        row.setDeleted(isDeleted);

        boolean readSomeContent = false;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].read(is)) {
                readSomeContent = true;
            }
        }
        return readSomeContent;
    }

    public DBFFileHeader getHeader() {
        return header;
    }

    public Charset getCharset() {
        return charset;
    }

    /**
     * removes trailing 0 from byte array
     *
     * @param bytes
     * @return
     */
    public static byte[] trim(byte[] bytes) {
        int end = bytes.length - 1;
        for (int i = end; i > -1; i--) {
            if (bytes[i] != 0) {
                end = i;
                break;
            }
        }
        if (end == bytes.length - 1) {
            return bytes;
        }
        byte[] ret = new byte[end + 1];
        System.arraycopy(bytes, 0, ret, 0, end + 1);
        return ret;
    }
}
