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
package org.apache.tika.metadata;

import java.util.Map;

public interface MetadataWriteFilter {

    void filterExisting(Map<String, String[]> data);

    boolean include(String field, String value);

    /**
     * Based on the field and value, this filter modifies the value
     * to something that should be set or added to the Metadata object.
     *
     * If the value is <code>null</code>, no value is set or added.
     *
     * Status updates (e.g. write limit reached) can be added directly to the
     * underlying metadata.
     *
     * @param field
     * @param value
     * @param data
     * @return
     */
    String filter(String field, String value, Map<String, String[]> data);
}
