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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

public class FieldNameMappingFilter extends MetadataFilter {

    Map<String, String> mappings = new LinkedHashMap<>();

    boolean excludeUnmapped = true;

    @Override
    public void filter(Metadata metadata) throws TikaException {
        if (excludeUnmapped) {
            for (String n : metadata.names()) {
                if (mappings.containsKey(n)) {
                    String[] vals = metadata.getValues(n);
                    metadata.remove(n);
                    for (String val : vals) {
                        metadata.add(mappings.get(n), val);
                    }
                } else {
                    metadata.remove(n);
                }
            }
        } else {
            for (String n : metadata.names()) {
                if (mappings.containsKey(n)) {
                    String[] vals = metadata.getValues(n);
                    metadata.remove(n);
                    for (String val : vals) {
                        metadata.add(mappings.get(n), val);
                    }
                }
            }
        }
    }

    /**
     * If this is <code>true</code> (default), this means that only the fields that
     * have a "from" value in the mapper will be passed through.  Otherwise,
     * this will pass through all keys/values and mutate the keys
     * that exist in the mappings.
     *
     * @param excludeUnmapped
     */
    @Field
    public void setExcludeUnmapped(boolean excludeUnmapped) {
        this.excludeUnmapped = excludeUnmapped;
    }

    @Field
    public void setMappings(Map<String, String> mappings) {
        for (Map.Entry<String, String> e : mappings.entrySet()) {
            this.mappings.put(e.getKey(), e.getValue());
        }
    }

    @Override
    public String toString() {
        return "FieldNameMappingFilter{" + "mappings=" + mappings + ", excludeUnmapped=" + excludeUnmapped + '}';
    }
}
