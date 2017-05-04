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

import org.apache.commons.io.IOUtils;
import org.apache.tika.io.EndianUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

class DBFCell {

    private final DBFColumnHeader.ColType colType;
    private final byte[] bytes;
    private final int decimalCount;
    int bytesReadLast = 0;

    DBFCell(DBFColumnHeader.ColType colType, int fieldLength, int decimalCount) {
        this.colType = colType;
        this.decimalCount = decimalCount;
        this.bytes = new byte[fieldLength];
    }

    String getString(Charset charset) {
        switch (colType) {
            case C:
                return new String(getBytes(), charset).trim();
            case D:
                return getFormattedDate();
            case N:
                return new String(getBytes(), StandardCharsets.US_ASCII).trim();
            case L:
                return new String(getBytes(), StandardCharsets.US_ASCII).trim();
            case T:
                return getFormattedDateTime();
            default:
                //TODO: find examples of other cell types for testing
                return new String(getBytes(), StandardCharsets.US_ASCII).trim();
        }
    }

    //returns whether any content was read
    boolean read(InputStream is) throws IOException {
        bytesReadLast = IOUtils.read(is, bytes);
        if (DBFReader.STRICT && bytesReadLast != bytes.length) {
            throw new IOException("Truncated record, only read "+bytesReadLast+
                    " bytes, but should have read: "+bytes.length);
        }
        return bytesReadLast > 0;
    }

    /**
     *
     * @return copy of bytes that were read on the last read
     */
    byte[] getBytes() {
        byte[] ret = new byte[bytesReadLast];
        System.arraycopy(bytes, 0, ret, 0, bytesReadLast);
        return ret;
    }

    DBFColumnHeader.ColType getColType() {
        return colType;
    }

    @Override
    public String toString() {
        return "DBFCell{" +
                "colType=" + colType +
                ", bytes=" + Arrays.toString(bytes) +
                ", decimalCount=" + decimalCount +
                '}';
    }

    DBFCell deepCopy() {
        DBFCell cell = new DBFCell(colType, bytes.length, decimalCount);
        cell.bytesReadLast = this.bytesReadLast;
        System.arraycopy(this.bytes, 0, cell.bytes, 0, bytesReadLast);
        return cell;
    }

    private String getFormattedDate() {
        byte[] dateBytes = getBytes();
        if (dateBytes.length < 8) {
            return "";
        }
        String year = new String(dateBytes, 0, 4, StandardCharsets.US_ASCII);
        String month = new String(dateBytes, 4, 2, StandardCharsets.US_ASCII);
        String day = new String(dateBytes, 6, 2, StandardCharsets.US_ASCII);
        //test to see that these values make any sense
        for (String s : new String[]{year, month, day}) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return "";
            }
        }
        return String.format(Locale.ROOT,
                "%s/%s/%s", month, day, year);
    }

    public String getFormattedDateTime() {
        //sometimes 12/31/1899 instead of 01/01/4713 BC.
        //http://stackoverflow.com/questions/20026154/convert-dbase-timestamp
        //TODO: add heuristic for deciding;
        //TODO: find example of file with time != 0
        Calendar baseCalendar = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
//        baseCalendar.set(1899, 11, 31, 0, 0, 0);
        baseCalendar.set(-4712, 0, 1, 0, 0, 0);
        byte[] bytes = getBytes();
        try (InputStream is = new ByteArrayInputStream(getBytes())) {

            int date = EndianUtils.readIntLE(is);
            int time = EndianUtils.readIntLE(is);
            baseCalendar.add(Calendar.DATE, date);
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            return df.format(baseCalendar.getTime());
        } catch (IOException|EndianUtils.BufferUnderrunException e) {

        }
        return "";
    }
}
