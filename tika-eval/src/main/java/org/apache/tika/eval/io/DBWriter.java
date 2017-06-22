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
package org.apache.tika.eval.io;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tika.eval.db.ColInfo;
import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.JDBCUtil;
import org.apache.tika.eval.db.MimeBuffer;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.io.IOExceptionWithCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is still in its early stages.  The idea is to
 * get something working with h2 and then add to that
 * as necessary.
 *
 * Beware, this deletes the db file with each initialization.
 *
 * Each thread must construct its own DBWriter because each
 * DBWriter creates its own PreparedStatements at initialization.
 */
public class DBWriter implements IDBWriter {

    private static final Logger LOG = LoggerFactory.getLogger(DBWriter.class);

    private static final AtomicInteger WRITER_ID = new AtomicInteger();
    private final AtomicLong insertedRows = new AtomicLong();
    private final Long commitEveryX = 1000L;

    private final Connection conn;
    private final JDBCUtil dbUtil;
    private final MimeBuffer mimeBuffer;
    private final int myId = WRITER_ID.getAndIncrement();

    //<tableName, preparedStatement>
    private final Map<String, PreparedStatement> inserts = new HashMap<>();

    public DBWriter(Connection connection, List<TableInfo> tableInfos, JDBCUtil dbUtil, MimeBuffer mimeBuffer)
            throws IOException, SQLException {

        this.conn = connection;
        this.mimeBuffer = mimeBuffer;
        this.dbUtil = dbUtil;
        for (TableInfo tableInfo : tableInfos) {
            try {
                PreparedStatement st = createPreparedInsert(tableInfo);
                inserts.put(tableInfo.getName(), st);
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


    public void writeRow(TableInfo table, Map<Cols, String> data) throws IOException {
        try {
            PreparedStatement p = inserts.get(table.getName());
            if (p == null) {
                throw new RuntimeException("Failed to create prepared statement for: "+
                        table.getName());
            }
            dbUtil.insert(p, table, data);
            long rows = insertedRows.incrementAndGet();
            if (rows % commitEveryX == 0) {
                LOG.debug("writer ({}) is committing after {} rows", myId, rows);
                conn.commit();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    public void close() throws IOException {
        try {
            conn.commit();
        } catch (SQLException e){
            throw new IOExceptionWithCause(e);
        }
        try {
            conn.close();
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }

    }
}
