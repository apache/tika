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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internet media type.
 */
public final class MediaType {

    private static final Map<String, String> NO_PARAMETERS =
        new TreeMap<String, String>();

    private static final Pattern SPECIAL =
        Pattern.compile("[\\(\\)<>@,;:\\\\\"/\\[\\]\\?=]");

    private static final Pattern SPECIAL_OR_WHITESPACE =
        Pattern.compile("[\\(\\)<>@,;:\\\\\"/\\[\\]\\?=\\s]");

    // TIKA-350: handle charset as first element in content-type
    // See http://www.ietf.org/rfc/rfc2045.txt for valid mime-type characters.
    private static final String VALID_MIMETYPE_CHARS = "[^\\c\\(\\)<>@,;:\\\\\"/\\[\\]\\?=\\s]";
    private static final String MIME_TYPE_PATTERN_STRING = "(" + VALID_MIMETYPE_CHARS + "+)"
                    + "\\s*/\\s*" + "(" + VALID_MIMETYPE_CHARS + "+)";
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(
                    "(?is)\\s*" + MIME_TYPE_PATTERN_STRING + "\\s*($|;.*)");
    private static final Pattern CONTENT_TYPE_CHARSET_FIRST_PATTERN = Pattern.compile(
                    "(?i)\\s*(charset\\s*=\\s*[^\\c;\\s]+)\\s*;\\s*" + MIME_TYPE_PATTERN_STRING);

    public static final MediaType OCTET_STREAM = application("octet-stream");

    public static final MediaType TEXT_PLAIN = text("plain");

    public static final MediaType APPLICATION_XML = application("xml");

    public static MediaType application(String type) {
        return new MediaType("application", type);
    }

    public static MediaType audio(String type) {
        return new MediaType("audio", type);
    }

    public static MediaType image(String type) {
        return new MediaType("image", type);
    }

    public static MediaType text(String type) {
        return new MediaType("text", type);
    }

    public static MediaType video(String type) {
        return new MediaType("video", type);
    }

    /**
     * Parses the given string to a media type. The string is expected to be of
     * the form "type/subtype(; parameter=...)*" as defined in RFC 2045, though
     * we also handle "charset=xxx; type/subtype" for broken web servers.
     * 
     * @param string
     *            media type string to be parsed
     * @return parsed media type, or <code>null</code> if parsing fails
     */
    public static MediaType parse(String string) {
        if (string == null) {
            return null;
        }

        String type;
        String subtype;
        String params;
        
        Matcher m = CONTENT_TYPE_PATTERN.matcher(string);
        if (m.matches()) {
            type = m.group(1);
            subtype = m.group(2);
            params = m.group(3);
        } else {
            m = CONTENT_TYPE_CHARSET_FIRST_PATTERN.matcher(string);
            if (m.matches()) {
                params = m.group(1);
                type = m.group(2);
                subtype = m.group(3);
            } else {
                return null;
            }
        }
        
        MediaType result = new MediaType(type, subtype);
        String[] paramPieces = params.split(";");
        for (String paramPiece : paramPieces) {
            String[] keyValue = paramPiece.split("=");
            if (keyValue.length != 2) {
                continue;
            }
            
            String key = keyValue[0].trim();
            if (key.length() > 0) {
                result.parameters.put(key, keyValue[1].trim());
            }
        }
        
        return result;
    }

    private final String type;

    private final String subtype;

    private final SortedMap<String, String> parameters;

    public MediaType(
            String type, String subtype, Map<String, String> parameters) {
        this.type = type.trim().toLowerCase();
        this.subtype = subtype.trim().toLowerCase();
        this.parameters = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            this.parameters.put(
                    entry.getKey().trim().toLowerCase(), entry.getValue());
        }
    }

    public MediaType(String type, String subtype) {
        this(type, subtype, NO_PARAMETERS);
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
        this(type.type, type.subtype, union(type.parameters, parameters));
    }

    public MediaType getBaseType() {
        if (parameters.isEmpty()) {
            return this;
        } else {
            return new MediaType(type, subtype);
        }
    }

    public String getType() {
        return type;
    }

    public String getSubtype() {
        return subtype;
    }

    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public boolean isSpecializationOf(MediaType that) {
        if (OCTET_STREAM.equals(that)) {
            return true;
        } else if (!type.equals(that.type)) {
            return false;
        } else if (!parameters.entrySet().containsAll(that.parameters.entrySet())) {
            return false;
        } else if (TEXT_PLAIN.equals(that.getBaseType())) {
            return true;
        } else if (APPLICATION_XML.equals(that.getBaseType())
                && subtype.endsWith("+xml")) {
            return true;
        } else {
            return subtype.equals(that.subtype);
        }
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(type);
        builder.append('/');
        builder.append(subtype);
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
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
        return builder.toString();
    }

    public boolean equals(Object object) {
        if (object instanceof MediaType) {
            MediaType that = (MediaType) object;
            return type.equals(that.type)
                && subtype.equals(that.subtype)
                && parameters.equals(that.parameters);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + type.hashCode();
        hash = hash * 31 + subtype.hashCode();
        hash = hash * 31 + parameters.hashCode();
        return hash;
    }

}
