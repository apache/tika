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
package org.apache.tika.extractor;

import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.StringUtils;

public class BasicEmbeddedBytesSelector implements EmbeddedBytesSelector {



    private final Set<String> includeMimes;
    private final Set<String> excludeMimes;
    private final Set<String> includeEmbeddedResourceTypes;

    private final Set<String> excludeEmbeddedResourceTypes;

    public BasicEmbeddedBytesSelector(Set<String> includeMimes, Set<String> excludeMimes,
                                      Set<String> includeEmbeddedResourceTypes,
                                      Set<String> excludeEmbeddedResourceTypes) {
        this.includeMimes = includeMimes;
        this.excludeMimes = excludeMimes;
        this.includeEmbeddedResourceTypes = includeEmbeddedResourceTypes;
        this.excludeEmbeddedResourceTypes = excludeEmbeddedResourceTypes;
    }

    public boolean select(Metadata metadata) {
        String mime = metadata.get(Metadata.CONTENT_TYPE);
        if (mime == null) {
            mime = "";
        } else {
            //if mime matters at all, make sure to get the mime without parameters
            if (includeMimes.size() > 0 || excludeMimes.size() > 0) {
                MediaType mt = MediaType.parse(mime);
                if (mt != null) {
                    mime = mt.getType() + "/" + mt.getSubtype();
                }
            }
        }
        if (excludeMimes.contains(mime)) {
            return false;
        }
        if (includeMimes.size() > 0 && ! includeMimes.contains(mime)) {
            return false;
        }
        String embeddedResourceType = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        //if a parser doesn't specify the type, treat it as ATTACHMENT
        embeddedResourceType = StringUtils.isBlank(embeddedResourceType) ? "ATTACHMENT" :
                embeddedResourceType;

        if (excludeEmbeddedResourceTypes.contains(embeddedResourceType)) {
            return false;
        }
        if (includeEmbeddedResourceTypes.size() > 0 && includeEmbeddedResourceTypes.contains(embeddedResourceType)) {
            return true;
        }
        return false;
    }
}
