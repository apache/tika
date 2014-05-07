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
package org.apache.tika.parser.chm.core;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.chm.accessor.ChmLzxcResetTable;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.assertion.ChmAssert;
import org.apache.tika.parser.chm.exception.ChmParsingException;

public class ChmCommons {
    /* Prevents initialization */
    private ChmCommons() {
    }

    public static void assertByteArrayNotNull(byte[] data) throws TikaException {
        if (data == null)
            throw new TikaException("byte[] is null");
    }

    /**
     * Represents entry types: uncompressed, compressed
     */
    public enum EntryType {
        UNCOMPRESSED, COMPRESSED
    }

    /**
     * Represents lzx states: started decoding, not started decoding
     */
    public enum LzxState {
        STARTED_DECODING, NOT_STARTED_DECODING
    }

    /**
     * Represents intel file states during decompression
     */
    public enum IntelState {
        STARTED, NOT_STARTED
    }

    /**
     * Represents lzx block types in order to decompress differently
     */
    public final static int UNDEFINED = 0;
    public final static int VERBATIM = 1;
    public final static int ALIGNED_OFFSET = 2;
    public final static int UNCOMPRESSED = 3;

    /**
     * LZX supports window sizes of 2^15 (32Kb) through 2^21 (2Mb) Returns X,
     * i.e 2^X
     * 
     * @param window
     *            chmLzxControlData.getWindowSize()
     * 
     * @return window size
     */
    public static int getWindowSize(int window) {
        int win = 0;
        while (window > 1) {
            window >>>= 1;
            win++;
        }
        return win;
    }

    public static byte[] getChmBlockSegment(byte[] data,
            ChmLzxcResetTable resetTable, int blockNumber, int lzxcBlockOffset,
            int lzxcBlockLength) throws TikaException {
        ChmAssert.assertChmBlockSegment(data, resetTable, blockNumber,
                lzxcBlockOffset, lzxcBlockLength);
        int blockLength = -1;
        // TODO add int_max_value checking
        if (blockNumber < (resetTable.getBlockAddress().length - 1)) {
            blockLength = (int) (resetTable.getBlockAddress()[blockNumber + 1] - resetTable
                    .getBlockAddress()[blockNumber]);
        } else {
            /* new code */
            if (blockNumber >= resetTable.getBlockAddress().length)
                blockLength = 0;
            else
                /* end new code */
                blockLength = (int) (lzxcBlockLength - resetTable
                        .getBlockAddress()[blockNumber]);
        }
        byte[] t = ChmCommons
                .copyOfRange(
                        data,
                        (int) (lzxcBlockOffset + resetTable.getBlockAddress()[blockNumber]),
                        (int) (lzxcBlockOffset
                                + resetTable.getBlockAddress()[blockNumber] + blockLength));
        return (t != null) ? t : new byte[1];
    }

    /**
     * Returns textual representation of LangID
     * 
     * @param langID
     * 
     * @return language name
     */
    public static String getLanguage(long langID) {
        /* Potential problem with casting */
        switch ((int) langID) {
        case 1025:
            return "Arabic";
        case 1069:
            return "Basque";
        case 1027:
            return "Catalan";
        case 2052:
            return "Chinese (Simplified)";
        case 1028:
            return "Chinese (Traditional)";
        case 1029:
            return "Czech";
        case 1030:
            return "Danish";
        case 1043:
            return "Dutch";
        case 1033:
            return "English (United States)";
        case 1035:
            return "Finnish";
        case 1036:
            return "French";
        case 1031:
            return "German";
        case 1032:
            return "Greek";
        case 1037:
            return "Hebrew";
        case 1038:
            return "Hungarian";
        case 1040:
            return "Italian";
        case 1041:
            return "Japanese";
        case 1042:
            return "Korean";
        case 1044:
            return "Norwegian";
        case 1045:
            return "Polish";
        case 2070:
            return "Portuguese";
        case 1046:
            return "Portuguese (Brazil)";
        case 1049:
            return "Russian";
        case 1051:
            return "Slovakian";
        case 1060:
            return "Slovenian";
        case 3082:
            return "Spanish";
        case 1053:
            return "Swedish";
        case 1055:
            return "Turkish";
        default:
            return "unknown - http://msdn.microsoft.com/en-us/library/bb165625%28VS.80%29.aspx";
        }
    }

