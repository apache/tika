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
package org.apache.tika.pipes.fetchers.filesystem;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;

import org.apache.tika.pipes.core.iterators.PipeInput;
import org.apache.tika.pipes.core.iterators.PipeIterator;
import org.apache.tika.pipes.core.iterators.PipeIteratorConfig;
import org.apache.tika.pipes.core.iterators.TikaPipeIteratorException;

@Extension
public class CsvPipeIterator implements PipeIterator {
    private CsvPipeIteratorConfig csvPipeIteratorConfig;

    @Override
    public <T extends PipeIteratorConfig> void init(T config) {
        this.csvPipeIteratorConfig = (CsvPipeIteratorConfig) config;
        try {
            Path csvPath = Path.of(csvPipeIteratorConfig.getCsvPath());
            CSVFormat csvFormat = CSVFormat.valueOf(csvPipeIteratorConfig
                    .getCsvFormat());
            reader = Files.newBufferedReader(csvPath, Charset.forName(csvPipeIteratorConfig.getCharset()));
            records = csvFormat.parse(reader).getRecords().iterator();
        } catch (IOException e) {
            throw new TikaPipeIteratorException("Could not initialize reader", e);
        }
    }

    @Override
    public String getPipeIteratorId() {
        return "csv-pipe-iterator";
    }
    private Reader reader;
    private Iterator<CSVRecord> records;

    @Override
    public boolean hasNext() {
        return records.hasNext();
    }

    @Override
    public List<PipeInput> next() {
        CSVRecord record = records.next();
        String value;
        if (csvPipeIteratorConfig.getFetchKeyColumnIndex() != null) {
            value = record.get(csvPipeIteratorConfig.getFetchKeyColumnIndex());
        } else if (StringUtils.isNotBlank(csvPipeIteratorConfig.getFetchKeyColumn())) {
            value = record.get(csvPipeIteratorConfig.getFetchKeyColumn());
        } else {
            value = record.get(0); // Default just use first record.
        }
        // Note we do not populate the fetcherId. That's up to the user of the fetchAndParseRequest.
        return List.of(PipeInput.builder()
                .fetchKey(value)
                .metadata(Map.of())
                .build());
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }
}
