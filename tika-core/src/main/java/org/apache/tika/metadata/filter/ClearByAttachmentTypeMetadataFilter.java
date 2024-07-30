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
package org.apache.tika.metadata.filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * This class clears the entire metadata object if the
 * attachment type matches one of the types.  The idea is that you might not want
 * to store/transmit metadata for images or specific file types.
 */
public class ClearByAttachmentTypeMetadataFilter extends MetadataFilter {
    private final Set<String> types;

    public ClearByAttachmentTypeMetadataFilter() {
        this(new HashSet<>());
    }

    public ClearByAttachmentTypeMetadataFilter(Set<String> types) {
        this.types = types;
    }

    @Override
    public void filter(Metadata metadata) throws TikaException {
        String type = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        if (type == null) {
            return;
        }
        if (! types.contains(type)) {
            return;
        }
        for (String n : metadata.names()) {
            metadata.remove(n);
        }
    }

    /**
     * For types see {@link TikaCoreProperties.EmbeddedResourceType}
     *
     * @param types attachment types that should be deleted.
     * @throws TikaConfigException
     */
    @Field
    public void setTypes(List<String> types) throws TikaConfigException {
        for (String t : types) {
            try {
                TikaCoreProperties.EmbeddedResourceType.valueOf(t);
            } catch (IllegalArgumentException e) {
                StringBuilder sb = new StringBuilder();
                int i = 0;
                for (TikaCoreProperties.EmbeddedResourceType type : TikaCoreProperties.EmbeddedResourceType.values()) {
                    if (i++ > 0) {
                        sb.append(", ");
                    }
                    sb.append(type.name());
                }
                throw new TikaConfigException("I'm sorry. I regret I don't recognise " + t +
                        ". I do recognize the following (case-sensitive):" + sb.toString());
            }
        }
        this.types.addAll(types);
    }

    public List<String> getTypes() {
        return new ArrayList<>(types);
    }
}
