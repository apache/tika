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
package org.apache.tika.parser.txt;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.CharsetUtils;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.UniversalDetector;

/**
 * Helper class used by {@link UniversalEncodingDetector} to access the
 * <code>juniversalchardet</code> detection logic.
 */
class UniversalEncodingListener implements CharsetListener {

    private static final String CHARSET_ISO_8859_1 = "ISO-8859-1";

    private static final String CHARSET_ISO_8859_15 = "ISO-8859-15";

    private static Map<String, Charset> makeCC(String... names) {
        Map<String, Charset> charsets = new HashMap<String, Charset>();
        for (String name : names) {
            try {
                charsets.put(name, Charset.forName(name));
            } catch (Exception e) {
                // ignore
            }
        }
        return charsets;
    }

    private static final Map<String, Charset> CONSTANT_CHARSETS = makeCC(
            Constants.CHARSET_BIG5,
            Constants.CHARSET_EUC_JP,
            Constants.CHARSET_EUC_KR,
            Constants.CHARSET_EUC_TW,
            Constants.CHARSET_GB18030,
            Constants.CHARSET_HZ_GB_2312, // not supported?
            Constants.CHARSET_IBM855,
            Constants.CHARSET_IBM866,
            Constants.CHARSET_ISO_2022_CN,
            Constants.CHARSET_ISO_2022_JP,
            Constants.CHARSET_ISO_2022_KR,
            CHARSET_ISO_8859_1,
            Constants.CHARSET_ISO_8859_5,
            Constants.CHARSET_ISO_8859_7,
            Constants.CHARSET_ISO_8859_8,
            CHARSET_ISO_8859_15,
            Constants.CHARSET_KOI8_R,
            Constants.CHARSET_MACCYRILLIC,
            Constants.CHARSET_SHIFT_JIS,
            Constants.CHARSET_UTF_16BE,
            Constants.CHARSET_UTF_16LE,
            Constants.CHARSET_UTF_32BE, // not supported?
            Constants.CHARSET_UTF_32LE, // not supported?
            Constants.CHARSET_UTF_8,
            Constants.CHARSET_WINDOWS_1251,
            Constants.CHARSET_WINDOWS_1252,
            Constants.CHARSET_WINDOWS_1253,
            Constants.CHARSET_WINDOWS_1255,
            Constants.CHARSET_X_ISO_10646_UCS_4_2143, // not supported?
            Constants.CHARSET_X_ISO_10646_UCS_4_3412); // not supported?

    private static Charset getCharset(String name) {
        Charset charset = CONSTANT_CHARSETS.get(name);
        if (charset == null) {
            try {
                charset = CharsetUtils.forName(name);
            } catch (Exception e) {
                // ignore
            }
        }
        return charset;
    }

    private final UniversalDetector detector = new UniversalDetector(this);

    private String hint = null;

    private Charset charset = null;

    private boolean hasCR = false;

    public UniversalEncodingListener(Metadata metadata) {
        MediaType type = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        if (type != null) {
            hint = type.getParameters().get("charset");
        }
        if (hint == null) {
            hint = metadata.get(Metadata.CONTENT_ENCODING);
        }
    }

    public void report(String name) {
        if (Constants.CHARSET_WINDOWS_1252.equals(name)) {
            if (hint != null) {
                // Use the encoding hint to distinguish between latin charsets
                name = hint;
            } else if (!hasCR) {
                // If there are no CRLFs, it's more likely to be ISO-8859-1
                name = CHARSET_ISO_8859_1;
            }
        }
        this.charset = getCharset(name);
    }

    public boolean isDone() {
        return detector.isDone();
    }

    public void handleData(byte[] buf, int offset, int length) {
        for (int i = 0; !hasCR && i < length; i++) {
            if (buf[offset + i] == '\r') {
                hasCR = true;
            }
        }
        detector.handleData(buf, offset, length);
    }

    public Charset dataEnd() {
        detector.dataEnd();
        return charset;
    }

}