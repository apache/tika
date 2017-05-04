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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.batch.FileResource;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.JDBCUtil;
import org.apache.tika.eval.db.MimeBuffer;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.DBWriter;
import org.apache.tika.eval.io.ExtractReader;
import org.apache.tika.eval.io.ExtractReaderException;
import org.apache.tika.eval.io.IDBWriter;
import org.apache.tika.util.PropsUtil;

public abstract class EvalConsumerBuilder {
    private AtomicInteger count = new AtomicInteger(0);
    protected ArrayBlockingQueue<FileResource> queue;
    Map<String, String> localAttrs;
    JDBCUtil dbUtil;
    private MimeBuffer mimeBuffer;
    AtomicInteger initialized = new AtomicInteger(0);

    public MimeBuffer init(ArrayBlockingQueue<FileResource> queue, Map<String, String> localAttrs,
                     JDBCUtil dbUtil, boolean forceDrop) throws IOException, SQLException {
        if (initialized.getAndIncrement() > 0) {
            throw new RuntimeException("Can only init a consumer builder once!");
        }
        this.queue = queue;
        this.localAttrs = localAttrs;
        this.dbUtil = dbUtil;
        //the order of the following is critical
        //step 1. update the table names with prefixes
        updateTableInfosWithPrefixes(localAttrs);

        JDBCUtil.CREATE_TABLE createRegularTable = (forceDrop) ? JDBCUtil.CREATE_TABLE.DROP_IF_EXISTS :
                JDBCUtil.CREATE_TABLE.THROW_EX_IF_EXISTS;

        JDBCUtil.CREATE_TABLE createRefTable = (forceDrop) ? JDBCUtil.CREATE_TABLE.DROP_IF_EXISTS :
                JDBCUtil.CREATE_TABLE.SKIP_IF_EXISTS;

        //step 2. create the tables
        dbUtil.createTables(getNonRefTableInfos(), createRegularTable);
        dbUtil.createTables(getRefTableInfos(), createRefTable);

        //step 3. create mime buffer
        this.mimeBuffer = new MimeBuffer(dbUtil.getConnection(), TikaConfig.getDefaultConfig());

        //step 4. populate the reference tabless
        populateRefTables();

        return mimeBuffer;
    }

    public abstract FileResourceConsumer build() throws IOException, SQLException;

    protected abstract void updateTableInfosWithPrefixes(Map<String, String> attrs);

    /**
     *
     * @return only the ref tables
     */
    protected abstract List<TableInfo> getRefTableInfos();

    /**
     *
     * @return the main tables, not including the ref tables
     */
    protected abstract List<TableInfo> getNonRefTableInfos();

    protected abstract void addErrorLogTablePairs(DBConsumersManager manager);

    public void populateRefTables() throws IOException, SQLException {
        //test for one ref table.  If it exists, don't populate ref tables
        //TODO: test one at a time
        boolean tableExists = false;
        try (Connection connection = dbUtil.getConnection()) {
            Set<String> tables = dbUtil.getTables(connection);
            if (tables.contains(
                    AbstractProfiler.REF_PARSE_ERROR_TYPES.getName().toLowerCase(Locale.US)
            )) {
                tableExists = true;
            }
        } catch (SQLException e) {
            //swallow
        }

        if (tableExists) {
            return;
        }

        IDBWriter writer = getDBWriter(getRefTableInfos());
        Map<Cols, String> m = new HashMap<>();
        for (AbstractProfiler.PARSE_ERROR_TYPE t : AbstractProfiler.PARSE_ERROR_TYPE.values()) {
            m.clear();
            m.put(Cols.PARSE_ERROR_ID, Integer.toString(t.ordinal()));
            m.put(Cols.PARSE_ERROR_DESCRIPTION, t.name());
            writer.writeRow(AbstractProfiler.REF_PARSE_ERROR_TYPES, m);
        }

        for (AbstractProfiler.EXCEPTION_TYPE t : AbstractProfiler.EXCEPTION_TYPE.values()) {
            m.clear();
            m.put(Cols.PARSE_EXCEPTION_ID, Integer.toString(t.ordinal()));
            m.put(Cols.PARSE_EXCEPTION_DESCRIPTION, t.name());
            writer.writeRow(AbstractProfiler.REF_PARSE_EXCEPTION_TYPES, m);
        }

        for (ExtractReaderException.TYPE t :
                ExtractReaderException.TYPE.values()) {
            m.clear();
            m.put(Cols.EXTRACT_EXCEPTION_ID, Integer.toString(t.ordinal()));
            m.put(Cols.EXTRACT_EXCEPTION_DESCRIPTION, t.name());
            writer.writeRow(AbstractProfiler.REF_EXTRACT_EXCEPTION_TYPES, m);
        }
        writer.close();
    }

    protected IDBWriter getDBWriter(List<TableInfo> tableInfos) throws IOException, SQLException {
        Connection conn = dbUtil.getConnection();
        return new DBWriter(conn, tableInfos, dbUtil, mimeBuffer);
    }

    ExtractReader.ALTER_METADATA_LIST getAlterMetadata(Map<String, String> localAttrs) {

        String alterExtractString = localAttrs.get("alterExtract");
        ExtractReader.ALTER_METADATA_LIST alterExtractList;
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

    protected ExtractReader buildExtractReader(Map<String, String> localAttrs) {
        long minExtractLength = PropsUtil.getLong(localAttrs.get("minExtractLength"), -1L);
        long maxExtractLength = PropsUtil.getLong(localAttrs.get("maxExtractLength"), -1L);

        ExtractReader.ALTER_METADATA_LIST alterExtractList = getAlterMetadata(localAttrs);
        return new ExtractReader(alterExtractList, minExtractLength, maxExtractLength);
    }

    FileResourceConsumer parameterizeProfiler(AbstractProfiler abstractProfiler) {

        int maxContentLength = PropsUtil.getInt(localAttrs.get("maxContentLength"), -2);
        if (maxContentLength > -2) {
            abstractProfiler.setMaxContentLength(maxContentLength);
        }

        int maxContentLengthForLangId = PropsUtil.getInt(localAttrs.get("maxContentLengthForLangId"), -2);
        if (maxContentLengthForLangId > -2) {
            abstractProfiler.setMaxContentLengthForLangId(maxContentLengthForLangId);
        }

        int maxTokens = PropsUtil.getInt(localAttrs.get("maxTokens"), -2);
        if (maxTokens > -2) {
            abstractProfiler.setMaxTokens(maxTokens);
        }


        return abstractProfiler;
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
