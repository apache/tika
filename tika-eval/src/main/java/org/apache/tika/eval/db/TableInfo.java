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
package org.apache.tika.eval.db;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TableInfo {

    private final String name;
    private final List<ColInfo> colInfos = new ArrayList<>();
    private final Set<Cols> colNames = new HashSet<>();

    private String prefix;

    public TableInfo(String name, ColInfo... cols) {
        Collections.addAll(colInfos, cols);
        Collections.unmodifiableList(colInfos);
        this.name = name;
        for (ColInfo c : colInfos) {
            assert (!colNames.contains(c.getName()));
            colNames.add(c.getName());
        }
    }

    public TableInfo(String name, List<ColInfo> cols) {
        colInfos.addAll(cols);
        Collections.unmodifiableList(colInfos);
        this.name = name;
        for (ColInfo c : colInfos) {
            assert (!colNames.contains(c.getName()));
            colNames.add(c.getName());
        }
    }

    public String getName() {
        if (prefix == null) {
            return name;
        }
        return prefix+name;
    }

    public void setNamePrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<ColInfo> getColInfos() {
        return colInfos;
    }

    public boolean containsColumn(Cols cols) {
        return colNames.contains(cols);
    }
}

