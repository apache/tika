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

public class ChmConstants {
    /* Prevents instantiation */
    private ChmConstants() {
    }

    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String ITSF = "ITSF";
    public static final String ITSP = "ITSP";
    public static final String PMGL = "PMGL";
    public static final String LZXC = "LZXC";
    public static final String CHM_PMGI_MARKER = "PMGI";
    public static final int BYTE_ARRAY_LENGHT = 16;
    public static final int CHM_ITSF_V2_LEN = 0x58;
    public static final int CHM_ITSF_V3_LEN = 0x60;
    public static final int CHM_ITSP_V1_LEN = 0x54;
    public static final int CHM_PMGL_LEN = 0x14;
    public static final int CHM_PMGI_LEN = 0x08;
    public static final int CHM_LZXC_RESETTABLE_V1_LEN = 0x28;
    public static final int CHM_LZXC_MIN_LEN = 0x18;
    public static final int CHM_LZXC_V2_LEN = 0x1c;
    public static final int CHM_SIGNATURE_LEN = 4;
    public static final int CHM_VER_2 = 2;
    public static final int CHM_VER_3 = 3;
    public static final int CHM_VER_1 = 1;
    public static final int CHM_WINDOW_SIZE_BLOCK = 0x8000;

    /* my hacking */
    public static final int START_PMGL = 0xCC;
    public static final String CONTROL_DATA = "ControlData";
    public static final String RESET_TABLE = "ResetTable";
    public static final String CONTENT = "Content";

    /* some constants defined by the LZX specification */
    public static final int LZX_MIN_MATCH = 2;
    public static final int LZX_MAX_MATCH = 257;
    public static final int LZX_NUM_CHARS = 256;
    public static final int LZX_BLOCKTYPE_INVALID = 0; /*
                                                        * also blocktypes 4-7
                                                        * invalid
                                                        */
    public static final int LZX_BLOCKTYPE_VERBATIM = 1;
    public static final int LZX_BLOCKTYPE_ALIGNED = 2;
    public static final int LZX_BLOCKTYPE_UNCOMPRESSED = 3;
    public static final int LZX_PRETREE_NUM_ELEMENTS_BITS = 4; /* ??? */
    public static final int LZX_PRETREE_NUM_ELEMENTS = 20;
    public static final int LZX_ALIGNED_NUM_ELEMENTS = 8; /*
                                                           * aligned offset tree
                                                           * #elements
                                                           */
    public static final int LZX_NUM_PRIMARY_LENGTHS = 7; /*
                                                          * this one missing
                                                          * from spec!
                                                          */
    public static final int LZX_NUM_SECONDARY_LENGTHS = 249; /*
                                                              * length tree
                                                              * #elements
                                                              */

    /* LZX huffman defines: tweak tablebits as desired */
    public static final int LZX_PRETREE_MAXSYMBOLS = LZX_PRETREE_NUM_ELEMENTS;
    public static final int LZX_PRETREE_TABLEBITS = 6;
    public static final int LZX_MAINTREE_MAXSYMBOLS = LZX_NUM_CHARS + 50 * 8;
    public static final int LZX_MAIN_MAXSYMBOLS = LZX_NUM_CHARS * 2;
    public static final int LZX_MAINTREE_TABLEBITS = 12;
    public static final int LZX_LENGTH_MAXSYMBOLS = LZX_NUM_SECONDARY_LENGTHS + 1;
    public static final int LZX_LENGTH_TABLEBITS = 12;
    public static final int LZX_ALIGNED_MAXSYMBOLS = LZX_ALIGNED_NUM_ELEMENTS;
    public static final int LZX_ALIGNED_TABLEBITS = 7;
    public static final int LZX_LENTABLE_SAFETY = 64;

    public static short[] EXTRA_BITS = { 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5,
            5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14,
            15, 15, 16, 16, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17,
            17, 17 };

    public static int[] POSITION_BASE = { 0, 1, 2, 3, 4, 6, 8, 12, 16, 24, 32,
            48, 64, 96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048, 3072,
            4096, 6144, 8192, 12288, 16384, 24576, 32768, 49152, 65536, 98304,
            131072, 196608, 262144, 393216, 524288, 655360, 786432, 917504,
            1048576, 1179648, 1310720, 1441792, 1572864, 1703936, 1835008,
            1966080, 2097152 };
}
