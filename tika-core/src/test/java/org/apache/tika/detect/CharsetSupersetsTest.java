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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class CharsetSupersetsTest {

    private static String name(String detected) {
        Charset s = CharsetSupersets.supersetOf(Charset.forName(detected));
        return s == null ? null : s.name();
    }

    @Test
    public void mapsLegacyCjkToVendorSupersets() {
        assertEquals("x-windows-949", name("EUC-KR"));
        assertEquals("Big5-HKSCS", name("Big5"));
        assertEquals("GB18030", name("GB2312"));
        assertEquals("GB18030", name("GBK"));
        assertEquals("windows-31j", name("Shift_JIS"));
        assertEquals("x-eucJP-Open", name("EUC-JP"));
    }

    @Test
    public void returnsNullWhenNoSuperset() {
        assertNull(CharsetSupersets.supersetOf(null));
        assertNull(CharsetSupersets.supersetOf(StandardCharsets.UTF_8));
        assertNull(CharsetSupersets.supersetOf(Charset.forName("windows-1252")));
        // Superset targets have no further superset.
        assertNull(CharsetSupersets.supersetOf(Charset.forName("GB18030")));
    }

    /** The point of the map: vendor-extension bytes the strict base drops to
     *  U+FFFD decode correctly under the superset. */
    @Test
    public void supersetRecoversVendorExtensionChars() {
        // CP932/EUC-JP NEC special U+2460 (circled one): strict base fails to
        // U+FFFD, superset maps it.
        byte[] sjis = {(byte) 0x87, (byte) 0x40};
        assertEquals('\uFFFD', new String(sjis, Charset.forName("Shift_JIS")).charAt(0));
        assertEquals("\u2460", new String(sjis, Charset.forName(name("Shift_JIS"))));

        byte[] eucjp = {(byte) 0xAD, (byte) 0xA1};
        assertEquals('\uFFFD', new String(eucjp, Charset.forName("EUC-JP")).charAt(0));
        assertEquals("\u2460", new String(eucjp, Charset.forName(name("EUC-JP"))));
    }
}
