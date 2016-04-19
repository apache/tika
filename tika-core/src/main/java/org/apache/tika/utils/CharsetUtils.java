/**
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
package org.apache.tika.utils;

import static java.util.Locale.ENGLISH;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CharsetUtils {

    private static final Pattern CHARSET_NAME_PATTERN =
            Pattern.compile("[ \\\"]*([^ >,;\\\"]+).*");

    private static final Pattern ISO_NAME_PATTERN =
            Pattern.compile(".*8859-(\\d+)");

    private static final Pattern CP_NAME_PATTERN =
            Pattern.compile("cp-(\\d+)");

    private static final Pattern WIN_NAME_PATTERN =
            Pattern.compile("win-?(\\d+)");

    private static final Map<String, Charset> COMMON_CHARSETS =
            new HashMap<String, Charset>();

    private static Method getCharsetICU = null;
    private static Method isSupportedICU = null;

    private static Map<String, Charset> initCommonCharsets(String... names) {
        Map<String, Charset> charsets = new HashMap<String, Charset>();
        for (String name : names) {
            try {
                Charset charset = Charset.forName(name);
                COMMON_CHARSETS.put(name.toLowerCase(ENGLISH), charset);
                for (String alias : charset.aliases()) {
                    COMMON_CHARSETS.put(alias.toLowerCase(ENGLISH), charset);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return charsets;
    }

    static {
        initCommonCharsets(
                "Big5",
                "EUC-JP", "EUC-KR", "x-EUC-TW",
                "GB18030",
                "IBM855", "IBM866",
                "ISO-2022-CN", "ISO-2022-JP", "ISO-2022-KR",
                "ISO-8859-1", "ISO-8859-2", "ISO-8859-3", "ISO-8859-4",
                "ISO-8859-5", "ISO-8859-6", "ISO-8859-7", "ISO-8859-8",
                "ISO-8859-9", "ISO-8859-11", "ISO-8859-13", "ISO-8859-15",
                "KOI8-R",
                "x-MacCyrillic",
                "SHIFT_JIS",
                "UTF-8", "UTF-16BE", "UTF-16LE",
                "windows-1251", "windows-1252", "windows-1253", "windows-1255");

        // Common aliases/typos not included in standard charset definitions
        COMMON_CHARSETS.put("iso-8851-1", COMMON_CHARSETS.get("iso-8859-1"));
        COMMON_CHARSETS.put("windows", COMMON_CHARSETS.get("windows-1252"));
        COMMON_CHARSETS.put("koi8r", COMMON_CHARSETS.get("koi8-r"));

        // See if we can load the icu4j CharsetICU class
        Class<?> icuCharset = null;
        try  {
            icuCharset = CharsetUtils.class.getClassLoader().loadClass(
                    "com.ibm.icu.charset.CharsetICU");
        }  catch (ClassNotFoundException e) {
        }
        if (icuCharset != null) {
            try {
                getCharsetICU = icuCharset.getMethod("forNameICU", String.class);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            try {
                isSupportedICU = icuCharset.getMethod("isSupported", String.class);
            } catch (Throwable t) {
            }
            // TODO: would be nice to somehow log that we
            // successfully found ICU
        }
    }

    /**
     * Safely return whether <charsetName> is supported, without throwing exceptions
     * 
     * @param charsetName Name of charset (can be null)
     * @return true if the character set is supported
     */
    public static boolean isSupported(String charsetName) {
        try {
            if (isSupportedICU != null && ((Boolean) isSupportedICU.invoke(null, charsetName)).booleanValue()) {
                return true;
            }
            return Charset.isSupported(charsetName);
        } catch (IllegalCharsetNameException e) {
            return false;
        } catch (IllegalArgumentException e) {
            // null, for example
            return false;
        } catch (Exception e) {
            // Unexpected exception, what to do?
            return false;
        }
    }

    /**
     * Handle various common charset name errors, and return something
     * that will be considered valid (and is normalized)
     * 
     * @param charsetName name of charset to process
     * @return potentially remapped/cleaned up version of charset name
     */
    public static String clean(String charsetName) {
        try {
            return forName(charsetName).name();
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns Charset impl, if one exists.  This method
     *  optionally uses ICU4J's CharsetICU.forNameICU,
     *  if it is found on the classpath, else only uses
     *  JDK's builtin Charset.forName. */
    public static Charset forName(String name) {
        if (name == null) {
            throw new IllegalArgumentException();
        }

        // Get rid of cruft around names, like <>, trailing commas, etc.
        Matcher m = CHARSET_NAME_PATTERN.matcher(name);
        if (!m.matches()) {
            throw new IllegalCharsetNameException(name);
        }
        name = m.group(1);

        String lower = name.toLowerCase(Locale.ENGLISH);
        Charset charset = COMMON_CHARSETS.get(lower);
        if (charset != null) {
            return charset;
        } else if ("none".equals(lower) || "no".equals(lower)) {
            throw new IllegalCharsetNameException(name);
        } else {
            Matcher iso = ISO_NAME_PATTERN.matcher(lower);
            Matcher cp = CP_NAME_PATTERN.matcher(lower);
            Matcher win = WIN_NAME_PATTERN.matcher(lower);
            if (iso.matches()) {
                // Handle "iso 8859-x" error
                name = "iso-8859-" + iso.group(1);
                charset = COMMON_CHARSETS.get(name);
            } else if (cp.matches()) {
                // Handle "cp-xxx" error
                name = "cp" + cp.group(1);
                charset = COMMON_CHARSETS.get(name);
            } else if (win.matches()) {
                // Handle "winxxx" and "win-xxx" errors
                name = "windows-" + win.group(1);
                charset = COMMON_CHARSETS.get(name);
            }
            if (charset != null) {
                return charset;
            }
        }

        if (getCharsetICU != null) {
            try {
                Charset cs = (Charset) getCharsetICU.invoke(null, name);
                if (cs != null) {
                    return cs;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return Charset.forName(name);
    }
}
