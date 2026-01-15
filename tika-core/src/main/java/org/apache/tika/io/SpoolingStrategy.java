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
package org.apache.tika.io;

import java.util.HashSet;
import java.util.Set;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

/**
 * Strategy for determining when to spool a TikaInputStream to disk.
 * <p>
 * Components (detectors, parsers) can check this strategy before calling
 * {@link TikaInputStream#getFile()} to determine if spooling is appropriate
 * for the given media type.
 * <p>
 * Default behavior (when no strategy is in ParseContext): components spool when needed.
 * A strategy allows fine-grained control over spooling decisions.
 * <p>
 * Configure via JSON:
 * <pre>
 * {
 *   "spooling-strategy": {
 *     "spoolTypes": ["application/zip", "application/x-tika-msoffice", "application/pdf"]
 *   }
 * }
 * </pre>
 */
@TikaComponent(spi = false)
public class SpoolingStrategy {

    private static final Set<MediaType> DEFAULT_SPOOL_TYPES;

    static {
        Set<MediaType> types = new HashSet<>();
        types.add(MediaType.application("zip"));
        types.add(MediaType.application("x-tika-msoffice"));
        types.add(MediaType.application("x-bplist"));
        types.add(MediaType.application("pdf"));
        DEFAULT_SPOOL_TYPES = Set.copyOf(types);
    }

    private Set<MediaType> spoolTypes = new HashSet<>(DEFAULT_SPOOL_TYPES);
    private MediaTypeRegistry mediaTypeRegistry;

    /**
     * Determines whether the stream should be spooled to disk.
     *
     * @param tis       the TikaInputStream (can check hasFile(), getLength())
     * @param metadata  metadata (can check content-type hints, filename)
     * @param mediaType the detected or declared media type
     * @return true if the stream should be spooled to disk
     */
    public boolean shouldSpool(TikaInputStream tis, Metadata metadata, MediaType mediaType) {
        // Already has file? No need to spool
        if (tis != null && tis.hasFile()) {
            return false;
        }
        // Check type against spoolTypes
        return matchesSpoolType(mediaType);
    }

    private boolean matchesSpoolType(MediaType type) {
        if (type == null) {
            return false;
        }
        // Exact match
        if (spoolTypes.contains(type)) {
            return true;
        }
        // Base type match (without parameters)
        MediaType baseType = type.getBaseType();
        if (spoolTypes.contains(baseType)) {
            return true;
        }
        // Check if type is a specialization of any spool type
        if (mediaTypeRegistry != null) {
            for (MediaType spoolType : spoolTypes) {
                if (mediaTypeRegistry.isSpecializationOf(type, spoolType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sets the media types that should be spooled to disk.
     * Specializations of these types are also included.
     *
     * @param spoolTypes set of media types to spool
     */
    public void setSpoolTypes(Set<MediaType> spoolTypes) {
        this.spoolTypes = spoolTypes != null ? new HashSet<>(spoolTypes) : new HashSet<>();
    }

    /**
     * Returns the media types that should be spooled to disk.
     *
     * @return set of media types to spool
     */
    public Set<MediaType> getSpoolTypes() {
        return spoolTypes;
    }

    /**
     * Sets the media type registry used for checking type specializations.
     *
     * @param registry the media type registry
     */
    public void setMediaTypeRegistry(MediaTypeRegistry registry) {
        this.mediaTypeRegistry = registry;
    }

    /**
     * Returns the media type registry.
     *
     * @return the media type registry, or null if not set
     */
    public MediaTypeRegistry getMediaTypeRegistry() {
        return mediaTypeRegistry;
    }
}
