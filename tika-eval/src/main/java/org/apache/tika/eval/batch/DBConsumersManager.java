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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.batch.ConsumersManager;
import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.XMLErrorLogUpdater;
import org.apache.tika.eval.db.JDBCUtil;
import org.apache.tika.eval.db.MimeBuffer;
import org.apache.tika.eval.db.TableInfo;


public class DBConsumersManager extends ConsumersManager {

    private final Connection conn;
    private final MimeBuffer mimeBuffer;
    private final List<LogTablePair> errorLogs = new ArrayList<>();

    public DBConsumersManager(JDBCUtil dbUtil, MimeBuffer mimeBuffer, List<FileResourceConsumer> consumers)
            throws SQLException {
        super(consumers);
        this.conn = dbUtil.getConnection();
        this.mimeBuffer = mimeBuffer;
    }


    @Override
    public void shutdown() {

        for (FileResourceConsumer consumer : getConsumers()) {
            if (consumer instanceof AbstractProfiler) {
                try{
                    ((AbstractProfiler)consumer).closeWriter();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        try {
            mimeBuffer.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        //MUST HAPPEN AFTER consumers have closed and
        //committed container information!!!
        XMLErrorLogUpdater up = new XMLErrorLogUpdater();
        for (LogTablePair p : errorLogs) {
            try {
                up.update(conn, p.tableInfo, p.log);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }


        try {
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addErrorLogTablePair(Path log, TableInfo tableInfo) {
        LogTablePair p = new LogTablePair();
        p.log = log;
        p.tableInfo = tableInfo;
        errorLogs.add(p);
    }

    class LogTablePair {
        Path log;
        TableInfo tableInfo;
    }
}