    /**
     * Checks skippable patterns
     * 
     * @param directoryListingEntry
     * 
     * @return boolean
     */
    public static boolean hasSkip(DirectoryListingEntry directoryListingEntry) {
        return (directoryListingEntry.getName().startsWith("/$")
                || directoryListingEntry.getName().startsWith("/#") || directoryListingEntry
                .getName().startsWith("::")) ? true : false;
    }

    /**
     * Writes byte[][] to the file
     * 
     * @param buffer
     * @param fileToBeSaved
     *            file name
     * @throws TikaException 
     */
    public static void writeFile(byte[][] buffer, String fileToBeSaved) throws TikaException {
        FileOutputStream output = null;
        if (buffer != null && fileToBeSaved != null
                && !ChmCommons.isEmpty(fileToBeSaved)) {
            try {
                output = new FileOutputStream(fileToBeSaved);
                if (output != null)
                    for (int i = 0; i < buffer.length; i++) {
                        output.write(buffer[i]);
                    }
            } catch (FileNotFoundException e) {
                throw new TikaException(e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (output != null)
                    try {
                        output.flush();
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }
    }

    /**
     * Reverses the order of given array
     * 
     * @param array
     */
    public static void reverse(byte[] array) {
        if (array == null) {
            return;
        }
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    /**
     * Returns an index of the reset table
     * 
     * @param text
     * @param pattern
     * @return index of the reset table
     * @throws ChmParsingException 
     */
    public static final int indexOfResetTableBlock(byte[] text, byte[] pattern) throws ChmParsingException {
        return (indexOf(text, pattern)) - 4;
    }

    /**
     * Searches some pattern in byte[]
     * 
     * @param text
     *            byte[]
     * @param pattern
     *            byte[]
     * @return an index, if nothing found returns -1
     * @throws ChmParsingException 
     */
    public static int indexOf(byte[] text, byte[] pattern) throws ChmParsingException {
        int[] next = null;
        int i = 0, j = -1;

        /* Preprocessing */
        if (pattern != null && text != null) {
            next = new int[pattern.length];
            next[0] = -1;
        } else
            throw new ChmParsingException("pattern and/or text should not be null");

        /* Computes a failure function */
        while (i < pattern.length - 1) {
            if (j == -1 || pattern[i] == pattern[j]) {
                i++;
                j++;
                if (pattern[i] != pattern[j])
                    next[i] = j;
                else
                    next[i] = next[j];
            } else
                j = next[j];
        }

        /* Reinitializes local variables */
        i = j = 0;

        /* Matching */
        while (i < text.length && j < pattern.length) {
            if (j == -1 || pattern[j] == text[i]) {
                i++;
                j++;
            } else
                j = next[j];
        }
        if (j == pattern.length)
            return (i - j); // match found at offset i - M
        else
            return -1; // not found
    }

    /**
     * Searches for some pattern in the directory listing entry list
     * 
     * @param list
     * @param pattern
     * @return an index, if nothing found returns -1
     */
    public static int indexOf(List<DirectoryListingEntry> list, String pattern) {
        int place = 0;
        for (Iterator<DirectoryListingEntry> iterator = list.iterator(); iterator.hasNext();) {
            DirectoryListingEntry directoryListingEntry = iterator.next();
            if (directoryListingEntry.toString().contains(pattern)) {
                return place;
            } else
                ++place;
        }
        return -1;// not found
    }

    /*
     * This method is added because of supporting of Java 5
     */
    public static byte[] copyOfRange(byte[] original, int from, int to) {
        checkCopyOfRangeParams(original, from, to);
        int newLength = to - from;
        if (newLength < 0)
            throw new IllegalArgumentException(from + " > " + to);
        byte[] copy = new byte[newLength];
        System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    private static void checkCopyOfRangeParams(byte[] original, int from, int to) {
        if (original == null)
            throw new NullPointerException("array is null");
        if (from < 0)
            throw new IllegalArgumentException(from + " should be > 0");
        if (to < 0)
            throw new IllegalArgumentException(to + " should be > 0");
    }

    /*
     * This method is added because of supporting of Java 5
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
    }

}
