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
        assertEquals("x-Big5-HKSCS-WHATWG", name("Big5"));
        assertEquals("x-Big5-HKSCS-WHATWG", name("Big5-HKSCS"));
        assertEquals("GB18030", name("GB2312"));
        assertEquals("GB18030", name("GBK"));
        assertEquals("windows-31j", name("Shift_JIS"));
        assertEquals("x-eucJP-Open-WHATWG", name("EUC-JP"));
        assertEquals("x-eucJP-Open-WHATWG", name("x-eucJP-Open"));
        assertEquals("x-windows-50221", name("ISO-2022-JP"));
    }

    @Test
    public void returnsNullWhenNoSuperset() {
        assertNull(CharsetSupersets.supersetOf(null));
        assertNull(CharsetSupersets.supersetOf(StandardCharsets.UTF_8));
        assertNull(CharsetSupersets.supersetOf(Charset.forName("windows-1252")));
        // Superset targets have no further superset.
        assertNull(CharsetSupersets.supersetOf(Charset.forName("GB18030")));
        assertNull(CharsetSupersets.supersetOf(CharsetSupersets.decodeAs(Charset.forName("Big5"))));
    }

    @Test
    public void jdkDecodeAsLeavesBrowserPatchOff() {
        assertEquals("Big5-HKSCS", CharsetSupersets.jdkDecodeAs(Charset.forName("Big5")).name());
        assertEquals("Big5-HKSCS",
                CharsetSupersets.jdkDecodeAs(Charset.forName("Big5-HKSCS")).name());
        assertEquals("windows-31j",
                CharsetSupersets.jdkDecodeAs(Charset.forName("Shift_JIS")).name());
        assertEquals(StandardCharsets.UTF_8, CharsetSupersets.jdkDecodeAs(StandardCharsets.UTF_8));
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
        assertEquals("\u2460", new String(eucjp,
                CharsetSupersets.supersetOf(Charset.forName("EUC-JP"))));

        byte[] jis = "\u65e5\u2460".getBytes(Charset.forName("x-windows-50221"));
        assertEquals('\uFFFD', new String(jis, Charset.forName("ISO-2022-JP")).charAt(1));
        assertEquals("\u65e5\u2460", new String(jis, Charset.forName(name("ISO-2022-JP"))));
    }

    @Test
    public void singleByteDecodeAsBrowserCodePage() {
        assertEquals("windows-1252", name("ISO-8859-1"));
        assertEquals("windows-1254", name("ISO-8859-9"));
        assertEquals("x-windows-874", name("TIS-620"));
        assertEquals("x-windows-874", name("ISO-8859-11"));
        // 0x92 is a C1 control under ISO-8859-1, a right single quote on real pages
        assertEquals("\u2019", new String(new byte[]{(byte) 0x92}, Charset.forName(name("ISO-8859-1"))));
        for (String narrow : new String[]{"ISO-8859-1", "ISO-8859-9", "TIS-620", "ISO-8859-11"}) {
            Charset n = Charset.forName(narrow);
            Charset w = Charset.forName(name(narrow));
            for (int b = 0; b < 256; b++) {
                if (b >= 0x80 && b < 0xA0) {
                    continue;
                }
                byte[] one = {(byte) b};
                String expected = new String(one, n);
                if (expected.charAt(0) != '\uFFFD') {
                    assertEquals(expected, new String(one, w), narrow + " byte " + b);
                }
            }
        }
    }
}
