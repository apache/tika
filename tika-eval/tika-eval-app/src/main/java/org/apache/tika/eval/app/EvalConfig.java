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
package org.apache.tika.eval.app;

import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

public class EvalConfig {

    private long minExtractLength = 0;
    private long maxExtractLength = 2_000_000;
    private String jdbcString = null;
    private String jdbcDriverClass = null;
    private boolean forceDrop = true;
    private int maxFilesToAdd = -1;
    private int maxTokens = 200000;

    private int maxContentLength = 5_000_000;
    private int numWorkers = 4;
    private Path errorLogFile = null;


    public static EvalConfig load(Path path) throws Exception {
        return new ObjectMapper().readValue(path.toFile(), EvalConfig.class);
    }

    public long getMinExtractLength() {
        return minExtractLength;
    }

    public long getMaxExtractLength() {
        return maxExtractLength;
    }

    public String getJdbcString() {
        return jdbcString;
    }

    public String getJdbcDriverClass() {
        return jdbcDriverClass;
    }

    public boolean isForceDrop() {
        return forceDrop;
    }

    public int getMaxFilesToAdd() {
        return maxFilesToAdd;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public int getNumWorkers() {
        return numWorkers;
    }

    public Path getErrorLogFile() {
        return errorLogFile;
    }

    @Override
    public String toString() {
        return "EvalConfig{" + "minExtractLength=" + minExtractLength + ", maxExtractLength=" + maxExtractLength + ", jdbcString='" + jdbcString + '\'' + ", jdbcDriverClass='" +
                jdbcDriverClass + '\'' + ", forceDrop=" + forceDrop + ", maxFilesToAdd=" + maxFilesToAdd + ", maxTokens=" + maxTokens + ", maxContentLength=" + maxContentLength +
                ", numThreads=" + numWorkers + ", errorLogFile=" + errorLogFile + '}';
    }
}
