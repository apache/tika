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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OneNote;
import org.apache.tika.metadata.Property;

/**
 * Collects the GUID metadata of one OneNote section for the classic and FSSHTTPB parsers.
 * Values are deduplicated per key and capped at {@link #MAX_GUID_COUNT} in total.
 */
public final class OneNoteGuidCollector {

    public static final int MAX_GUID_COUNT = 100_000;

    public enum Category {
        SECTION(OneNote.SECTION_GUID),
        PAGE(OneNote.PAGE_GUIDS),
        PAGE_SERIES(OneNote.PAGE_SERIES_GUIDS),
        PAGE_NODE(OneNote.PAGE_NODE_GUIDS),
        CONFLICT_PAGE(OneNote.CONFLICT_PAGE_GUIDS);

        private final Property property;

        Category(Property property) {
            this.property = property;
        }
    }

    private final Map<Category, LinkedHashSet<String>> values = new EnumMap<>(Category.class);
    private final Consumer<String> warningHandler;
    private int count;
    private boolean limitWarningReported;

    public OneNoteGuidCollector(Consumer<String> warningHandler) {
        this.warningHandler = warningHandler;
        for (Category category : Category.values()) {
            values.put(category, new LinkedHashSet<>());
        }
    }

    public void add(Category category, String guid) {
        if (guid == null) {
            return;
        }
        Set<String> categoryValues = values.get(category);
        if (categoryValues.contains(guid)
                || (category == Category.SECTION && !categoryValues.isEmpty())) {
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

    /**
     * Records a NotebookManagementEntityGuid under the key for the JCID of the object that
     * carries it. Section-node objects and unrecognized JCIDs are skipped.
     */
    public void addForObjectType(int jcid, String guid) {
        Category category = categoryFor(jcid);
        if (category != null) {
            add(category, guid);
        }
    }

    static Category categoryFor(int jcid) {
        switch (jcid) {
            case OneNoteJcid.PAGE_METADATA:
                return Category.PAGE;
            case OneNoteJcid.PAGE_SERIES_NODE:
                return Category.PAGE_SERIES;
            case OneNoteJcid.PAGE_NODE:
                return Category.PAGE_NODE;
            case OneNoteJcid.CONFLICT_PAGE_METADATA:
                return Category.CONFLICT_PAGE;
            default:
                return null;
        }
    }

    private void reportLimitWarning() {
        if (!limitWarningReported) {
            limitWarningReported = true;
            warningHandler.accept("Capping OneNote GUID metadata at " + MAX_GUID_COUNT +
                    " distinct values");
        }
    }

    public Set<String> values(Category category) {
        return Collections.unmodifiableSet(values.get(category));
    }

    public boolean isFull() {
        return count >= MAX_GUID_COUNT;
    }

    /** Sets each non-empty key on {@code metadata}, with values sorted. */
    public void publish(Metadata metadata) {
        for (Map.Entry<Category, LinkedHashSet<String>> entry : values.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                if (entry.getKey() == Category.SECTION) {
                    metadata.set(entry.getKey().property, entry.getValue().iterator().next());
                } else {
                    String[] sorted = entry.getValue().toArray(new String[0]);
                    Arrays.sort(sorted);
                    metadata.set(entry.getKey().property, sorted);
                }
            }
        }
    }
}
