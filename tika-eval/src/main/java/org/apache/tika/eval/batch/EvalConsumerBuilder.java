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

package org.apache.tika.eval.batch;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.DBUtil;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.ExtractReader;
import org.apache.tika.eval.io.IDBWriter;

public abstract class EvalConsumerBuilder {
    private AtomicInteger count = new AtomicInteger(0);
    protected ArrayBlockingQueue<FileResource> queue;
    Map<String, String> localAttrs;
    DBUtil dbUtil;

    public void init(ArrayBlockingQueue<FileResource> queue, Map<String, String> localAttrs,
                     DBUtil dbUtil) {
        this.queue = queue;
        this.localAttrs = localAttrs;
        this.dbUtil = dbUtil;
    }

    public abstract FileResourceConsumer build() throws IOException, SQLException;

    protected abstract List<TableInfo> getTableInfo();

    protected abstract IDBWriter getDBWriter() throws IOException, SQLException;

    protected abstract void addErrorLogTablePairs(DBConsumersManager manager);

    public void populateRefTables(IDBWriter writer) throws IOException, SQLException {
        //figure out cleaner way of doing this!
        if (count.getAndIncrement() > 0) {
            return;
        }
        Map<Cols, String> m = new HashMap<>();
        for (AbstractProfiler.PARSE_ERROR_TYPE t : AbstractProfiler.PARSE_ERROR_TYPE.values()) {
            m.clear();
            m.put(Cols.PARSE_ERROR_TYPE_ID, Integer.toString(t.ordinal()));
            m.put(Cols.PARSE_ERROR_DESCRIPTION, t.name());
            writer.writeRow(AbstractProfiler.REF_PARSE_ERROR_TYPES, m);
        }

        for (AbstractProfiler.EXCEPTION_TYPE t : AbstractProfiler.EXCEPTION_TYPE.values()) {
            m.clear();
            m.put(Cols.PARSE_EXCEPTION_TYPE_ID, Integer.toString(t.ordinal()));
            m.put(Cols.PARSE_EXCEPTION_DESCRIPTION, t.name());
            writer.writeRow(AbstractProfiler.REF_PARSE_EXCEPTION_TYPES, m);
        }

        for (AbstractProfiler.EXTRACT_ERROR_TYPE t :
                AbstractProfiler.EXTRACT_ERROR_TYPE.values()) {
            m.clear();
            m.put(Cols.EXTRACT_ERROR_TYPE_ID, Integer.toString(t.ordinal()));
            m.put(Cols.EXTRACT_ERROR_DESCRIPTION, t.name());
            writer.writeRow(AbstractProfiler.REF_EXTRACT_ERROR_TYPES, m);
        }

    }

    ExtractReader.ALTER_METADATA_LIST getAlterMetadata(Map<String, String> localAttrs) {

        String alterExtractString = localAttrs.get("alterExtract");
        ExtractReader.ALTER_METADATA_LIST alterExtractList = ExtractReader.ALTER_METADATA_LIST.AS_IS;
        if (alterExtractString == null || alterExtractString.equalsIgnoreCase("as_is")) {
            alterExtractList = ExtractReader.ALTER_METADATA_LIST.AS_IS;
        } else if (alterExtractString.equalsIgnoreCase("first_only")) {
            alterExtractList = ExtractReader.ALTER_METADATA_LIST.FIRST_ONLY;
        } else if (alterExtractString.equalsIgnoreCase("concatenate_content")) {
            alterExtractList = ExtractReader.ALTER_METADATA_LIST.CONCATENATE_CONTENT_INTO_FIRST;
        } else {
            throw new RuntimeException("options for alterExtract: as_is, first_only, concatenate_content." +
                    " I don't understand:" + alterExtractString);
        }
        return alterExtractList;
    }


/*
    public abstract Map<String, String> getIndexInfo();

    class ValueComparator implements Comparator<String> {

        Map<String, ColInfo> map;

        public ValueComparator(Map<String, ColInfo> base) {
            this.map = base;
        }

        public int compare(String a, String b) {
            Integer aVal = map.get(a).getDBColOffset();
            Integer bVal = map.get(b).getDBColOffset();
            if (aVal == null || bVal == null) {
                throw new IllegalArgumentException("Column offset must be specified!");
            }
            if (aVal == bVal && ! map.get(a).equals(map.get(b))) {
                throw new IllegalArgumentException("Column offsets must be unique: " + a + " and " + b + " both have: "+aVal);
            }
            if (aVal < bVal) {
                return -1;
            } else {
                return 1;
            }
        }
    }
*/
}
