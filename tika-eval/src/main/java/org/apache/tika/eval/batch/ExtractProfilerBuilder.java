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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.ExtractProfiler;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.util.PropsUtil;


public class ExtractProfilerBuilder extends EvalConsumerBuilder {

    public final static String TABLE_PREFIX_KEY = "tablePrefix";

    private final List<TableInfo> tableInfos;
    private final List<TableInfo> refTableInfos;
    public ExtractProfilerBuilder() {
        List<TableInfo> tableInfos = new ArrayList();
        tableInfos.add(AbstractProfiler.MIME_TABLE);
        tableInfos.add(ExtractProfiler.CONTAINER_TABLE);
        tableInfos.add(ExtractProfiler.PROFILE_TABLE);
        tableInfos.add(ExtractProfiler.EXTRACT_EXCEPTION_TABLE);
        tableInfos.add(ExtractProfiler.EXCEPTION_TABLE);
        tableInfos.add(ExtractProfiler.CONTENTS_TABLE);
        tableInfos.add(ExtractProfiler.EMBEDDED_FILE_PATH_TABLE);
        this.tableInfos = Collections.unmodifiableList(tableInfos);

        List<TableInfo> refTableInfos = new ArrayList<>();
        refTableInfos.add(AbstractProfiler.REF_PARSE_ERROR_TYPES);
        refTableInfos.add(AbstractProfiler.REF_PARSE_EXCEPTION_TYPES);
        refTableInfos.add(AbstractProfiler.REF_EXTRACT_EXCEPTION_TYPES);
        this.refTableInfos = Collections.unmodifiableList(refTableInfos);
    }

    @Override
    public FileResourceConsumer build() throws IOException, SQLException {
        Path extracts = PropsUtil.getPath(localAttrs.get("extracts"), null);
        if (extracts == null) {
            throw new RuntimeException("Must specify \"extracts\" -- directory to crawl");
        }
        if (!Files.isDirectory(extracts)) {
            throw new RuntimeException("ROOT DIRECTORY DOES NOT EXIST: " +
                    extracts.toAbsolutePath());
        }

        Path inputDir = PropsUtil.getPath(localAttrs.get("inputDir"), null);

        //we _could_ set this to extracts (if not null)
        //here, but the Crawler defaults to "input" if nothing is passed
        //so this won't work
        if (inputDir == null) {
            throw new RuntimeException("Must specify -inputDir");
        }
        if (extracts == null && inputDir != null) {
            extracts = inputDir;
        }
        return parameterizeProfiler(new ExtractProfiler(queue, inputDir, extracts,
                buildExtractReader(localAttrs),
                getDBWriter(tableInfos)));
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
        return refTableInfos;
    }

    @Override
    protected List<TableInfo> getNonRefTableInfos() {
        return tableInfos;
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
