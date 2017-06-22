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

package org.apache.tika.eval.db;


import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCUtil {
    private static final Logger LOG = LoggerFactory.getLogger(JDBCUtil.class);

    public enum CREATE_TABLE {
        DROP_IF_EXISTS,
        SKIP_IF_EXISTS,
        THROW_EX_IF_EXISTS,
    }

    private final String connectionString;
    private String driverClass;

    public JDBCUtil(String connectionString, String driverClass) {
        this.connectionString = connectionString;
        this.driverClass = driverClass;
        if (driverClass == null || driverClass.length() == 0) {
            if (System.getProperty("jdbc.drivers") != null) {
                //user has specified it on the command line
                //stop now
            } else {
                //try to use the mappings in db.properties to determine the class
                try (InputStream is = JDBCUtil.class.getResourceAsStream("/db.properties")) {
                    Properties properties = new Properties();
                    properties.load(is);
                    for (String k : properties.stringPropertyNames()) {
                        Matcher m = Pattern.compile("(?i)jdbc:"+k).matcher(connectionString);
                        if (m.find()) {
                            this.driverClass = properties.getProperty(k);
                        }
                    }

                } catch (IOException e) {

                }
            }
        }
    }

    /**
     * Override this any optimizations you want to do on the db
     * before writing/reading.
     *
     * @return
     * @throws IOException
     */
    public Connection getConnection() throws SQLException {
        String connectionString = getConnectionString();
        Connection conn = null;
        String jdbcDriver = getJDBCDriverClass();
        if (jdbcDriver != null) {
            try {
                Class.forName(getJDBCDriverClass());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        conn = DriverManager.getConnection(connectionString);
        conn.setAutoCommit(false);

        return conn;
    }

    /**
     * JDBC driver class.  Override as necessary.
     * @return
     */
    public String getJDBCDriverClass() {
        return driverClass;
    }


    public boolean dropTableIfExists(Connection conn, String tableName) throws SQLException {
        if (containsTable(tableName)) {
            try (Statement st = conn.createStatement()) {
                String sql = "drop table " + tableName;
                return st.execute(sql);
            }
        }
        return true;
    }


    public String getConnectionString() {
        return connectionString;
    }


    public Set<String> getTables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();

        DatabaseMetaData dbMeta = connection.getMetaData();

        try (ResultSet rs = dbMeta.getTables(null, null, "%", null)) {
            while (rs.next()) {
                tables.add(rs.getString(3).toLowerCase(Locale.US));
            }
        }
        return tables;
    }

    public static int insert(PreparedStatement insertStatement,
                             TableInfo table,
                             Map<Cols, String> data) throws SQLException {

        //clear parameters before setting
        insertStatement.clearParameters();
        try {
            int i = 1;
            for (ColInfo colInfo : table.getColInfos()) {
                updateInsertStatement(i, insertStatement, colInfo, data.get(colInfo.getName()));
                i++;
            }
            for (Cols c : data.keySet()) {
                if (!table.containsColumn(c)) {
                    throw new IllegalArgumentException("Can't add data to " + c +
                            " because it doesn't exist in the table: " + table.getName());
                }
            }
            return insertStatement.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("couldn't insert data for this row: {}", e.getMessage());
            return -1;
        }
    }

    public static void updateInsertStatement(int dbColOffset, PreparedStatement st,
                                             ColInfo colInfo, String value) throws SQLException {
        if (value == null) {
            st.setNull(dbColOffset, colInfo.getType());
            return;
        }
        try {
            switch (colInfo.getType()) {
                case Types.VARCHAR:
                    if (value != null && value.length() > colInfo.getPrecision()) {
                        value = value.substring(0, colInfo.getPrecision());
                        LOG.warn("truncated varchar value in {} : {}", colInfo.getName(), value);
                    }
                    st.setString(dbColOffset, value);
                    break;
                case Types.CHAR:
                    st.setString(dbColOffset, value);
                    break;
                case Types.DOUBLE:
                    st.setDouble(dbColOffset, Double.parseDouble(value));
                    break;
                case Types.FLOAT:
                    st.setDouble(dbColOffset, Float.parseFloat(value));
                    break;
                case Types.INTEGER:
                    st.setInt(dbColOffset, Integer.parseInt(value));
                    break;
                case Types.BIGINT:
                    st.setLong(dbColOffset, Long.parseLong(value));
                    break;
                case Types.BOOLEAN:
                    st.setBoolean(dbColOffset, Boolean.parseBoolean(value));
                    break;
                default:
                    throw new UnsupportedOperationException("Don't yet support type: " + colInfo.getType());
            }
        } catch (NumberFormatException e) {
            if (!"".equals(value)) {
                LOG.warn("number format exception: {} : {}", colInfo.getName(), value);
            }
            st.setNull(dbColOffset, colInfo.getType());
        } catch (SQLException e) {
            LOG.warn("sqlexception: {} : {}", colInfo, value);
            st.setNull(dbColOffset, colInfo.getType());
        }
    }

    public void createTables(List<TableInfo> tableInfos, CREATE_TABLE createTable) throws SQLException, IOException {

        try (Connection conn = getConnection ()) {
            for (TableInfo tableInfo : tableInfos) {

                if (createTable.equals(CREATE_TABLE.DROP_IF_EXISTS)) {
                    dropTableIfExists(conn, tableInfo.getName());
                } else if (createTable.equals(CREATE_TABLE.SKIP_IF_EXISTS)) {
                    if (containsTable(tableInfo.getName())) {
                        continue;
                    }
                }
                createTable(conn, tableInfo);
            }
            conn.commit();
        }
    }

    public boolean containsTable(String tableName) throws SQLException {
        try (Connection connection = getConnection()) {
            Set<String> tables = getTables(connection);
            if (tables.contains(normalizeTableName(tableName))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Override for custom behavior
     * @param tableName
     * @return
     */
    String normalizeTableName(String tableName) {
        tableName = tableName.toLowerCase(Locale.US);
        return tableName;
    }

    //does not close the connection
    private void createTable(Connection conn, TableInfo tableInfo) throws SQLException {
        StringBuilder createSql = new StringBuilder();
        createSql.append("CREATE TABLE " + tableInfo.getName());
        createSql.append("(");

        int last = 0;
        for (ColInfo col : tableInfo.getColInfos()) {
            last++;
            if (last > 1) {
                createSql.append(", ");
            }
            createSql.append(col.getName());
            createSql.append(" ");
            createSql.append(col.getSqlDef());
            String constraints = col.getConstraints();
            if (constraints != null) {
                createSql.append(" ");
                createSql.append(constraints);
            }
        }
        createSql.append(")");
        try (Statement st = conn.createStatement()) {
            st.execute(createSql.toString());
            st.close();
        }
        conn.commit();
    }
}
