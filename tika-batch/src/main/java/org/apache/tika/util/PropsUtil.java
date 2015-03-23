package org.apache.tika.util;

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

import java.io.File;
import java.util.Locale;

/**
 * Utility class to handle properties.  If the value is null,
 * or if there is a parser error, the defaultMissing value will be returned.
 */
public class PropsUtil {

    /**
     * Parses v.  If there is a problem, this returns defaultMissing.
     *
     * @param v string to parse
     * @param defaultMissing value to return if value is null or unparseable
     * @return parsed value
     */
    public static Boolean getBoolean(String v, Boolean defaultMissing) {
        if (v == null || v.length() == 0) {
            return defaultMissing;
        }
        if (v.toLowerCase(Locale.ROOT).equals("true")) {
            return true;
        }
        if (v.toLowerCase(Locale.ROOT).equals("false")) {
            return false;
        }
        return defaultMissing;
    }

    /**
     * Parses v.  If there is a problem, this returns defaultMissing.
     *
     * @param v string to parse
     * @param defaultMissing value to return if value is null or unparseable
     * @return parsed value
     */
    public static Integer getInt(String v, Integer defaultMissing) {
        if (v == null || v.length() == 0) {
            return defaultMissing;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            //NO OP
        }
        return defaultMissing;
    }

    /**
     * Parses v.  If there is a problem, this returns defaultMissing.
     *
     * @param v string to parse
     * @param defaultMissing value to return if value is null or unparseable
     * @return parsed value
     */
    public static Long getLong(String v, Long defaultMissing) {
        if (v == null || v.length() == 0) {
            return defaultMissing;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            //swallow
        }
        return defaultMissing;
    }


    /**
     * Parses v.  If there is a problem, this returns defaultMissing.
     *
     * @param v string to parse
     * @param defaultMissing value to return if value is null or unparseable
     * @return parsed value
     */
    public static File getFile(String v, File defaultMissing) {
        if (v == null || v.length() == 0) {
            return defaultMissing;
        }
        //trim initial and final " if they exist
        if (v.startsWith("\"")) {
            v = v.substring(1);
        }
        if (v.endsWith("\"")) {
            v = v.substring(0, v.length()-1);
        }

        return new File(v);
    }

    /**
     * Parses v.  If v is null, this returns defaultMissing.
     *
     * @param v string to parse
     * @param defaultMissing value to return if value is null
     * @return parsed value
     */
    public static String getString(String v, String defaultMissing) {
        if (v == null) {
            return defaultMissing;
        }
        return v;
    }
}
