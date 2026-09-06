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
package org.apache.tika.eval.app.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JDBCUtilTest {

    private String originalCacheSize;

    @BeforeEach
    public void stashCacheSizeProperty() {
        originalCacheSize = System.getProperty(JDBCUtil.H2_CACHE_SIZE_KB_PROPERTY);
        System.clearProperty(JDBCUtil.H2_CACHE_SIZE_KB_PROPERTY);
    }

    @AfterEach
    public void restoreCacheSizeProperty() {
        if (originalCacheSize == null) {
            System.clearProperty(JDBCUtil.H2_CACHE_SIZE_KB_PROPERTY);
        } else {
            System.setProperty(JDBCUtil.H2_CACHE_SIZE_KB_PROPERTY, originalCacheSize);
        }
    }

    @Test
    public void testJdbcStringPassesThrough() {
        String jdbc = "jdbc:postgresql://localhost/tika_eval";
        assertEquals(jdbc, JDBCUtil.getJdbcConnectionString(jdbc));
    }

    @Test
    public void testH2Defaults() {
        long cacheSizeKb = JDBCUtil.getH2CacheSizeKb();
        assertTrue(cacheSizeKb >= 65_536L && cacheSizeKb <= 1_048_576L, "clamped to [64MB, 1GB]: " + cacheSizeKb);
        String connectionString = JDBCUtil.getJdbcConnectionString("mydb");
        assertTrue(connectionString.startsWith("jdbc:h2:file:"), connectionString);
        assertTrue(connectionString.endsWith(";RETENTION_TIME=0;CACHE_SIZE=" + cacheSizeKb), connectionString);
    }

    @Test
    public void testH2CacheSizeOverrideIsUnclamped() {
        System.setProperty(JDBCUtil.H2_CACHE_SIZE_KB_PROPERTY, "8388608");
        assertEquals(8_388_608L, JDBCUtil.getH2CacheSizeKb());
    }
}
