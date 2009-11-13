/**
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
package org.apache.tika.parser;

import java.util.HashMap;
import java.util.Map;

/**
 * Parse context. Used to pass context information to Tika parsers.
 *
 * @since Apache Tika 0.5
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
 */
public class ParseContext {

    private final Map<Class<?>, Object> context =
        new HashMap<Class<?>, Object>();
 
    public <T> void set(Class<T> key, T value) {
        context.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> key) {
        return (T) context.get(key);
    }

    public <T> T get(Class<T> key, T defaultValue) {
        T value = get(key);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

}
