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
package org.apache.tika.pipes.iterator.jdbc;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.pipesiterator.PipesIteratorConfig;

public class JDBCPipesIteratorConfig extends PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static JDBCPipesIteratorConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    JDBCPipesIteratorConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse JDBCPipesIteratorConfig from JSON", e);
        }
    }

    private String idColumn;
    private String fetchKeyColumn;
    private String fetchKeyRangeStartColumn;
    private String fetchKeyRangeEndColumn;
    private String emitKeyColumn;
    private String connection;
    private String select;
    private int fetchSize = -1;
    private int queryTimeoutSeconds = -1;

    public String getIdColumn() {
        return idColumn;
    }

    public String getFetchKeyColumn() {
        return fetchKeyColumn;
    }

    public String getFetchKeyRangeStartColumn() {
        return fetchKeyRangeStartColumn;
    }

    public String getFetchKeyRangeEndColumn() {
        return fetchKeyRangeEndColumn;
    }

    public String getEmitKeyColumn() {
        return emitKeyColumn;
    }

    public String getConnection() {
        return connection;
    }

    public String getSelect() {
        return select;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JDBCPipesIteratorConfig that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return fetchSize == that.fetchSize &&
                queryTimeoutSeconds == that.queryTimeoutSeconds &&
                Objects.equals(idColumn, that.idColumn) &&
                Objects.equals(fetchKeyColumn, that.fetchKeyColumn) &&
                Objects.equals(fetchKeyRangeStartColumn, that.fetchKeyRangeStartColumn) &&
                Objects.equals(fetchKeyRangeEndColumn, that.fetchKeyRangeEndColumn) &&
                Objects.equals(emitKeyColumn, that.emitKeyColumn) &&
                Objects.equals(connection, that.connection) &&
                Objects.equals(select, that.select);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(idColumn);
        result = 31 * result + Objects.hashCode(fetchKeyColumn);
        result = 31 * result + Objects.hashCode(fetchKeyRangeStartColumn);
        result = 31 * result + Objects.hashCode(fetchKeyRangeEndColumn);
        result = 31 * result + Objects.hashCode(emitKeyColumn);
        result = 31 * result + Objects.hashCode(connection);
        result = 31 * result + Objects.hashCode(select);
        result = 31 * result + fetchSize;
        result = 31 * result + queryTimeoutSeconds;
        return result;
    }
}
