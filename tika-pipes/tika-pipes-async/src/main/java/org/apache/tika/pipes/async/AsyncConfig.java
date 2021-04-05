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
package org.apache.tika.pipes.async;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.utils.StringUtils;

public class AsyncConfig {

    private final int queueSize = 1000;
    private final int numWorkers = 10;
    private final int numEmitters = 1;
    private String jdbcString;
    private Path dbDir;

    public static AsyncConfig load(Path p) throws IOException {
        AsyncConfig asyncConfig = new AsyncConfig();

        if (StringUtils.isBlank(asyncConfig.getJdbcString())) {
            asyncConfig.dbDir = Files.createTempDirectory("tika-async-");
            Path dbFile = asyncConfig.dbDir.resolve("tika-async");
            asyncConfig.setJdbcString(
                    "jdbc:h2:file:" + dbFile.toAbsolutePath().toString() + ";AUTO_SERVER=TRUE");
        } else {
            asyncConfig.dbDir = null;
        }
        return asyncConfig;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getNumWorkers() {
        return numWorkers;
    }

    public int getNumEmitters() {
        return numEmitters;
    }

    public String getJdbcString() {
        return jdbcString;
    }

    public void setJdbcString(String jdbcString) {
        this.jdbcString = jdbcString;
    }

    /**
     * If no jdbc connection was specified, this
     * dir contains the h2 database.  Otherwise, null.
     *
     * @return
     */
    public Path getTempDBDir() {
        return dbDir;
    }
}
