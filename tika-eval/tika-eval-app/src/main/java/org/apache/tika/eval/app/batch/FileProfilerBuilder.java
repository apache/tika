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
package org.apache.tika.eval.app.batch;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.eval.app.ExtractProfiler;
import org.apache.tika.eval.app.FileProfiler;
import org.apache.tika.eval.app.db.TableInfo;
import org.apache.tika.util.PropsUtil;


public class FileProfilerBuilder extends EvalConsumerBuilder {

    public final static String TABLE_PREFIX_KEY = "tablePrefix";

    private final List<TableInfo> tableInfos;

    public FileProfilerBuilder() {
        List<TableInfo> tableInfos = new ArrayList();
        tableInfos.add(FileProfiler.FILE_MIME_TABLE);
        tableInfos.add(FileProfiler.FILE_PROFILES);
        this.tableInfos = Collections.unmodifiableList(tableInfos);

    }

    @Override
    public FileResourceConsumer build() throws IOException, SQLException {

        Path inputDir = PropsUtil.getPath(localAttrs.get("inputDir"), null);

        //we _could_ set this to extracts (if not null)
        //here, but the Crawler defaults to "input" if nothing is passed
        //so this won't work
        if (inputDir == null) {
            throw new RuntimeException("Must specify -inputDir");
        }
        return parameterizeProfiler(new FileProfiler(queue, inputDir, getDBWriter(tableInfos)));
    }


    @Override
    protected void updateTableInfosWithPrefixes(Map<String, String> attrs) {
        String tableNamePrefix = attrs.get(TABLE_PREFIX_KEY);
        if (tableNamePrefix != null && !tableNamePrefix.equals("null")) {
            for (TableInfo tableInfo : tableInfos) {
                tableInfo.setNamePrefix(tableNamePrefix);
            }
        }
    }

    @Override
    protected List<TableInfo> getRefTableInfos() {
        return Collections.EMPTY_LIST;
    }

    @Override
    protected List<TableInfo> getNonRefTableInfos() {
        return tableInfos;
    }

    @Override
    protected TableInfo getMimeTable() {
        return FileProfiler.FILE_MIME_TABLE;
    }

    @Override
    protected void addErrorLogTablePairs(DBConsumersManager manager) {
        Path errorLog = PropsUtil.getPath(localAttrs.get("errorLogFile"), null);
        if (errorLog == null) {
            return;
        }
        manager.addErrorLogTablePair(errorLog, ExtractProfiler.EXTRACT_EXCEPTION_TABLE);
    }
}
