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
package org.apache.tika.pipes.reporters.jdbc;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 *
 * @param connectionString connection string
 * @param reportSql This is the sql for the prepared statement to execute
 *                  to store the report record. the default is:
 *                  <code>insert into tika_status (id, status, timestamp) values (?,?,?)</code>
 * @param tableName table name or defaults to 'tika_status'
 * @param createTable whether or not to create the table <b>NOTE</b> The default behavior is to drop the table if it exists and
 *                    then create it. Make sure to set this to false if you do not want to drop the table.
 *
 * @param postConnectionSql This sql will be called immediately after the connection is made. This was
 *                          initially added for setting pragmas on sqlite3, but may be used for other connection configuration in other dbs.
 *                          Note: This is called before the table is created if it needs to be created.
 * @param reportVariables ADVANCED: This is used to set the variables in the prepared statement for the report. This needs to be coordinated
 *                        with {@link #reportSql}. The available variables are "id, status, timestamp". If you're modifying to an update
 *                        statement like "update table tika_status set status=?, timestamp=? where id = ?"
 *                        then the values for this would be ["status", "timestamp", "id"].
 * @param reportWithinMs
 * @param cacheSize
 */
public record JDBCPipesReporterConfig(String connectionString, String reportSql, String tableName, boolean createTable, String postConnectionSql,
                                      List<String> reportVariables, long reportWithinMs, int cacheSize) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static JDBCPipesReporterConfig load(String json) throws IOException, TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, JDBCPipesReporterConfig.class);
        } catch (JacksonException e) {
            throw new TikaConfigException("problem w json", e);
        }
    }

    @JsonCreator
    public JDBCPipesReporterConfig(@JsonProperty("connectionString") String connectionString) {
        this(connectionString, null, JDBCPipesReporter.TABLE_NAME, true,
                null, List.of(), JDBCPipesReporter.DEFAULT_REPORT_WITHIN_MS, JDBCPipesReporter.DEFAULT_CACHE_SIZE);
    }
}
