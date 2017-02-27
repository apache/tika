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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.tika.io.IOExceptionWithCause;

public abstract class DBUtil {

    public static Logger logger = Logger.getLogger(DBUtil.class);
    public abstract String getJDBCDriverClass();
    public abstract boolean dropTableIfExists(Connection conn, String tableName) throws SQLException;
    private final Path db;
    public DBUtil(Path db) {
        this.db = db;
    }

    /**
     * This is intended for a file/directory based db.
     * <p>
     * Override this any optimizations you want to do on the db
     * before writing/reading.
     *
     * @return
     * @throws IOException
     */
    public Connection getConnection(boolean createIfDoesntExist) throws IOException {
        String connectionString = getConnectionString(db, createIfDoesntExist);
        Connection conn = null;
        try {
            try {
                Class.forName(getJDBCDriverClass());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            conn = DriverManager.getConnection(connectionString);
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        return conn;
    }

    abstract public String getConnectionString(Path db, boolean createIfDoesntExist);

    /**
     *
     * @param connection
     * @return a list of uppercased table names
     * @throws SQLException
     */
    abstract public Set<String> getTables(Connection connection) throws SQLException;

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
                if (! table.containsColumn(c)) {
                    throw new IllegalArgumentException("Can't add data to "+c +
                    " because it doesn't exist in the table: "+table.getName());
                }
            }
            return insertStatement.executeUpdate();
        } catch (SQLException e) {
            logger.warn("couldn't insert data for this row: "+e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public static void updateInsertStatement(int dbColOffset, PreparedStatement st,
                                             ColInfo colInfo, String value ) throws SQLException {
        if (value == null) {
            st.setNull(dbColOffset, colInfo.getType());
            return;
        }
        try {
            switch (colInfo.getType()) {
                case Types.VARCHAR:
                    if (value != null && value.length() > colInfo.getPrecision()) {
                        value = value.substring(0, colInfo.getPrecision());
                        logger.warn("truncated varchar value in " + colInfo.getName() + " : "+value);
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
            if (! "".equals(value)) {
                logger.warn("number format exception: " + colInfo.getName() + " : " + value);
            }
            st.setNull(dbColOffset, colInfo.getType());
        } catch (SQLException e) {
            logger.warn("sqlexception: "+colInfo+ " : " + value);
            st.setNull(dbColOffset, colInfo.getType());
        }
    }

    public void createDB(List<TableInfo> tableInfos, boolean append) throws SQLException, IOException {
        Connection conn = getConnection(true);
        Set<String> tables = getTables(conn);

        for (TableInfo tableInfo : tableInfos) {

            if (append && tables.contains(tableInfo.getName().toUpperCase(Locale.ROOT))) {
                continue;
            }
            if (! append) {
                dropTableIfExists(conn, tableInfo.getName());
            }
            createTable(conn, tableInfo);
        }

        conn.commit();
        conn.close();
    }

    private void createTable(Connection conn, TableInfo tableInfo) throws SQLException {
        StringBuilder createSql = new StringBuilder();
        createSql.append("CREATE TABLE "+tableInfo.getName());
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
        Statement st = conn.createStatement();
        st.execute(createSql.toString());

        st.close();
        conn.commit();
    }
}
