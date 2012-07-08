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
package org.apache.tika.mime;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internet media type.
 */
public final class MediaType implements Comparable<MediaType>, Serializable {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = -3831000556189036392L;

    private static final Pattern SPECIAL =
        Pattern.compile("[\\(\\)<>@,;:\\\\\"/\\[\\]\\?=]");

    private static final Pattern SPECIAL_OR_WHITESPACE =
        Pattern.compile("[\\(\\)<>@,;:\\\\\"/\\[\\]\\?=\\s]");

    /**
     * See http://www.ietf.org/rfc/rfc2045.txt for valid mime-type characters.
     */
    private static final String VALID_CHARS =
            "([^\\c\\(\\)<>@,;:\\\\\"/\\[\\]\\?=\\s]+)";

    private static final Pattern TYPE_PATTERN = Pattern.compile(
                    "(?s)\\s*" + VALID_CHARS + "\\s*/\\s*" + VALID_CHARS
                    + "\\s*($|;.*)");

    // TIKA-350: handle charset as first element in content-type
    private static final Pattern CHARSET_FIRST_PATTERN = Pattern.compile(
            "(?is)\\s*(charset\\s*=\\s*[^\\c;\\s]+)\\s*;\\s*"
            + VALID_CHARS + "\\s*/\\s*" + VALID_CHARS + "\\s*");

    /**
     * Set of basic types with normalized "type/subtype" names.
     * Used to optimize type lookup and to avoid having too many
     * {@link MediaType} instances in memory.
     */
    private static final Map<String, MediaType> SIMPLE_TYPES =
            new HashMap<String, MediaType>();

    public static final MediaType OCTET_STREAM =
            parse("application/octet-stream");

    public static final MediaType TEXT_PLAIN = parse("text/plain");

    public static final MediaType TEXT_HTML = parse("text/html");

    public static final MediaType APPLICATION_XML = parse("application/xml");

    public static final MediaType APPLICATION_ZIP = parse("application/zip");

    public static MediaType application(String type) {
        return MediaType.parse("application/" + type);
    }

    public static MediaType audio(String type) {
        return MediaType.parse("audio/" + type);
    }

    public static MediaType image(String type) {
        return MediaType.parse("image/" + type);
    }

    public static MediaType text(String type) {
        return MediaType.parse("text/" + type);
    }

    public static MediaType video(String type) {
        return MediaType.parse("video/" + type);
    }

