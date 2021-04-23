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
package org.apache.tika.eval.app.io;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.eval.app.db.ColInfo;
import org.apache.tika.eval.app.db.Cols;
import org.apache.tika.eval.app.db.JDBCUtil;
import org.apache.tika.eval.app.db.MimeBuffer;
import org.apache.tika.eval.app.db.TableInfo;

/**
 * This is still in its early stages.  The idea is to
 * get something working with h2 and then add to that
 * as necessary.
 * <p>
 * Beware, this deletes the db file with each initialization.
 * <p>
 * Each thread must construct its own DBWriter because each
 * DBWriter creates its own PreparedStatements at initialization.
 */
public class DBWriter implements IDBWriter {

    private static final Logger LOG = LoggerFactory.getLogger(DBWriter.class);

    private static final AtomicInteger WRITER_ID = new AtomicInteger();
    private final Long commitEveryXRows = 10000L;
    //private final Long commitEveryXMS = 60000L;

    private final Connection conn;
    private final JDBCUtil dbUtil;
    private final MimeBuffer mimeBuffer;
    private final int myId = WRITER_ID.getAndIncrement();

    //<tableName, preparedStatement>
    private final Map<String, PreparedStatement> inserts = new HashMap<>();
    private final Map<String, LastInsert> lastInsertMap = new HashMap<>();

    public DBWriter(Connection connection, List<TableInfo> tableInfos, JDBCUtil dbUtil,
                    MimeBuffer mimeBuffer) throws IOException, SQLException {

        this.conn = connection;
        this.mimeBuffer = mimeBuffer;
        this.dbUtil = dbUtil;
        for (TableInfo tableInfo : tableInfos) {
            try {
                PreparedStatement st = createPreparedInsert(tableInfo);
                inserts.put(tableInfo.getName(), st);
                lastInsertMap.put(tableInfo.getName(), new LastInsert());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int getMimeId(String mimeString) {
        return mimeBuffer.getId(mimeString);
    }

    private PreparedStatement createPreparedInsert(TableInfo tableInfo) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(tableInfo.getName());
        sb.append("(");
        int i = 0;
        for (ColInfo c : tableInfo.getColInfos()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(c.getName());
        }
        sb.append(") ");

        sb.append("VALUES");
        sb.append("(");
        for (int j = 0; j < i; j++) {
            if (j > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        sb.append(")");

        return conn.prepareStatement(sb.toString());
    }


    @Override
    public void writeRow(TableInfo table, Map<Cols, String> data) throws IOException {
        try {
            PreparedStatement p = inserts.get(table.getName());
            if (p == null) {
                throw new RuntimeException(
                        "Failed to create prepared statement for: " + table.getName());
            }
            dbUtil.batchInsert(p, table, data);
            LastInsert lastInsert = lastInsertMap.get(table.getName());
            lastInsert.rowCount++;
            long elapsed = System.currentTimeMillis() - lastInsert.lastInsert;
            if (
                //elapsed > commitEveryXMS ||
                    lastInsert.rowCount % commitEveryXRows == 0) {
                LOG.info("writer ({}) on table ({}) is committing after {} rows and {} ms", myId,
                        table.getName(), lastInsert.rowCount, elapsed);
                p.executeBatch();
                conn.commit();
                lastInsert.lastInsert = System.currentTimeMillis();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /**
     * This closes the writer by executing batch and
     * committing changes.  This DOES NOT close the connection
     *
     * @throws IOException
     */
    public void close() throws IOException {
        for (PreparedStatement p : inserts.values()) {
            try {
                p.executeBatch();
            } catch (SQLException e) {
                throw new IOException(e);
            }
        }
        try {
            conn.commit();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private class LastInsert {
        private long lastInsert = System.currentTimeMillis();
        private long rowCount = 0;
    }
}
