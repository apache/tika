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
package org.apache.tika.fetcher.jdbc;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fetcher.Fetcher;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JDBCFetcher implements Fetcher, Initializable {

    private int requestPoolSize = 10;
    private String table;
    private List<String> metadataColumns = new ArrayList<>();
    private String binaryField;
    private String connectionString;


    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //TODO: init prepared
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        //no-op
    }

    @Override
    public Set<String> getSupportedPrefixes() {
        return null;
    }

    @Override
    public InputStream fetch(String fetchString, Metadata metadata) throws TikaException, IOException {
        return null;
    }

    /**
     * The prepared inserts are pooled. The default is 10.
     * This sets how big this pool should be.
     * @param requestPoolSize
     */
    @Field
    public void setRequestPoolSize(int requestPoolSize) {
        this.requestPoolSize = requestPoolSize;
    }

    @Field
    public void setJDBCConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    @Field
    public void setTable(String table) {
        this.table = table;
    }

    @Field
    public void setMetadataFields(List<String> cols) {

    }

    /**
     * If there's a blob or text field that you
     * want Tika to parse.
     *
     * This is optional.
     * @param binaryField
     */
    @Field
    public void setBinaryField(String binaryField) {

    }
}
