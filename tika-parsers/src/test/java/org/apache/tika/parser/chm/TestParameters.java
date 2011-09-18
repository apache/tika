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
package org.apache.tika.parser.chm;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.chm.core.ChmCommons.EntryType;

/**
 * Holds test parameters such as verification points
 */
public class TestParameters {
    /* Prevents initialization */
    private TestParameters() {
    }

    /* Tests values */
    static final int nameLength = 5;
    static final String entryName = TestParameters.class.getName();
    static EntryType entryType = EntryType.COMPRESSED;
    static final int offset = 3;
    static final int length = 20;
    static final int NTHREADS = 2;

    static final int BUFFER_SIZE = 16384;

    static final byte[] chmData = readResource("/test-documents/testChm.chm");

    private static byte[] readResource(String name) {
        try {
            InputStream stream = TestParameters.class.getResourceAsStream(name);
            try {
                return IOUtils.toByteArray(stream);
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* Verification points */
    static final String VP_CHM_MIME_TYPE = "Content-Type=application/x-chm";
    static final String VP_EXTRACTED_TEXT = "The TCard method accepts only numeric arguments";
    static final String VP_ISTF_SIGNATURE = "ITSF";
    static final String VP_ISTP_SIGNATURE = "ITSP";
    static final String VP_PMGL_SIGNATURE = "PMGL";
    static final String VP_CONTROL_DATA_SIGNATURE = "LZXC";

    static final int VP_DIRECTORY_LENGTH = 4180;
    static final int VP_DATA_OFFSET_LENGTH = 4300;
    static final int VP_DIRECTORY_OFFSET = 120;
    static final int VP_ITSF_HEADER_LENGTH = 96;
    static final int VP_LANGUAGE_ID = 1033;
    static final int VP_LAST_MODIFIED = 1042357880;
    static final int VP_UNKNOWN_000C = 1;
    static final int VP_UNKNOWN_LEN = 24;
    static final int VP_UNKNOWN_OFFSET = 96;
    static final int VP_VERSION = 3;
    static final int VP_BLOCK_LENGTH = 4096;
    static final int VP_BLOCK_INDEX_INTERVAL = 2;
    static final int VP_ITSP_HEADER_LENGTH = 84;
    static final int VP_INDEX_DEPTH = 1;
    static final int VP_INDEX_HEAD = 0;
    static final int VP_INDEX_ROOT = -1;
    static final int VP_UNKNOWN_NUM_BLOCKS = -1;
    static final int VP_ITSP_UNKNOWN_000C = 10;
    static final int VP_ITSP_UNKNOWN_0024 = 0;
    static final int VP_ITSP_UNKNOWN_002C = 1;
    static final int VP_ITSP_BYTEARR_LEN = 16;
    static final int VP_ITSP_VERSION = 1;
    static final int VP_RESET_INTERVAL = 2;
    static final int VP_CONTROL_DATA_SIZE = 6;
    static final int VP_UNKNOWN_18 = 0;
    static final int VP_CONTROL_DATA_VERSION = 2;
    static final int VP_WINDOW_SIZE = 65536;
    static final int VP_WINDOWS_PER_RESET = 1;
    static final int VP_CHM_ENTITIES_NUMBER = 101;
    static final int VP_PMGI_FREE_SPACE = 3;
    static final int VP_PMGL_BLOCK_NEXT = -1;
    static final int VP_PMGL_BLOCK_PREV = -1;
    static final int VP_PMGL_FREE_SPACE = 1644;
    static final int VP_PMGL_UNKNOWN_008 = 0;
    static final int VP_RESET_TABLE_BA = 12;
    static final int VP_RES_TBL_BLOCK_LENGTH = 32768;
    static final int VP_RES_TBL_COMPR_LENGTH = 177408;
    static final int VP_RES_TBL_UNCOMP_LENGTH = 383786;
    static final int VP_TBL_OFFSET = 40;
    static final int VP_RES_TBL_UNKNOWN = 8;
    static final int VP_RES_TBL_VERSION = 2;
}
