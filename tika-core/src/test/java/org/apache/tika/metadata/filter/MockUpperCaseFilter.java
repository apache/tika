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

import java.util.Locale;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

/**
 * Mock Filter for testing uppercasing of all values
 */
public class MockUpperCaseFilter implements MetadataFilter {

    @Override
    public void filter(Metadata metadata) throws TikaException {
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            metadata.remove(n);
            for (String val : vals) {
                metadata.add(n, val.toUpperCase(Locale.US));
            }
        }
    }
}
