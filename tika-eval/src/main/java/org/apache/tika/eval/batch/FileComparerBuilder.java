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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.batch.FileResourceConsumer;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.eval.AbstractProfiler;
import org.apache.tika.eval.ExtractComparer;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.DBWriter;
import org.apache.tika.eval.io.ExtractReader;
import org.apache.tika.eval.io.IDBWriter;
import org.apache.tika.util.PropsUtil;

public class FileComparerBuilder extends EvalConsumerBuilder {
    private final static String WHICH_DB = "h2";//TODO: allow flexibility


    @Override
    public FileResourceConsumer build() throws IOException, SQLException {
        Path thisRootDir = PropsUtil.getPath(localAttrs.get("extractsA"), null);
        if (thisRootDir == null) {
            throw new RuntimeException("Must specify \"extractsA\" -- directory for 'A' extracts");
        }
        Path thatRootDir = PropsUtil.getPath(localAttrs.get("extractsB"), null);
        if (thatRootDir == null) {
            throw new RuntimeException("Must specify \"extractsB\" -- directory for 'B' extracts");
        }

        Path inputRootDir = PropsUtil.getPath(localAttrs.get("inputDir"), null);

        long minJsonLength = PropsUtil.getLong(localAttrs.get("minJsonFileSizeBytes"), -1L);
        long maxJsonLength = PropsUtil.getLong(localAttrs.get("maxJsonFileSizeBytes"), -1L);

        ExtractReader.ALTER_METADATA_LIST alterExtractList = getAlterMetadata(localAttrs);


        IDBWriter writer = getDBWriter();
        //TODO: clean up the writing of the ref tables!!!
        try {
            populateRefTables(writer);
        } catch (SQLException e) {
            throw new RuntimeException("Can't populate ref tables", e);
        }

        if (inputRootDir == null) {
            //this is for the sake of the crawler
            throw new RuntimeException("Must specify an -inputDir");
        }

        return new ExtractComparer(queue, inputRootDir, thisRootDir, thatRootDir, writer,
                minJsonLength, maxJsonLength, alterExtractList);
    }

    @Override
    protected List<TableInfo> getTableInfo() {
        List<TableInfo> tableInfos = new ArrayList<>();
        tableInfos.add(ExtractComparer.COMPARISON_CONTAINERS);
        tableInfos.add(ExtractComparer.PROFILES_A);
        tableInfos.add(ExtractComparer.PROFILES_B);
        tableInfos.add(ExtractComparer.ERROR_TABLE_A);
        tableInfos.add(ExtractComparer.ERROR_TABLE_B);
        tableInfos.add(ExtractComparer.EXCEPTION_TABLE_A);
        tableInfos.add(ExtractComparer.EXCEPTION_TABLE_B);
        tableInfos.add(ExtractComparer.ERROR_TABLE_A);
        tableInfos.add(ExtractComparer.ERROR_TABLE_B);
        tableInfos.add(ExtractComparer.CONTENTS_TABLE_A);
        tableInfos.add(ExtractComparer.CONTENTS_TABLE_B);
        tableInfos.add(ExtractComparer.EMBEDDED_FILE_PATH_TABLE_A);
        tableInfos.add(ExtractComparer.EMBEDDED_FILE_PATH_TABLE_B);

        tableInfos.add(ExtractComparer.CONTENT_COMPARISONS);
        tableInfos.add(AbstractProfiler.MIME_TABLE);
        tableInfos.add(ExtractComparer.REF_PAIR_NAMES);
        tableInfos.add(AbstractProfiler.REF_PARSE_ERROR_TYPES);
        tableInfos.add(AbstractProfiler.REF_PARSE_EXCEPTION_TYPES);
        tableInfos.add(AbstractProfiler.REF_EXTRACT_ERROR_TYPES);
        return tableInfos;
    }

    @Override
    protected IDBWriter getDBWriter() throws IOException, SQLException {
        return new DBWriter(getTableInfo(), TikaConfig.getDefaultConfig(), dbUtil);
    }

    @Override
    protected void addErrorLogTablePairs(DBConsumersManager manager) {
        Path errorLogA = PropsUtil.getPath(localAttrs.get("errorLogFileA"), null);
        if (errorLogA == null) {
            return;
        }
        manager.addErrorLogTablePair(errorLogA, ExtractComparer.ERROR_TABLE_A);
        Path errorLogB = PropsUtil.getPath(localAttrs.get("errorLogFileB"), null);
        if (errorLogB == null) {
            return;
        }
        manager.addErrorLogTablePair(errorLogB, ExtractComparer.ERROR_TABLE_B);

    }

}
