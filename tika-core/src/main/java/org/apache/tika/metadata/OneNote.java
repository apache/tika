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
 * OneNote file-header and revision metadata. Key suffixes are the [MS-ONESTORE]/[MS-FSSHTTPB]
 * field names verbatim (external-standard spellings), under the {@code onenote:} namespace.
 * Previously these were minted per parse via the registering Property factories
 * (TIKA-4816 round-3 review: per-record global-lock interning); a bounded, spec-defined
 * vocabulary belongs in curated constants.
 */
public interface OneNote {

    String PREFIX = "onenote" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    Property BUILD_NUMBER_CREATED = Property.externalText(PREFIX + "buildNumberCreated");
    Property BUILD_NUMBER_LAST_WROTE_TO_FILE =
            Property.externalText(PREFIX + "buildNumberLastWroteToFile");
    Property BUILD_NUMBER_NEWEST_WRITTEN =
            Property.externalText(PREFIX + "buildNumberNewestWritten");
    Property BUILD_NUMBER_OLDEST_WRITTEN =
            Property.externalText(PREFIX + "buildNumberOldestWritten");
    Property CB_EXPECTED_FILE_LENGTH = Property.externalText(PREFIX + "cbExpectedFileLength");
    Property CB_FREE_SPACE_IN_FREE_CHUNK_LIST =
            Property.externalText(PREFIX + "cbFreeSpaceInFreeChunkList");
    Property CB_LEGACY_EXPECTED_FILE_LENGTH =
            Property.externalText(PREFIX + "cbLegacyExpectedFileLength");
    Property CB_LEGACY_FREE_SPACE_IN_FREE_CHUNK_LIST =
            Property.externalText(PREFIX + "cbLegacyFreeSpaceInFreeChunkList");
    Property CRC_NAME = Property.externalText(PREFIX + "crcName");
    Property C_TRANSACTIONS_IN_LOG = Property.externalText(PREFIX + "cTransactionsInLog");
    Property FFV_LAST_CODE_THAT_WROTE_TO_THIS_FILE =
            Property.externalText(PREFIX + "ffvLastCodeThatWroteToThisFile");
    Property FFV_NEWEST_CODE_THAT_HAS_WRITTEN_TO_THIS_FILE =
            Property.externalText(PREFIX + "ffvNewestCodeThatHasWrittenToThisFile");
    Property FFV_OLDEST_CODE_THAT_HAS_WRITTEN_TO_THIS_FILE =
            Property.externalText(PREFIX + "ffvOldestCodeThatHasWrittenToThisFile");
    Property FFV_OLDEST_CODE_THAT_MAY_READ_THIS_FILE =
            Property.externalText(PREFIX + "ffvOldestCodeThatMayReadThisFile");
    Property GRF_DEBUG_LOG_FLAGS = Property.externalText(PREFIX + "grfDebugLogFlags");
    Property N_FILE_VERSION_GENERATION =
            Property.externalText(PREFIX + "nFileVersionGeneration");
    Property RGB_PLACEHOLDER = Property.externalText(PREFIX + "rgbPlaceholder");

    Property MOST_RECENT_AUTHORS = Property.externalTextBag(PREFIX + "mostRecentAuthors");
    Property ORIGINAL_AUTHORS = Property.externalTextBag(PREFIX + "originalAuthors");
    Property CREATION_TIMESTAMP = Property.externalText(PREFIX + "creationTimestamp");
    Property LAST_MODIFIED_TIMESTAMP = Property.externalText(PREFIX + "lastModifiedTimestamp");
}
