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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

public class FieldNameMappingFilter implements MetadataFilter {
    private static final String MAPPING_OPERATOR = "->";

    Map<String, String> mapping = new HashMap<>();

    boolean excludeUnmapped = true;

    @Override
    public void filter(Metadata metadata) throws TikaException {
        if (excludeUnmapped) {
            for (String n : metadata.names()) {
                if (mapping.containsKey(n)) {
                    String[] vals = metadata.getValues(n);
                    metadata.remove(n);
                    for (String val : vals) {
                        metadata.add(mapping.get(n), val);
                    }
                } else {
                    mapping.remove(n);
                }
            }
        } else {
            for (String n : metadata.names()) {
                if (mapping.containsKey(n)) {
                    String[] vals = metadata.getValues(n);
                    metadata.remove(n);
                    for (String val : vals) {
                        metadata.add(mapping.get(n), val);
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
    public void setMappings(List<String> mappings) {
        for (String m : mappings) {
            String[] args = m.split(MAPPING_OPERATOR);
            if (args.length == 0 || args.length == 1) {
                throw new IllegalArgumentException("Can't find mapping operator '->' in: " + m);
            } else if (args.length > 2) {
                throw new IllegalArgumentException(
                        "Must have only one mapping operator. I found more than one: " + m);
            }
            String from = args[0].trim();
            if (from.length() == 0) {
                throw new IllegalArgumentException(
                        "Must contain content before the " + "mapping operator '->'");
            }
            String to = args[1].trim();
            if (to.length() == 0) {
                throw new IllegalArgumentException(
                        "Must contain content after the " + "mapping operator '->'");
            }
            mapping.put(from, to);
        }
    }
}
