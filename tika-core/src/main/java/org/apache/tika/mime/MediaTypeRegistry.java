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
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of known Internet media types.
 */
public class MediaTypeRegistry implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 4710974869988895410L;
    /**
     * Registry of known media types, including type aliases. A canonical
     * media type is handled as an identity mapping, while an alias is stored
     * as a mapping from the alias to the corresponding canonical type.
     */
    private final Map<MediaType, MediaType> registry = new ConcurrentHashMap<>();
    /**
     * Known type inheritance relationships. The mapping is from a media type
     * to the closest supertype.
     */
    private final Map<MediaType, MediaType> inheritance = new HashMap<>();

    /**
     * Returns the built-in media type registry included in Tika.
     *
     * @return default media type registry
     * @since Apache Tika 0.8
     */
    public static MediaTypeRegistry getDefaultRegistry() {
        return MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry();
    }

    /**
     * Returns the set of all known canonical media types. Type aliases are
     * not included in the returned set.
     *
     * @return canonical media types
     * @since Apache Tika 0.8
     */
    public SortedSet<MediaType> getTypes() {
        return new TreeSet<>(registry.values());
    }

    /**
     * Returns the set of known aliases of the given canonical media type.
     *
     * @param type canonical media type
     * @return known aliases
     * @since Apache Tika 0.8
     */
    public SortedSet<MediaType> getAliases(MediaType type) {
        SortedSet<MediaType> aliases = new TreeSet<>();
        for (Map.Entry<MediaType, MediaType> entry : registry.entrySet()) {
            if (entry.getValue().equals(type) && !entry.getKey().equals(type)) {
                aliases.add(entry.getKey());
            }
        }
        return aliases;
    }

    /**
     * Returns the set of known children of the given canonical media type
     *
     * @param type canonical media type
     * @return known children
     * @since Apache Tika 1.8
     */
    public SortedSet<MediaType> getChildTypes(MediaType type) {
        SortedSet<MediaType> children = new TreeSet<>();
        for (Map.Entry<MediaType, MediaType> entry : inheritance.entrySet()) {
            if (entry.getValue().equals(type)) {
                children.add(entry.getKey());
            }
        }
        return children;
    }

    public void addType(MediaType type) {
        registry.put(type, type);
    }

    public void addAlias(MediaType type, MediaType alias) {
        registry.put(alias, type);
    }

    public void addSuperType(MediaType type, MediaType supertype) {
        inheritance.put(type, supertype);
    }

    public MediaType normalize(MediaType type) {
        if (type == null) {
            return null;
        }
        MediaType canonical = registry.get(type.getBaseType());
        if (canonical == null) {
            return type;
        } else if (type.hasParameters()) {
            return new MediaType(canonical, type.getParameters());
        } else {
            return canonical;
        }
    }

    /**
     * Checks whether the given media type a is a specialization of a more
     * generic type b. Both types should be already normalised.
     *
     * @param a media type, normalised
     * @param b suspected supertype, normalised
     * @return <code>true</code> if b is a supertype of a,
     * <code>false</code> otherwise
     * @since Apache Tika 0.8
     */
    public boolean isSpecializationOf(MediaType a, MediaType b) {
        return isInstanceOf(getSupertype(a), b);
    }

    /**
     * Checks whether the given media type equals the given base type or
     * is a specialization of it. Both types should be already normalised.
     *
     * @param a media type, normalised
     * @param b base type, normalised
     * @return <code>true</code> if b equals a or is a specialization of it,
     * <code>false</code> otherwise
     * @since Apache Tika 1.2
     */
    public boolean isInstanceOf(MediaType a, MediaType b) {
        return a != null && (a.equals(b) || isSpecializationOf(a, b));
    }

    /**
     * Parses and normalises the given media type string and checks whether
     * the result equals the given base type or is a specialization of it.
     * The given base type should already be normalised.
     *
     * @param a media type
     * @param b base type, normalised
     * @return <code>true</code> if b equals a or is a specialization of it,
     * <code>false</code> otherwise
     * @since Apache Tika 1.2
     */
    public boolean isInstanceOf(String a, MediaType b) {
        return isInstanceOf(normalize(MediaType.parse(a)), b);
    }

    /**
     * Returns the supertype of the given type. If the media type database
     * has an explicit inheritance rule for the type, then that is used.
     * Next, if the given type has any parameters, then the respective base
     * type (parameter-less) is returned. Otherwise built-in heuristics like
     * text/... -&gt; text/plain and .../...+xml -&gt; application/xml are used.
     * Finally application/octet-stream is returned for all types for which no other
     * supertype is known, and the return value for application/octet-stream
     * is <code>null</code>.
     *
     * @param type media type
     * @return supertype, or <code>null</code> for application/octet-stream
     * @since Apache Tika 0.8
     */
    public MediaType getSupertype(MediaType type) {
        if (type == null) {
            return null;
        } else if (inheritance.containsKey(type)) {
            return inheritance.get(type);
        } else if (type.hasParameters()) {
            return type.getBaseType();
        } else if (type.getSubtype().endsWith("+xml")) {
            return MediaType.APPLICATION_XML;
        } else if (type.getSubtype().endsWith("+zip")) {
            return MediaType.APPLICATION_ZIP;
        } else if ("text".equals(type.getType()) && !MediaType.TEXT_PLAIN.equals(type)) {
            return MediaType.TEXT_PLAIN;
        } else if (type.getType().contains("empty") && !MediaType.EMPTY.equals(type)) {
            return MediaType.EMPTY;
        } else if (!MediaType.OCTET_STREAM.equals(type)) {
            return MediaType.OCTET_STREAM;
        } else {
            return null;
        }
    }

}
