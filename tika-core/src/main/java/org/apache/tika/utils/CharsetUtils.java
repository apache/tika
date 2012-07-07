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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CharsetUtils {
    private static final Pattern CHARSET_NAME_PATTERN = Pattern.compile("[ \\\"]*([^ >,;\\\"]+).*");
    private static final Pattern ISO_NAME_PATTERN = Pattern.compile("(?i).*8859-([\\d]+)");
    private static final Pattern CP_NAME_PATTERN = Pattern.compile("(?i)cp-([\\d]+)");
    private static final Pattern WIN_NAME_PATTERN = Pattern.compile("(?i)win(|-)([\\d]+)");
    
    // List of common invalid charset names that we can't fix using
    // pattern matching + heuristic
    private static final Map<String, String> CHARSET_ALIASES =
            new HashMap<String, String>();

    private static final Map<String, Charset> STANDARD_CHARSETS =
            new HashMap<String, Charset>();

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
        if (charsetName == null) {
            return null;
        }
        
        // Get rid of cruft around names, like <>, trailing commas, etc.
        Matcher m = CHARSET_NAME_PATTERN.matcher(charsetName);
        if (!m.matches()) {
            return null;
        }

        String result = m.group(1);
        if (CHARSET_ALIASES.containsKey(result.toLowerCase())) {
            // Handle common erroneous charset names.
            result = CHARSET_ALIASES.get(result.toLowerCase());
        } else if (ISO_NAME_PATTERN.matcher(result).matches()) {
            // Handle "iso 8859-x" error
            m = ISO_NAME_PATTERN.matcher(result);
            m.matches();
            result = "iso-8859-" + m.group(1);
        } else if (CP_NAME_PATTERN.matcher(result).matches()) {
            // Handle "cp-xxx" error
            m = CP_NAME_PATTERN.matcher(result);
            m.matches();
            result = "cp" + m.group(1);
        } else if (WIN_NAME_PATTERN.matcher(result).matches()) {
            // Handle "winxxx" and "win-xxx" errors
            m = WIN_NAME_PATTERN.matcher(result);
            m.matches();
            result = "windows-" + m.group(2);
        }
        
        try {
            Charset cs = forName(result);
            return cs.name();
        } catch (Exception e) {
            return null;
        }
    }

    private static Method getCharsetICU;
    private static Method isSupportedICU;

    static {
        CHARSET_ALIASES.put("none", null);
        CHARSET_ALIASES.put("no", null);
        CHARSET_ALIASES.put("iso-8851-1", "iso-8859-1");
        CHARSET_ALIASES.put("windows", "windows-1252");
        CHARSET_ALIASES.put("koi8r", "KOI8-R");

        STANDARD_CHARSETS.put("US-ASCII", Charset.forName("US-ASCII"));
        STANDARD_CHARSETS.put("ISO-8859-1", Charset.forName("ISO-8859-1"));
        STANDARD_CHARSETS.put("UTF-8", Charset.forName("UTF-8"));
        STANDARD_CHARSETS.put("UTF-16BE", Charset.forName("UTF-16BE"));
        STANDARD_CHARSETS.put("UTF-16LE", Charset.forName("UTF-16LE"));
        STANDARD_CHARSETS.put("UTF-16", Charset.forName("UTF-16"));

        // See if we can load the icu4j CharsetICU class
        Class<?> icuCharset = null;
        try  {
            icuCharset = CharsetUtils.class.getClassLoader().loadClass("com.ibm.icu.charset.CharsetICU");
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

    /** Returns Charset impl, if one exists.  This method
     *  optionally uses ICU4J's CharsetICU.forNameICU,
     *  if it is found on the classpath, else only uses
     *  JDK's builtin Charset.forName. */
    public static Charset forName(String name) {
        Charset charset =
                STANDARD_CHARSETS.get(name.toUpperCase(Locale.ENGLISH));
        if (charset != null) {
            return charset;
        }

        if (getCharsetICU != null) {
            try {
                Charset cs = (Charset) getCharsetICU.invoke(null, name);
                if (cs != null) {
                    return cs;
                }
            } catch (InvocationTargetException ite) {
            } catch (IllegalAccessException iae) {
            }
        }

        return Charset.forName(name);
    }
}
