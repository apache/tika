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
package org.apache.tika.metadata;

/**
 * ZIP file properties collection.
 *
 * @since Apache Tika 4.0
 */
public interface Zip {

    String ZIP_PREFIX = "zip" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    // ==================== Detector Hints ====================
    // These are set by the detector to communicate state to the parser.
    // The detector MUST always set these when detecting a ZIP file,
    // overwriting any user-provided values.

    /**
     * Set by the detector to indicate whether it successfully opened the ZIP as a ZipFile.
     * If true, the ZipFile is available via TikaInputStream.getOpenContainer().
     * If false, ZipFile failed to open (truncated, corrupt, etc.) and parser should use streaming.
     */
    Property DETECTOR_ZIPFILE_OPENED =
            Property.internalBoolean(ZIP_PREFIX + "detectorZipFileOpened");

    /**
     * Set by the detector to indicate whether streaming required DATA_DESCRIPTOR support.
     * If true, parser should start streaming with allowStoredEntriesWithDataDescriptor=true.
     */
    Property DETECTOR_DATA_DESCRIPTOR_REQUIRED =
            Property.internalBoolean(ZIP_PREFIX + "detectorDataDescriptorRequired");

    /**
     * Set to true if the ZIP file was salvaged (rebuilt from a corrupt/truncated original).
     * This indicates that the ZIP could not be opened directly and was repaired by
     * streaming through the local headers and reconstructing a valid ZIP structure.
     */
    Property SALVAGED = Property.internalBoolean(ZIP_PREFIX + "salvaged");

    // ==================== Entry Metadata ====================
    // These are set on embedded document metadata for each ZIP entry.

    /**
     * Comment associated with a ZIP entry.
     */
    Property COMMENT = Property.externalText(ZIP_PREFIX + "comment");

    /**
     * Compression method used for the entry (0=stored, 8=deflated, etc.).
     */
    Property COMPRESSION_METHOD = Property.externalInteger(ZIP_PREFIX + "compressionMethod");

    /**
     * Compressed size of the entry in bytes.
     */
    Property COMPRESSED_SIZE = Property.externalText(ZIP_PREFIX + "compressedSize");

    /**
     * Uncompressed size of the entry in bytes.
     */
    Property UNCOMPRESSED_SIZE = Property.externalText(ZIP_PREFIX + "uncompressedSize");

    /**
     * CRC-32 checksum of the uncompressed entry data.
     */
    Property CRC32 = Property.externalText(ZIP_PREFIX + "crc32");

    /**
     * Unix file mode/permissions for the entry.
     */
    Property UNIX_MODE = Property.externalInteger(ZIP_PREFIX + "unixMode");

    /**
     * Platform that created the entry (0=MS-DOS, 3=Unix, etc.).
     */
    Property PLATFORM = Property.externalInteger(ZIP_PREFIX + "platform");

    /**
     * Version of ZIP specification used to create the entry.
     */
    Property VERSION_MADE_BY = Property.externalInteger(ZIP_PREFIX + "versionMadeBy");

    /**
     * Whether the entry is encrypted.
     */
    Property ENCRYPTED = Property.externalBoolean(ZIP_PREFIX + "encrypted");

    // ==================== Integrity Check Results ====================
    // These are set on the parent document metadata after integrity checking.

    /**
     * Result of the integrity check comparing central directory to local headers.
     * Values: "PASS" (no issues), "FAIL" (issues found), "PARTIAL" (only duplicate check done).
     */
    Property INTEGRITY_CHECK_RESULT = Property.internalText(ZIP_PREFIX + "integrityCheckResult");

    /**
     * Entry names that appear multiple times in the local headers (streaming).
     * Duplicate entries are a potential attack vector.
     */
    Property DUPLICATE_ENTRY_NAMES = Property.internalTextBag(ZIP_PREFIX + "duplicateEntryNames");

    /**
     * Entry names that exist in central directory but not in local headers.
     */
    Property CENTRAL_DIRECTORY_ONLY_ENTRIES =
            Property.internalTextBag(ZIP_PREFIX + "centralDirectoryOnlyEntries");

    /**
     * Entry names that exist in local headers but not in central directory.
     * These are "hidden" entries that some tools won't see.
     */
    Property LOCAL_HEADER_ONLY_ENTRIES =
            Property.internalTextBag(ZIP_PREFIX + "localHeaderOnlyEntries");
}
