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
package org.apache.tika.pipes.core;

import java.util.List;
import java.util.Locale;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

/**
 * Mock PassbackFilter for testing. Removes items without RESOURCE_NAME_KEY
 * and uppercases the RESOURCE_NAME_KEY values.
 */
@TikaComponent(contextKey = PassbackFilter.class)
public class MockPassbackFilter extends PassbackFilter {

    public MockPassbackFilter() {
        // Required for Jackson deserialization
    }

    @Override
    public void filter(List<Metadata> metadataList) throws TikaException {
        // Remove items without RESOURCE_NAME_KEY and transform remaining ones
        metadataList.removeIf(m -> StringUtils.isBlank(m.get(TikaCoreProperties.RESOURCE_NAME_KEY)));
        for (Metadata m : metadataList) {
            String val = m.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            // Clear all fields and only keep RESOURCE_NAME_KEY (uppercased)
            for (String name : m.names()) {
                m.remove(name);
            }
            m.set(TikaCoreProperties.RESOURCE_NAME_KEY, val.toUpperCase(Locale.ROOT));
        }
    }
}
