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
package org.apache.tika.pipes.emitter.jdbc;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.StringUtils;

/**
 * This is only an initial, basic implementation of an emitter for JDBC.
 * For now, it only processes the first metadata object in the list.
 * <p>
 * Later implementations may handle embedded files along the lines of
 * the OpenSearch/Solr emitters.
 */
public class JDBCEmitter extends AbstractEmitter implements Initializable, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCEmitter.class);
    private static volatile boolean INITIALIZED = false;
    //the "write" lock is used to make the connection and to configure the insertstatement
    //the "read" lock is used for preparing the insert and inserting
    private static ReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();
    private String connectionString;
    private String insert;
    private String createTable;
    private String idColumn;
    private Map<String, String> keys;
    private Connection connection;
    private PreparedStatement insertStatement;

    @Field
    public void setCreateTable(String createTable) {
        this.createTable = createTable;
    }

    @Field
    public void setInsert(String insert) {
        this.insert = insert;
    }

    @Field
    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }

    @Field
    public void setConnection(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * The implementation of keys should be a LinkedHashMap because
     * order matters!
     * <p>
     * Key is the name of the metadata field, value is the type of column:
     * boolean, string, int, long
     *
     * @param keys
     */
    @Field
    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaEmitterException {
        if (metadataList == null || metadataList.size() < 1) {
            return;
        }
        //we aren't currently batching inserts
        //because of risk of crashing in pipes handler.
        READ_WRITE_LOCK.readLock().lock();
        try {
            try {
                insertStatement.clearParameters();
                int i = 0;
                insertStatement.setString(++i, emitKey);
                for (Map.Entry<String, String> e : keys.entrySet()) {
                    updateValue(insertStatement, ++i, e.getKey(), e.getValue(), metadataList);
                }
                insertStatement.execute();
            } finally {
                READ_WRITE_LOCK.readLock().unlock();
            }
        } catch (SQLException e) {
            try {
                LOGGER.warn("problem during emit; going to try to reconnect", e);
                //something went wrong
                //try to reconnect
                reconnect();
            } catch (SQLException ex) {
                throw new TikaEmitterException("Couldn't reconnect!", ex);
            }
            throw new TikaEmitterException("couldn't emit", e);
        }
    }

    private void reconnect() throws SQLException {
        SQLException ex = null;
        try {
            READ_WRITE_LOCK.writeLock().lock();
            for (int i = 0; i < 3; i++) {
                try {
                    connection = DriverManager.getConnection(connectionString);
                    insertStatement = connection.prepareStatement(insert);
                    return;
                } catch (SQLException e) {
                    LOGGER.warn("couldn't reconnect to db", e);
                    ex = e;
                }
            }
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
        throw ex;
    }

    private void updateValue(PreparedStatement insertStatement, int i, String key, String type,
                             List<Metadata> metadataList) throws SQLException {
        //for now we're only taking the info from the container document.
        Metadata metadata = metadataList.get(0);
        String val = metadata.get(key);
        switch (type) {
            case "string":
                updateString(insertStatement, i, val);
                break;
            case "bool":
            case "boolean":
                updateBoolean(insertStatement, i, val);
                break;
            case "int":
            case "integer":
                updateInteger(insertStatement, i, val);
                break;
            case "long":
                updateLong(insertStatement, i, val);
                break;
            default:
                throw new IllegalArgumentException("Can only process: 'string', 'boolean', 'int' " +
                        "and 'long' types so far.  Please open a ticket to request other types");
        }
    }

    private void updateLong(PreparedStatement insertStatement, int i, String val)
            throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.BIGINT);
        } else {
            insertStatement.setLong(i, Long.parseLong(val));
        }
    }

    private void updateInteger(PreparedStatement insertStatement, int i, String val)
            throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.INTEGER);
        } else {
            insertStatement.setInt(i, Integer.parseInt(val));
        }
    }

    private void updateBoolean(PreparedStatement insertStatement, int i, String val)
            throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.BOOLEAN);
        } else {
            insertStatement.setBoolean(i, Boolean.parseBoolean(val));
        }
    }

    private void updateString(PreparedStatement insertStatement, int i, String val)
            throws SQLException {
        if (val == null) {
            insertStatement.setNull(i, Types.VARCHAR);
        } else {
            insertStatement.setString(i, val);
        }
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

        try {
            connection = DriverManager.getConnection(connectionString);
        } catch (SQLException e) {
            throw new TikaConfigException("couldn't open connection: " + connectionString, e);
        }
        try {
            READ_WRITE_LOCK.writeLock().lock();
            if (!INITIALIZED && !StringUtils.isBlank(createTable)) {
                try (Statement st = connection.createStatement()) {
                    st.execute(createTable);
                    INITIALIZED = true;
                } catch (SQLException e) {
                    throw new TikaConfigException("can't create table", e);
                }
            }
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
        try {
            insertStatement = connection.prepareStatement(insert);
        } catch (SQLException e) {
            throw new TikaConfigException("can't create insert statement", e);
        }

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        //require
    }

    /*
        TODO: This is currently not ever called.  We need rework the PipesParser
        to ensure that emitters are closed cleanly.
     */
    /**
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
