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
package org.apache.tika.pipes.core.extractor;

import java.util.HashSet;
import java.util.Set;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.extractor.UnpackSelector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.StringUtils;

/**
 * Selector for filtering which embedded documents should have their bytes extracted
 * during UNPACK mode. Configure via ParseContext in tika-config.json.
 * <p>
 * Example configuration:
 * <pre>
 * {
 *   "parse-context": {
 *     "standard-unpack-selector": {
 *       "includeMimeTypes": ["image/jpeg", "image/png"],
 *       "excludeMimeTypes": ["application/pdf"],
 *       "includeEmbeddedResourceTypes": ["ATTACHMENT"],
 *       "excludeEmbeddedResourceTypes": ["INLINE"]
 *     }
 *   }
 * }
 * </pre>
 */
@TikaComponent
public class StandardUnpackSelector implements UnpackSelector {

    private Set<String> includeMimeTypes = new HashSet<>();
    private Set<String> excludeMimeTypes = new HashSet<>();
    private Set<String> includeEmbeddedResourceTypes = new HashSet<>();
    private Set<String> excludeEmbeddedResourceTypes = new HashSet<>();

    public StandardUnpackSelector() {
    }

    @Override
    public boolean select(Metadata metadata) {
        // If no filters configured, accept all
        if (includeMimeTypes.isEmpty() && excludeMimeTypes.isEmpty()
                && includeEmbeddedResourceTypes.isEmpty() && excludeEmbeddedResourceTypes.isEmpty()) {
            return true;
        }

        String mime = metadata.get(Metadata.CONTENT_TYPE);
        if (mime == null) {
            mime = "";
        } else {
            // If mime matters at all, make sure to get the mime without parameters
            if (!includeMimeTypes.isEmpty() || !excludeMimeTypes.isEmpty()) {
                MediaType mt = MediaType.parse(mime);
                if (mt != null) {
                    mime = mt.getType() + "/" + mt.getSubtype();
                }
            }
        }

        if (excludeMimeTypes.contains(mime)) {
            return false;
        }
        if (!includeMimeTypes.isEmpty() && !includeMimeTypes.contains(mime)) {
            return false;
        }

        String embeddedResourceType = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        // If a parser doesn't specify the type, treat it as ATTACHMENT
        embeddedResourceType = StringUtils.isBlank(embeddedResourceType) ? "ATTACHMENT" :
                embeddedResourceType;

        if (excludeEmbeddedResourceTypes.contains(embeddedResourceType)) {
            return false;
        }
        if (!includeEmbeddedResourceTypes.isEmpty()
                && !includeEmbeddedResourceTypes.contains(embeddedResourceType)) {
            return false;
        }

        return true;
    }

    public Set<String> getIncludeMimeTypes() {
        return includeMimeTypes;
    }

    public void setIncludeMimeTypes(Set<String> includeMimeTypes) {
        this.includeMimeTypes = new HashSet<>(includeMimeTypes);
    }

    public Set<String> getExcludeMimeTypes() {
        return excludeMimeTypes;
    }

    public void setExcludeMimeTypes(Set<String> excludeMimeTypes) {
        this.excludeMimeTypes = new HashSet<>(excludeMimeTypes);
    }

    public Set<String> getIncludeEmbeddedResourceTypes() {
        return includeEmbeddedResourceTypes;
    }

    public void setIncludeEmbeddedResourceTypes(Set<String> includeEmbeddedResourceTypes) {
        this.includeEmbeddedResourceTypes = new HashSet<>(includeEmbeddedResourceTypes);
    }

    public Set<String> getExcludeEmbeddedResourceTypes() {
        return excludeEmbeddedResourceTypes;
    }

    public void setExcludeEmbeddedResourceTypes(Set<String> excludeEmbeddedResourceTypes) {
        this.excludeEmbeddedResourceTypes = new HashSet<>(excludeEmbeddedResourceTypes);
    }

    @Override
    public String toString() {
        return "StandardUnpackSelector{" +
                "includeMimeTypes=" + includeMimeTypes +
                ", excludeMimeTypes=" + excludeMimeTypes +
                ", includeEmbeddedResourceTypes=" + includeEmbeddedResourceTypes +
                ", excludeEmbeddedResourceTypes=" + excludeEmbeddedResourceTypes +
                '}';
    }
}