    /**
     * Convenience method that returns an unmodifiable set that contains
     * all the given media types.
     *
     * @since Apache Tika 1.2
     * @param types media types
     * @return unmodifiable set of the given types
     */
    public static Set<MediaType> set(MediaType... types) {
        Set<MediaType> set = new HashSet<MediaType>();
        for (MediaType type : types) {
            if (type != null) {
                set.add(type);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * Convenience method that parses the given media type strings and
     * returns an unmodifiable set that contains all the parsed types.
     *
     * @since Apache Tika 1.2
     * @param types media type strings
     * @return unmodifiable set of the parsed types
     */
    public static Set<MediaType> set(String... types) {
        Set<MediaType> set = new HashSet<MediaType>();
        for (String type : types) {
            MediaType mt = parse(type);
            if (mt != null) {
                set.add(mt);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * Parses the given string to a media type. The string is expected
     * to be of the form "type/subtype(; parameter=...)*" as defined in
     * RFC 2045, though we also handle "charset=xxx; type/subtype" for
     * broken web servers.
     *
     * @param string media type string to be parsed
     * @return parsed media type, or <code>null</code> if parsing fails
     */
    public static MediaType parse(String string) {
        if (string == null) {
            return null;
        }

        // Optimization for the common cases
        synchronized (SIMPLE_TYPES) {
            MediaType type = SIMPLE_TYPES.get(string);
            if (type == null) {
                int slash = string.indexOf('/');
                if (slash == -1) {
                    return null;
                } else if (SIMPLE_TYPES.size() < 10000
                        && isSimpleName(string.substring(0, slash))
                        && isSimpleName(string.substring(slash + 1))) {
                    type = new MediaType(string, slash);
                    SIMPLE_TYPES.put(string, type);
                }
            }
            if (type != null) {
                return type;
            }
        }

        Matcher matcher;
        matcher = TYPE_PATTERN.matcher(string);
        if (matcher.matches()) {
            return new MediaType(
                    matcher.group(1), matcher.group(2),
                    parseParameters(matcher.group(3)));
        }
        matcher = CHARSET_FIRST_PATTERN.matcher(string);
        if (matcher.matches()) {
            return new MediaType(
                    matcher.group(2), matcher.group(3),
                    parseParameters(matcher.group(1)));
        }

        return null;
    }

    private static boolean isSimpleName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '-' && c != '+' && c != '.' && c != '_'
                    && !('0' <= c && c <= '9')
                    && !('a' <= c && c <= 'z')) {
                return false;
            }
        }
        return name.length() > 0;
    }

    private static Map<String, String> parseParameters(String string) {
        if (string.length() == 0) {
            return Collections.<String, String>emptyMap();
        }

        // Extracts k1=v1, k2=v2 from mime/type; k1=v1; k2=v2
        // Note - this logic isn't fully RFC2045 compliant yet, as it
        //  doesn't fully handle quoted keys or values (eg containing ; or =)
        Map<String, String> parameters = new HashMap<String, String>();
        while (string.length() > 0) {
            String key = string;
            String value = "";

            int semicolon = string.indexOf(';');
            if (semicolon != -1) {
                key = string.substring(0, semicolon);
                string = string.substring(semicolon + 1);
            } else {
                string = "";
            }

            int equals = key.indexOf('=');
            if (equals != -1) {
                value = key.substring(equals + 1);
                key = key.substring(0, equals);
            }

            key = key.trim();
            if (key.length() > 0) {
                parameters.put(key, unquote(value.trim()));
            }
        }
        return parameters;
    }

    /**
     * Fuzzy unquoting mechanism that works also with somewhat malformed
     * quotes.
     *
     * @param s string to unquote
     * @return unquoted string
     */
    private static String unquote(String s) {
        while (s.startsWith("\"") || s.startsWith("'")) {
            s = s.substring(1);
        }
        while (s.endsWith("\"") || s.endsWith("'")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Canonical string representation of this media type.
     */
    private final String string;

    /**
     * Location of the "/" character separating the type and the subtype
     * tokens in {@link #string}.
     */
    private final int slash;

    /**
     * Location of the first ";" character separating the type part of
     * {@link #string} from possible parameters. Length of {@link #string}
     * in case there are no parameters.
     */
    private final int semicolon;

    /**
     * Immutable sorted map of media type parameters.
     */
    private final Map<String, String> parameters;

    public MediaType(
            String type, String subtype, Map<String, String> parameters) {
        type = type.trim().toLowerCase(Locale.ENGLISH);
        subtype = subtype.trim().toLowerCase(Locale.ENGLISH);

        this.slash = type.length();
        this.semicolon = slash + 1 + subtype.length();

        if (parameters.isEmpty()) {
            this.parameters = Collections.emptyMap();
            this.string = type + '/' + subtype;
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(type);
            builder.append('/');
            builder.append(subtype);

            SortedMap<String, String> map = new TreeMap<String, String>();
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey().trim().toLowerCase(Locale.ENGLISH);
                map.put(key, entry.getValue());
            }
            for (Map.Entry<String, String> entry : map.entrySet()) {
                builder.append("; ");
                builder.append(entry.getKey());
                builder.append("=");
                String value = entry.getValue();
                if (SPECIAL_OR_WHITESPACE.matcher(value).find()) {
                    builder.append('"');
                    builder.append(SPECIAL.matcher(value).replaceAll("\\\\$0"));
                    builder.append('"');
                } else {
                    builder.append(value);
                }
            }

            this.string = builder.toString();
            this.parameters = Collections.unmodifiableSortedMap(map);
        }
    }

    public MediaType(String type, String subtype) {
        this(type, subtype, Collections.<String, String>emptyMap());
    }

    private MediaType(String string, int slash) {
        assert slash != -1;
        assert string.charAt(slash) == '/';
        assert isSimpleName(string.substring(0, slash));
        assert isSimpleName(string.substring(slash + 1));
        this.string = string;
        this.slash = slash;
        this.semicolon = string.length();
        this.parameters = Collections.emptyMap();
    }

    private static Map<String, String> union(
            Map<String, String> a, Map<String, String> b) {
        if (a.isEmpty()) {
            return b;
        } else if (b.isEmpty()) {
            return a;
        } else {
            Map<String, String> union = new HashMap<String, String>();
            union.putAll(a);
            union.putAll(b);
            return union;
        }
    }

    public MediaType(MediaType type, Map<String, String> parameters) {
        this(type.getType(), type.getSubtype(),
                union(type.parameters, parameters));
    }

    /**
     * Creates a media type by adding a parameter to a base type.
     *
     * @param type base type
     * @param name parameter name
     * @param value parameter value
     * @since Apache Tika 1.2
     */
    public MediaType(MediaType type, String name, String value) {
        this(type, Collections.singletonMap(name, value));
    }

    /**
     * Creates a media type by adding the "charset" parameter to a base type.
     *
     * @param type base type
     * @param charset charset value
     * @since Apache Tika 1.2
     */
    public MediaType(MediaType type, Charset charset) {
        this(type, "charset", charset.name());
    }
    /**
     * Returns the base form of the MediaType, excluding
     *  any parameters, such as "text/plain" for
     *  "text/plain; charset=utf-8"
     */
    public MediaType getBaseType() {
        if (parameters.isEmpty()) {
            return this;
        } else {
            return MediaType.parse(string.substring(0, semicolon));
        }
    }

    /**
     * Return the Type of the MediaType, such as
     *  "text" for "text/plain"
     */
    public String getType() {
        return string.substring(0, slash);
    }

    /**
     * Return the Sub-Type of the MediaType, 
     *  such as "plain" for "text/plain"
     */
    public String getSubtype() {
        return string.substring(slash + 1, semicolon);
    }

    /**
     * Checks whether this media type contains parameters.
     *
     * @since Apache Tika 0.8
     * @return <code>true</code> if this type has one or more parameters,
     *         <code>false</code> otherwise
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    /**
     * Returns an immutable sorted map of the parameters of this media type.
     * The parameter names are guaranteed to be trimmed and in lower case.
     *
     * @return sorted map of parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    public String toString() {
        return string;
    }

    public boolean equals(Object object) {
        if (object instanceof MediaType) {
            MediaType that = (MediaType) object;
            return string.equals(that.string);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return string.hashCode();
    }

    public int compareTo(MediaType that) {
        return string.compareTo(that.string);
    }

}
