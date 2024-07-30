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
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * This class clears the entire metadata object if the
 * mime matches the mime filter.  The idea is that you might not want
 * to store/transmit metadata for images or specific file types.
 */
public class ClearByMimeMetadataFilter extends MetadataFilter {
    private final Set<String> mimes;

    public ClearByMimeMetadataFilter() {
        this(new HashSet<>());
    }

    public ClearByMimeMetadataFilter(Set<String> mimes) {
        this.mimes = mimes;
    }

    @Override
    public void filter(Metadata metadata) throws TikaException {
        String mimeString = metadata.get(Metadata.CONTENT_TYPE);
        if (mimeString == null) {
            return;
        }
        MediaType mt = MediaType.parse(mimeString);
        if (mt != null) {
            mimeString = mt.getBaseType().toString();
        }
        if (mimes.contains(mimeString)) {
            for (String n : metadata.names()) {
                metadata.remove(n);
            }
        }
    }

    /**
     * @param mimes list of mimes that will trigger complete removal of metadata
     */
    @Field
    public void setMimes(List<String> mimes) {
        this.mimes.addAll(mimes);
    }

    public List<String> getMimes() {
        return new ArrayList<>(mimes);
    }
}
