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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.StringUtils;

/**
 * This is only an initial, basic implementation of an emitter for JDBC.
 * <p>
 * It is currently NOT thread safe because of the shared prepared statement,
 * and depending on the jdbc implementation because of the shared connection.
 * <p>
 * As of the 2.5.0 release, this is ALPHA version.  There may be breaking changes
 * in the future.
 */
public class JDBCEmitter extends AbstractEmitter implements Initializable, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCEmitter.class);
    //the "write" lock is used for creating the table
    private static ReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();
    //this keeps track of which table + connection string have been created
    //so that only one table is created per table + connection string.
    //This is necessary for testing and if someone specifies multiple
    //different jdbc emitters.
    private static Set<String> TABLES_CREATED = new HashSet<>();
    private String connectionString;
    private String insert;
    private String createTable;
    private String alterTable;
    private Map<String, String> keys;
    private Connection connection;
    private PreparedStatement insertStatement;
    private AttachmentStrategy attachmentStrategy = AttachmentStrategy.FIRST_ONLY;

    /**
     * This is called immediately after the table is created.
     * The purpose of this is to allow for adding a complex primary key or
     * other constraint on the table after it is created.
     *
     * @param alterTable
     */
    public void setAlterTable(String alterTable) {
        this.alterTable = alterTable;
    }

    @Field
    public void setCreateTable(String createTable) {
        this.createTable = createTable;
    }

    @Field
    public void setInsert(String insert) {
        this.insert = insert;
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

    public void setAttachmentStrategy(AttachmentStrategy attachmentStrategy) {
        this.attachmentStrategy = attachmentStrategy;
    }

    @Field
    public void setAttachmentStrategy(String attachmentStrategy) {
        if ("all".equalsIgnoreCase(attachmentStrategy)) {
            setAttachmentStrategy(AttachmentStrategy.ALL);
        } else if ("first_only".equalsIgnoreCase(attachmentStrategy)) {
            setAttachmentStrategy(AttachmentStrategy.FIRST_ONLY);
        } else {
            throw new IllegalArgumentException("attachmentStrategy must be 'all' or 'first_only'");
        }
    }

    /**
     * This executes the emit with each call.  For more efficient
     * batch execution use {@link #emit(List)}.
     *
     * @param emitKey emit key
     * @param metadataList list of metadata per file
     * @throws IOException
     * @throws TikaEmitterException
     */
    @Override
    public void emit(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaEmitterException {
        if (metadataList == null || metadataList.size() < 1) {
            return;
        }
        try {
            if (attachmentStrategy == AttachmentStrategy.FIRST_ONLY) {
                insertFirstOnly(emitKey, metadataList);
                insertStatement.execute();
            } else {
                insertAll(emitKey, metadataList);
                insertStatement.executeBatch();
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

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {
        try {
            if (attachmentStrategy == AttachmentStrategy.FIRST_ONLY) {
                for (EmitData d : emitData) {
                    insertFirstOnly(d.getEmitKey().getEmitKey(), d.getMetadataList());
                    insertStatement.addBatch();
                }
            } else {
                for (EmitData d : emitData) {
                    insertAll(d.getEmitKey().getEmitKey(), d.getMetadataList());
                }
            }
            insertStatement.executeBatch();
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

    private void insertAll(String emitKey, List<Metadata> metadataList) throws SQLException {
        for (int i = 0; i < metadataList.size(); i++) {
            insertStatement.clearParameters();
            int col = 0;
            insertStatement.setString(++col, emitKey);
            insertStatement.setInt(++col, i);
            for (Map.Entry<String, String> e : keys.entrySet()) {
                updateValue(insertStatement, ++col, e.getKey(), e.getValue(), i, metadataList);
            }
            insertStatement.addBatch();
        }
    }

    private void insertFirstOnly(String emitKey, List<Metadata> metadataList) throws SQLException {
        insertStatement.clearParameters();
        int i = 0;
        insertStatement.setString(++i, emitKey);
        for (Map.Entry<String, String> e : keys.entrySet()) {
            updateValue(insertStatement, ++i, e.getKey(), e.getValue(), 0, metadataList);
        }
    }

    private void reconnect() throws SQLException {
        SQLException ex = null;
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
        throw ex;
    }

    private void updateValue(PreparedStatement insertStatement, int i, String key, String type,
                             int metadataListIndex, List<Metadata> metadataList)
            throws SQLException {
        //for now we're only taking the info from the container document.
        Metadata metadata = metadataList.get(metadataListIndex);
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
            case "float":
                updateFloat(insertStatement, i, val);
                break;
            default:
                throw new IllegalArgumentException("Can only process: 'string', 'boolean', 'int' " +
                        "and 'long' types so far.  Please open a ticket to request other types");
        }
    }

    private void updateFloat(PreparedStatement insertStatement, int i, String val)
            throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.FLOAT);
        } else {
            insertStatement.setFloat(i, Float.parseFloat(val));
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
        if (!StringUtils.isBlank(createTable)) {
            //synchronize table creation
            READ_WRITE_LOCK.writeLock().lock();
            try {
                String tableCreationString = connectionString + " " + createTable;
                if (!TABLES_CREATED.contains(tableCreationString)) {
                    try (Statement st = connection.createStatement()) {
                        st.execute(createTable);
                        if (!StringUtils.isBlank(alterTable)) {
                            st.execute(alterTable);
                        }
                        TABLES_CREATED.add(tableCreationString);
                    } catch (SQLException e) {
                        throw new TikaConfigException("can't create table", e);
                    }
                }
            } finally {
                READ_WRITE_LOCK.writeLock().unlock();
            }
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

    /*
        TODO: This is currently not ever called.  We need rework the PipesParser
        to ensure that emitters are closed cleanly.
     */

    public enum AttachmentStrategy {
        FIRST_ONLY, ALL
        //anything else?
    }
}
