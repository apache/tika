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
package org.apache.tika.mime;

// JDK imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines a MimeType pattern.
 */
class Patterns {

    private static Map<Character, String> escapeMap =
        new HashMap<Character, String>();

    static {
        escapeMap.put('\\', "\\\\");
        escapeMap.put('?', "\\?");
        escapeMap.put('[', "\\[");
        escapeMap.put(']', "\\]");
        escapeMap.put('^', "\\^");
        escapeMap.put('.', "\\.");
        escapeMap.put('-', "\\-");
        escapeMap.put('$', "\\$");
        escapeMap.put('+', "\\+");
        escapeMap.put('(', "\\(");
        escapeMap.put(')', "\\)");
        escapeMap.put('{', "\\{");
        escapeMap.put('}', "\\}");
        escapeMap.put('|', "\\|");
        escapeMap.put('*', ".*");
    }

    /** Gathers all the patterns */
    private ArrayList<String> patterns = new ArrayList<String>();

    /** An index of exact matching patterns */
    private Map<String, MimeType> exactIdx = new HashMap<String, MimeType>();

    /** An index of the patterns of the form "*.ext" */
    private Map<String, MimeType> extIdx = new HashMap<String, MimeType>();

    /** A list of other patterns */
    private Map<String, MimeType> others = new HashMap<String, MimeType>();

    void add(String[] patterns, MimeType type) {
        // Some preliminary checks
        if ((patterns == null) || (type == null)) {
            return;
        }
        // All is ok, so add the patterns
        for (String pattern : patterns) {
            add(pattern, type);
        }
    }

    void add(String pattern, MimeType type) {
        // Some preliminary checks
        if ((pattern == null) || (type == null)) {
            return;
        }

        // Add the pattern in the good index
        if ((pattern.indexOf('*') == -1) && (pattern.indexOf('?') == -1)
                && (pattern.indexOf('[') == -1)) {
            exactIdx.put(pattern, type);
        } else if (pattern.startsWith("*.")) {
            extIdx.put(pattern.substring(2), type);
        } else {
            others.put(escape(pattern), type);
        }
        // Add the pattern in the list of patterns
        patterns.add(pattern);
    }

    String[] getPatterns() {
        return patterns.toArray(new String[patterns.size()]);
    }

    /**
     * Find the MimeType corresponding to a resource name.
     * 
     * It applies the recommendations detailed in FreeDesktop Shared MIME-info
     * Database for guessing MimeType from a resource name: It first tries a
     * case-sensitive match, then try again with the resource name converted to
     * lower-case if that fails. If several patterns match then the longest
     * pattern is used. In particular, files with multiple extensions (such as
     * Data.tar.gz) match the longest sequence of extensions (eg '*.tar.gz' in
     * preference to '*.gz'). Literal patterns (eg, 'Makefile') are matched
     * before all others. Patterns beginning with `*.' and containing no other
     * special characters (`*?[') are matched before other wildcarded patterns
     * (since this covers the majority of the patterns).
     */
    MimeType matches(String resourceName) {

        // Preliminary check...
        if (resourceName == null) {
            return null;
        }

        // First, try exact match of the provided resource name
        MimeType type = exactIdx.get(resourceName);
        if (type != null) {
            return type;
        }

        // Then try exact match with only the resource name
        String str = last(resourceName, '/');
        if (str != null) {
            type = exactIdx.get(str);
            if (type != null) {
                return type;
            }
        }
        str = last(resourceName, '\\');
        if (str != null) {
            type = exactIdx.get(str);
            if (type != null) {
                return type;
            }
        }

        // Then try "extension" (*.xxx) matching
        int idx = resourceName.indexOf('.', 0);
        while (idx != -1) {
            type = extIdx.get(resourceName.substring(idx + 1));
            if (type != null) {
                return type;
            }
            idx = resourceName.indexOf('.', idx + 1);
        }

        // And finally, try complex regexp matching
        String longest = null;
        for (String pattern : others.keySet()) {
            if ((resourceName.matches(pattern))
                    && (pattern.length() > longest.length())) {
                longest = pattern;
            }
        }
        if (longest != null) {
            type = others.get(longest);
        }
        return type;
    }

    private final static String last(String str, char c) {
        if (str == null) {
            return null;
        }
        int idx = str.lastIndexOf(c);
        if ((idx < 0) || (idx >= (str.length() - 1))) {
            return null;
        }
        return str.substring(idx + 1);
    }

    private final static String escape(String str) {
        StringBuffer result = new StringBuffer(str.length());
        for (int i = 0; i < str.length(); i++) {
            String charAt = String.valueOf(str.charAt(i));
            String replace = escapeMap.get(charAt);
            result.append((replace != null) ? replace : charAt);
        }
        return result.toString();
    }

}
