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
package org.apache.tika.pipes.fetchiterator.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.FetchId;
import org.apache.tika.pipes.fetcher.FetchIdMetadataPair;
import org.apache.tika.pipes.fetchiterator.FetchIterator;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class CSVFetchIterator extends FetchIterator implements Initializable {

    private Charset charset = StandardCharsets.UTF_8;
    private Path csvPath;
    private String fetchKeyColumn;


    @Field
    public void setCsvPath(String csvPath) {
        setCsvPath(Paths.get(csvPath));
    }

    @Field
    public void setFetchKeyColumn(String fetchKeyColumn) {
        this.fetchKeyColumn = fetchKeyColumn;
    }

    public void setCsvPath(Path csvPath) {
        this.csvPath = csvPath;
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        try (Reader reader = Files.newBufferedReader(csvPath, charset)) {
            Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(reader);
            int fetchKeyIndex = -1;
            List<String> headers = new ArrayList<>();
            for (CSVRecord record : records) {
                fetchKeyIndex = loadHeaders(record, headers);
                break;
            }
            //check that if a user set a fetchKeyColumn, but we didn't find it
            if (fetchKeyIndex < 0 && (fetchKeyColumn != null)) {
                throw new IllegalArgumentException("Can't find " + fetchKeyColumn +
                        " in the csv. I see:"+
                        headers);
            }
            for (CSVRecord record : records) {
                String fetchKey = "";
                if (fetchKeyIndex > -1) {
                    fetchKey = record.get(fetchKeyIndex);
                }
                Metadata metadata = loadMetadata(fetchKeyIndex, headers, record);
                tryToAdd(new FetchIdMetadataPair(new FetchId(fetcherName, fetchKey), metadata));
            }
        }
    }

    private Metadata loadMetadata(int fetchKeyIndex, List<String> headers, CSVRecord record) {
        Metadata metadata = new Metadata();
        for (int i = 0; i < record.size(); i++) {
            if (fetchKeyIndex == i) {
                continue;
            }
            metadata.set(headers.get(i), record.get(i));
        }
        return metadata;
    }


    private int loadHeaders(CSVRecord record, List<String> headers) {
        int fetchKeyColumnIndex = -1;

        for (int col = 0; col < record.size(); col++) {
            String header = record.get(col);
            headers.add(header);
            if (header.equals(fetchKeyColumn)) {
                fetchKeyColumnIndex = col;
            }
        }
        return fetchKeyColumnIndex;
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("csvPath", this.csvPath.toString());
    }
}
