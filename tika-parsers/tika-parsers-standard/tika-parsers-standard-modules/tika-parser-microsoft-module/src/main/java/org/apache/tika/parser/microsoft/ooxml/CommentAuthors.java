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
package org.apache.tika.parser.microsoft.ooxml;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores comment author names and initials by ID for PPTX comment handling.
 */
class CommentAuthors {
    final Map<String, String> nameMap = new HashMap<>();
    final Map<String, String> initialMap = new HashMap<>();

    void add(String id, String name, String initials) {
        if (id == null) {
            return;
        }
        if (name != null) {
            nameMap.put(id, name);
        }
        if (initials != null) {
            initialMap.put(id, initials);
        }
    }

    String getName(String id) {
        if (id == null) {
            return null;
        }
        return nameMap.get(id);
    }

    String getInitials(String id) {
        if (id == null) {
            return null;
        }
        return initialMap.get(id);
    }
}
