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


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DBBuffer extends AbstractDBBuffer {

    private final PreparedStatement st;

    public DBBuffer(Connection connection, String tableName,
                    String idColumnName, String valueColumnName) throws SQLException {
        st = connection.prepareStatement("insert into "+tableName+ "( "+
                idColumnName + ", " + valueColumnName+") values (?,?);");
    }

    @Override
    public void write(int id, String value) throws RuntimeException {
        try {
            st.clearParameters();
            st.setInt(1, id);
            st.setString(2, value);
            st.execute();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws SQLException {
        st.close();

    }


}
