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
package org.apache.tika.parser.microsoft.onenote;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class OneNoteGuidCollector {

    static final int MAX_GUID_COUNT = 100_000;

    enum Category {
        SECTION, PAGE, PAGE_SERIES, CONFLICT_PAGE, ENTITY
    }

    private final Map<Category, LinkedHashSet<String>> values = new EnumMap<>(Category.class);
    private final Consumer<String> warningHandler;
    private int count;
    private boolean limitWarningReported;

    OneNoteGuidCollector(Consumer<String> warningHandler) {
        this.warningHandler = warningHandler;
        for (Category category : Category.values()) {
            values.put(category, new LinkedHashSet<>());
        }
    }

    void add(Category category, String guid) {
        if (guid == null) {
            return;
        }
        Set<String> categoryValues = values.get(category);
        if (categoryValues.contains(guid)) {
            return;
        }
        if (count >= MAX_GUID_COUNT) {
            reportLimitWarning();
            return;
        }
        categoryValues.add(guid);
        count++;
        if (count == MAX_GUID_COUNT) {
            reportLimitWarning();
        }
    }

    private void reportLimitWarning() {
        if (!limitWarningReported) {
            limitWarningReported = true;
            warningHandler.accept("Capping OneNote GUID metadata at " + MAX_GUID_COUNT +
                    " distinct values");
        }
    }

    Set<String> values(Category category) {
        return Collections.unmodifiableSet(values.get(category));
    }

    boolean isFull() {
        return count >= MAX_GUID_COUNT;
    }
}
