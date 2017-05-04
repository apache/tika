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
package org.apache.tika;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.IDBWriter;


public class MockDBWriter implements IDBWriter {
    //Map of tableName and tables
    //each table consists of a list of rows.
    //Each row consists of a map of columns/values
    Map<String, List<Map<Cols, String>>> db = new HashMap<String, List<Map<Cols, String>>>();

    public Map<String, Integer> mimes = new HashMap<>();

    public MockDBWriter() throws Exception {
    }

    @Override
    public void writeRow(TableInfo tableInfo, Map<Cols, String> row) throws IOException {
        List<Map<Cols, String>> table = db.get(tableInfo.getName());
        if (table == null) {
            table = new ArrayList<Map<Cols, String>>();
        }
        table.add(row);
        db.put(tableInfo.getName(), table);
    }

    @Override
    public void close() throws IOException {
        //no-op
    }

    @Override
    public int getMimeId(String mimeString) {
        Integer val = mimes.get(mimeString);
        if (val != null) {
            return val;
        }

        int id = mimes.size();
        mimes.put(mimeString, id);
        return id;
    }

    public List<Map<Cols, String>> getTable(TableInfo tableInfo) {
        if (db.get(tableInfo.getName()) == null) {
            System.err.println("I can't seem to find: "+ tableInfo.getName() + ", but I do see:");
            for (String table : db.keySet()) {
                System.err.println(table);
            }
        }
        return db.get(tableInfo.getName());
    }

    public void clear() {
        db.clear();
    }
}
