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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Defines a MimeType pattern.
 */
class Patterns {

    /**
     * Index of exact name patterns.
     */
    private final Map<String, MimeType> names = new HashMap<String, MimeType>();

    /**
     * Index of extension patterns of the form "*extension".
     */
    private final Map<String, MimeType> extensions =
        new HashMap<String, MimeType>();

    private int minExtensionLength = Integer.MAX_VALUE;

    private int maxExtensionLength = 0;

    /**
     * Index of generic glob patterns, sorted by length.
     */
    private final SortedMap<String, MimeType> globs =
        new TreeMap<String, MimeType>(new Comparator<String>() {
            public int compare(String a, String b) {
                int diff = b.length() - a.length();
                if (diff == 0) {
                    diff = a.compareTo(b);
                }
                return diff;
            }
        });


    public void add(String pattern, MimeType type) throws MimeTypeException {
        this.add(pattern, false, type);
    }
   
    public void add(String pattern, boolean isJavaRegex, MimeType type)
            throws MimeTypeException {
        if (pattern == null || type == null) {
            throw new IllegalArgumentException(
                    "Pattern and/or mime type is missing");
        }
        
        if (isJavaRegex) {
            // in this case, we don't need to build a regex pattern
            // it's already there for us, so just add the pattern as is
            addGlob(pattern, type);
        } else {

            if (pattern.indexOf('*') == -1 && pattern.indexOf('?') == -1
                    && pattern.indexOf('[') == -1) {
                addName(pattern, type);
            } else if (pattern.startsWith("*") && pattern.indexOf('*', 1) == -1
                    && pattern.indexOf('?') == -1 && pattern.indexOf('[') == -1) {
                addExtension(pattern.substring(1), type);
            } else {
                addGlob(compile(pattern), type);
            }
        }
    }
    
    private void addName(String name, MimeType type) throws MimeTypeException {
        MimeType previous = names.get(name);
        if (previous == null || previous.isDescendantOf(type)) {
            names.put(name, type);
        } else if (previous == type || type.isDescendantOf(previous)) {
            // do nothing
        } else {
            throw new MimeTypeException("Conflicting name pattern: " + name);
        }
    }

    private void addExtension(String extension, MimeType type)
            throws MimeTypeException {
        MimeType previous = extensions.get(extension);
        if (previous == null || previous.isDescendantOf(type)) {
            extensions.put(extension, type);
            int length = extension.length();
            minExtensionLength = Math.min(minExtensionLength, length);
            maxExtensionLength = Math.max(maxExtensionLength, length);
        } else if (previous == type || type.isDescendantOf(previous)) {
            // do nothing
        } else {
            throw new MimeTypeException(
                    "Conflicting extension pattern: " + extension);
        }
    }

    private void addGlob(String glob, MimeType type)
            throws MimeTypeException {
        MimeType previous = globs.get(glob);
        if (previous == null || previous.isDescendantOf(type)) {
            globs.put(glob, type);
        } else if (previous == type || type.isDescendantOf(previous)) {
            // do nothing
        } else {
            throw new MimeTypeException("Conflicting glob pattern: " + glob);
        }
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
    public MimeType matches(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is missing");
        }

        // First, try exact match of the provided resource name
        if (names.containsKey(name)) {
            return names.get(name);
        }

        // Then try "extension" (*.xxx) matching
        int maxLength = Math.min(maxExtensionLength, name.length());
        for (int n = maxLength; n >= minExtensionLength; n--) {
            String extension = name.substring(name.length() - n);
            if (extensions.containsKey(extension)) {
                return extensions.get(extension);
            }
        }

        // And finally, try complex regexp matching
        for (Map.Entry<String, MimeType> entry : globs.entrySet()) {
            if (name.matches(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String compile(String glob) {
        StringBuilder pattern = new StringBuilder();
        pattern.append("\\A");
        for (int i = 0; i < glob.length(); i++) {
            char ch = glob.charAt(i);
            if (ch == '?') {
                pattern.append('.');
            } else if (ch == '*') {
                pattern.append(".*");
            } else if ("\\[]^.-$+(){}|".indexOf(ch) != -1) {
                pattern.append('\\');
                pattern.append(ch);
            } else {
                pattern.append(ch);
            }
        }
        pattern.append("\\z");
        return pattern.toString();
    }

}
