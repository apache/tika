package org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic;
/**
 * This class is used to check is this a zip file header
 */

import java.util.Arrays;

public class ZipHeader {
    /**
     * The file header in zip.
     */
    public static final byte[] LocalFileHeader = new byte[] {0x50, 0x4b, 0x03, 0x04};

    /**
     * Prevents a default instance of the ZipHeader class from being created
     */
    private ZipHeader() {
    }

    /**
     * Check the input data is a local file header.
     *
     * @param byteArray The content of a file.
     * @param index     The index where to start.
     * @return True if the input data is a local file header, otherwise false.
     */
    public static boolean IsFileHeader(byte[] byteArray, int index) {
        if (Arrays.equals(LocalFileHeader, Arrays.copyOfRange(byteArray, index, 4))) {
            return true;
        }

        return false;
    }
}