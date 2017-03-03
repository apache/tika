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

package org.apache.tika.eval.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;

import org.apache.tika.eval.db.H2Util;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ResultsReporterTest {
    private Path configFile;
    private Path tmpDir;
    private String dbName = "test-db";
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        configFile = Paths.get(this.getClass().getResource("/reports.xml").toURI());
        tmpDir = Files.createTempDirectory("tika-eval-report-test-");

        connection = new H2Util(tmpDir.resolve(dbName)).getConnection();
        String sql = "CREATE TABLE test_table (ID LONG PRIMARY KEY, STRING VARCHAR(32))";
        Statement st = connection.createStatement();
        st.execute(sql);
        sql = "INSERT into test_table values ( 100000, 'the quick brown')";
        st.execute(sql);
        sql = "INSERT into test_table values (123456789, 'fox jumped over')";
        st.execute(sql);
        connection.commit();
    }

    @Test
    @Ignore("add a real test here")
    public void testBuilder() throws Exception {
        ResultsReporter r = ResultsReporter.build(configFile);
        r.execute(connection, Paths.get("reports"));
        System.out.println("finished: "+ tmpDir.toString());
    }
}
