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
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractEmitter;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

/**
 * Emitter to write parsed documents to a JDBC database.
 *
 * <p>Example JSON configuration:</p>
 * <pre>
 * {
 *   "emitters": {
 *     "jdbc-emitter": {
 *       "my-db": {
 *         "connection": "jdbc:postgresql://localhost/mydb",
 *         "createTable": "CREATE TABLE IF NOT EXISTS docs (path VARCHAR(1024), content TEXT)",
 *         "insert": "INSERT INTO docs (path, content) VALUES (?, ?)",
 *         "keys": {
 *           "tika:content": "string"
 *         },
 *         "attachmentStrategy": "FIRST_ONLY",
 *         "multivaluedFieldStrategy": "CONCATENATE"
 *       }
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * This is only an initial, basic implementation of an emitter for JDBC.
 * It is currently NOT thread safe because of the shared prepared statement,
 * and depending on the jdbc implementation because of the shared connection.
 * </p>
 */
public class JDBCEmitter extends AbstractEmitter implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCEmitter.class);

    private static final String[] TIKA_DATE_PATTERNS =
            new String[]{"yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss"};
    private static final ReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();
    private static final Set<String> TABLES_CREATED = new HashSet<>();

    private final JDBCEmitterConfig config;
    private final JDBCEmitterConfig.AttachmentStrategy attachmentStrategy;
    private final JDBCEmitterConfig.MultivaluedFieldStrategy multivaluedFieldStrategy;
    private final List<ColumnDefinition> columns;
    private final DateFormat[] dateFormats;
    private final StringNormalizer stringNormalizer;

    private Connection connection;
    private PreparedStatement insertStatement;

    public static JDBCEmitter build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        JDBCEmitterConfig config = JDBCEmitterConfig.load(extensionConfig.json());
        config.validate();
        return new JDBCEmitter(extensionConfig, config);
    }

    private JDBCEmitter(ExtensionConfig extensionConfig, JDBCEmitterConfig config) throws TikaConfigException, IOException {
        super(extensionConfig);
        this.config = config;
        this.attachmentStrategy = config.getAttachmentStrategyEnum();
        this.multivaluedFieldStrategy = config.getMultivaluedFieldStrategyEnum();
        this.columns = parseColTypes(config);
        this.dateFormats = new DateFormat[TIKA_DATE_PATTERNS.length];
        for (int i = 0; i < TIKA_DATE_PATTERNS.length; i++) {
            dateFormats[i] = new SimpleDateFormat(TIKA_DATE_PATTERNS[i], Locale.US);
        }
        this.stringNormalizer = config.connection().startsWith("jdbc:postgres")
                ? new PostgresNormalizer() : new StringNormalizer();

        initialize();
    }

    private void initialize() throws TikaConfigException {
        try {
            createConnection();
        } catch (SQLException e) {
            throw new TikaConfigException("couldn't open connection: " + config.connection(), e);
        }

        if (!StringUtils.isBlank(config.createTable())) {
            READ_WRITE_LOCK.writeLock().lock();
            try {
                String tableCreationString = config.connection() + " " + config.createTable();
                if (!TABLES_CREATED.contains(tableCreationString)) {
                    try (Statement st = connection.createStatement()) {
                        st.execute(config.createTable());
                        if (!StringUtils.isBlank(config.alterTable())) {
                            st.execute(config.alterTable());
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
            insertStatement = connection.prepareStatement(config.insert());
        } catch (SQLException e) {
            throw new TikaConfigException("can't create insert statement", e);
        }
    }

    private static List<ColumnDefinition> parseColTypes(JDBCEmitterConfig config) {
        List<ColumnDefinition> columns = new ArrayList<>();
        for (Map.Entry<String, String> e : config.keys().entrySet()) {
            columns.add(ColumnDefinition.parse(e.getKey(), e.getValue(), config.getEffectiveMaxStringLength()));
        }
        return columns;
    }

    private void createConnection() throws SQLException {
        connection = DriverManager.getConnection(config.connection());
        connection.setAutoCommit(false);
        if (!StringUtils.isBlank(config.postConnection())) {
            try (Statement st = connection.createStatement()) {
                st.execute(config.postConnection());
            }
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            return;
        }
        emitWithRetry(emitKey, metadataList);
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException {
        for (EmitData d : emitData) {
            emit(d.getEmitKey(), d.getMetadataList(), d.getParseContext());
        }
    }

    private void emitWithRetry(String emitKey, List<Metadata> metadataList) throws IOException {
        int tries = 0;
        Exception ex = null;
        while (tries++ <= config.maxRetries()) {
            try {
                emitNow(emitKey, metadataList);
                return;
            } catch (SQLException e) {
                try {
                    reconnect();
                } catch (SQLException exc) {
                    throw new IOException("couldn't reconnect!", exc);
                }
                ex = e;
            }
        }
        throw new IOException("Couldn't emit record for key: " + emitKey, ex);
    }

    private void emitNow(String emitKey, List<Metadata> metadataList) throws SQLException {
        if (attachmentStrategy == JDBCEmitterConfig.AttachmentStrategy.FIRST_ONLY) {
            insertFirstOnly(emitKey, metadataList);
            insertStatement.addBatch();
        } else {
            insertAll(emitKey, metadataList);
        }
        if (LOGGER.isDebugEnabled()) {
            long start = System.currentTimeMillis();
            insertStatement.executeBatch();
            connection.commit();
            LOGGER.debug("took {}ms to insert row for key: {}", System.currentTimeMillis() - start, emitKey);
        } else {
            insertStatement.executeBatch();
            connection.commit();
        }
    }

    private void insertAll(String emitKey, List<Metadata> metadataList) throws SQLException {
        for (int i = 0; i < metadataList.size(); i++) {
            insertStatement.clearParameters();
            int col = 0;
            insertStatement.setString(++col, emitKey);
            insertStatement.setInt(++col, i);
            for (ColumnDefinition columnDefinition : columns) {
                updateValue(emitKey, insertStatement, ++col, columnDefinition, i, metadataList);
            }
            insertStatement.addBatch();
        }
    }

    private void insertFirstOnly(String emitKey, List<Metadata> metadataList) throws SQLException {
        insertStatement.clearParameters();
        int i = 0;
        insertStatement.setString(++i, emitKey);
        for (ColumnDefinition columnDefinition : columns) {
            updateValue(emitKey, insertStatement, ++i, columnDefinition, 0, metadataList);
        }
    }

    private void reconnect() throws SQLException {
        SQLException ex = null;
        for (int i = 0; i < 3; i++) {
            try {
                tryClose();
                createConnection();
                insertStatement = connection.prepareStatement(config.insert());
                return;
            } catch (SQLException e) {
                LOGGER.warn("couldn't reconnect to db", e);
                ex = e;
            }
        }
        throw ex;
    }

    private void tryClose() {
        if (insertStatement != null) {
            try {
                insertStatement.close();
            } catch (SQLException e) {
                LOGGER.warn("exception closing insert", e);
            }
        }
        if (connection != null) {
            try {
                connection.commit();
                connection.close();
            } catch (SQLException e) {
                LOGGER.warn("exception closing connection", e);
            }
        }
    }

    private void updateValue(String emitKey, PreparedStatement insertStatement, int i,
                             ColumnDefinition columnDefinition, int metadataListIndex,
                             List<Metadata> metadataList) throws SQLException {
        Metadata metadata = metadataList.get(metadataListIndex);
        String val = getVal(metadata, columnDefinition);
        switch (columnDefinition.getType()) {
            case Types.VARCHAR:
                updateVarchar(emitKey, columnDefinition, insertStatement, i, val);
                break;
            case Types.BOOLEAN:
                updateBoolean(insertStatement, i, val);
                break;
            case Types.INTEGER:
                updateInteger(insertStatement, i, val);
                break;
            case Types.BIGINT:
                updateLong(insertStatement, i, val);
                break;
            case Types.FLOAT:
                updateFloat(insertStatement, i, val);
                break;
            case Types.DOUBLE:
                updateDouble(insertStatement, i, val);
                break;
            case Types.TIMESTAMP:
                updateTimestamp(insertStatement, i, val, dateFormats);
                break;
            default:
                throw new IllegalArgumentException("Can only process: " + getHandledTypes() +
                        " types so far. Please open a ticket to request: " +
                        columnDefinition.getType() + " for " + columnDefinition.getColumnName());
        }
    }

    private String getVal(Metadata metadata, ColumnDefinition columnDefinition) {
        if (columnDefinition.getType() != Types.VARCHAR) {
            return metadata.get(columnDefinition.getColumnName());
        }
        if (multivaluedFieldStrategy == JDBCEmitterConfig.MultivaluedFieldStrategy.FIRST_ONLY) {
            return metadata.get(columnDefinition.getColumnName());
        }
        String[] vals = metadata.getValues(columnDefinition.getColumnName());
        if (vals.length == 0) {
            return null;
        } else if (vals.length == 1) {
            return vals[0];
        }

        int j = 0;
        StringBuilder sb = new StringBuilder();
        for (String val : vals) {
            if (StringUtils.isBlank(val)) {
                continue;
            }
            if (j > 0) {
                sb.append(config.multivaluedFieldDelimiter());
            }
            sb.append(val);
            j++;
        }
        return sb.toString();
    }

    private void updateDouble(PreparedStatement insertStatement, int i, String val) throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.DOUBLE);
            return;
        }
        insertStatement.setDouble(i, Double.parseDouble(val));
    }

    private void updateVarchar(String emitKey, ColumnDefinition columnDefinition,
                               PreparedStatement insertStatement, int i, String val) throws SQLException {
        if (val == null) {
            insertStatement.setNull(i, Types.VARCHAR);
            return;
        }
        String normalized = stringNormalizer.normalize(emitKey,
                columnDefinition.getColumnName(), val, columnDefinition.getPrecision());
        insertStatement.setString(i, normalized);
    }

    private void updateTimestamp(PreparedStatement insertStatement, int i, String val,
                                 DateFormat[] dateFormats) throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.TIMESTAMP);
            return;
        }
        for (DateFormat df : dateFormats) {
            try {
                Date d = df.parse(val);
                insertStatement.setTimestamp(i, new Timestamp(d.getTime()));
                return;
            } catch (ParseException e) {
                // ignore
            }
        }
        LOGGER.warn("Couldn't parse {}", val);
        insertStatement.setNull(i, Types.TIMESTAMP);
    }

    private void updateFloat(PreparedStatement insertStatement, int i, String val) throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.FLOAT);
        } else {
            insertStatement.setFloat(i, Float.parseFloat(val));
        }
    }

    private void updateLong(PreparedStatement insertStatement, int i, String val) throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.BIGINT);
        } else {
            insertStatement.setLong(i, Long.parseLong(val));
        }
    }

    private void updateInteger(PreparedStatement insertStatement, int i, String val) throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.INTEGER);
        } else {
            insertStatement.setInt(i, Integer.parseInt(val));
        }
    }

    private void updateBoolean(PreparedStatement insertStatement, int i, String val) throws SQLException {
        if (StringUtils.isBlank(val)) {
            insertStatement.setNull(i, Types.BOOLEAN);
        } else {
            insertStatement.setBoolean(i, Boolean.parseBoolean(val));
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (insertStatement != null) {
                insertStatement.close();
            }
        } catch (SQLException e) {
            LOGGER.warn("problem closing insert", e);
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private static String getHandledTypes() {
        return "'string', 'varchar', 'boolean', 'int', 'long', 'float', 'double' and 'timestamp'";
    }

    private static class StringNormalizer {
        String normalize(String emitKey, String columnName, String s, int maxLength) {
            if (maxLength < 0 || s.length() <= maxLength) {
                return s;
            }
            LOGGER.warn("truncating {}->'{}' from {} chars to {} chars",
                    emitKey, columnName, s.length(), maxLength);
            return s.substring(0, maxLength);
        }
    }

    private static class PostgresNormalizer extends StringNormalizer {
        @Override
        String normalize(String emitKey, String columnName, String s, int maxLength) {
            s = s.replaceAll("\u0000", " ");
            return super.normalize(emitKey, columnName, s, maxLength);
        }
    }

    private static class ColumnDefinition {
        private static final Matcher VARCHAR_MATCHER = Pattern.compile("varchar\\((\\d+)\\)").matcher("");

        private final String columnName;
        private final int type;
        private final int precision;

        private static ColumnDefinition parse(String name, String type, int maxStringLength) {
            String lcType = type.toLowerCase(Locale.US);
            if (VARCHAR_MATCHER.reset(lcType).find()) {
                return new ColumnDefinition(name, Types.VARCHAR, Integer.parseInt(VARCHAR_MATCHER.group(1)));
            }

            switch (lcType) {
                case "string":
                    return new ColumnDefinition(name, Types.VARCHAR, maxStringLength);
                case "bool":
                case "boolean":
                    return new ColumnDefinition(name, Types.BOOLEAN, -1);
                case "int":
                case "integer":
                    return new ColumnDefinition(name, Types.INTEGER, -1);
                case "bigint":
                case "long":
                    return new ColumnDefinition(name, Types.BIGINT, -1);
                case "float":
                    return new ColumnDefinition(name, Types.FLOAT, -1);
                case "double":
                    return new ColumnDefinition(name, Types.DOUBLE, -1);
                case "timestamp":
                    return new ColumnDefinition(name, Types.TIMESTAMP, -1);
                default:
                    throw new IllegalArgumentException("Can only process: " + getHandledTypes() +
                            " types so far. Please open a ticket to request " + type + " for column: " + name);
            }
        }

        private ColumnDefinition(String columnName, int type, int precision) {
            this.columnName = columnName;
            this.type = type;
            this.precision = precision;
        }

        public String getColumnName() {
            return columnName;
        }

        public int getType() {
            return type;
        }

        public int getPrecision() {
            return precision;
        }
    }
}
