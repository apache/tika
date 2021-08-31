package org.apache.tika.parser.microsoft.fsshttpb;
/// <summary>
/// This class is used to check is this a zip file header.
/// </summary>

import java.util.Arrays;

public class ZipHeader {
    /// <summary>
    /// The file header in zip.
    /// </summary>
    public static final byte[] LocalFileHeader = new byte[] {0x50, 0x4b, 0x03, 0x04};

    /// <summary>
    /// Prevents a default instance of the ZipHeader class from being created
    /// </summary>
    private ZipHeader() {
    }

    /// <summary>
    /// Check the input data is a local file header.
    /// </summary>
    /// <param name="byteArray">The content of a file.</param>
    /// <param name="index">The index where to start.</param>
    /// <returns>True if the input data is a local file header, otherwise false.</returns>
    public static boolean IsFileHeader(byte[] byteArray, int index) {
        if (Arrays.equals(LocalFileHeader, Arrays.copyOfRange(byteArray, index, 4))) {
            return true;
        }

        return false;
    }
}