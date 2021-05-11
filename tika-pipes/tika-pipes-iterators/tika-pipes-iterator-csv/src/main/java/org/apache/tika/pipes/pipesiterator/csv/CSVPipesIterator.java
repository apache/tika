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
package org.apache.tika.pipes.pipesiterator.csv;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.utils.StringUtils;

/**
 * Iterates through a UTF-8 CSV file. This adds all columns
 * (except for the 'fetchKeyColumn' and 'emitKeyColumn', if specified)
 * to the metadata object.
 * <p>
 *  <ul>
 *      <li>If an 'idColumn' is specified, this will use that
 *      column's value as the id.</li>
 *      <li>If no 'idColumn' is specified, but a 'fetchKeyColumn' is specified,
 *          the string in the 'fetchKeyColumn' will be used as the 'id'.</li>
 *      <li>The 'idColumn' value is not added to the metadata.</li>
 *  </ul>
 *  <ul>
 *      <li>If a 'fetchKeyColumn' is specified, this will use that
 *      column's value as the fetchKey.</li>
 *      <li>If no 'fetchKeyColumn' is specified, this will send the
 *      metadata from the other columns.</li>
 *      <li>The 'fetchKeyColumn' value is not added to the metadata.</li>
 *  </ul>
 * <p>
 *  <ul>
 *      <li>If an 'emitKeyColumn' is specified, this will use that
 *      column's value as the emit key.</li>
 *      <li>If an 'emitKeyColumn' is not specified, this will use
 *      the value from the 'fetchKeyColumn'.</li>
 *      <li>The 'emitKeyColumn' value is not added to the metadata.</li>
 *  </ul>
 */
public class CSVPipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CSVPipesIterator.class);

    private final Charset charset = StandardCharsets.UTF_8;
    private Path csvPath;
    private String fetchKeyColumn;
    private String emitKeyColumn;
    private String idColumn;

    @Field
    public void setCsvPath(String csvPath) {
        setCsvPath(Paths.get(csvPath));
    }

    @Field
    public void setFetchKeyColumn(String fetchKeyColumn) {
        this.fetchKeyColumn = fetchKeyColumn;
    }

    @Field
    public void setEmitKeyColumn(String emitKeyColumn) {
        this.emitKeyColumn = emitKeyColumn;
    }

    @Field
    public void setIdColumn(String idColumn) {
        this.idColumn = idColumn;
    }

    @Field
    public void setCsvPath(Path csvPath) {
        this.csvPath = csvPath;
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();
        try (Reader reader = Files.newBufferedReader(csvPath, charset)) {
            Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(reader);
            List<String> headers = new ArrayList<>();
            FetchEmitKeyIndices fetchEmitKeyIndices = null;
            for (CSVRecord record : records) {
                fetchEmitKeyIndices = loadHeaders(record, headers);
                break;
            }

            checkFetchEmitValidity(fetcherName, emitterName, fetchEmitKeyIndices, headers);
            HandlerConfig handlerConfig = getHandlerConfig();
            for (CSVRecord record : records) {
                String id = getId(fetchEmitKeyIndices, record);
                String fetchKey = getFetchKey(fetchEmitKeyIndices, record);
                String emitKey = getEmitKey(fetchEmitKeyIndices, record);
                if (StringUtils.isBlank(fetchKey) && !StringUtils.isBlank(fetcherName)) {
                    LOGGER.debug("Fetcher specified ({}), but no fetchkey was found in ({})",
                            fetcherName, record);
                }
                if (StringUtils.isBlank(emitKey)) {
                    throw new IOException("emitKey must not be blank in :" + record);
                }
                if (StringUtils.isBlank(id) && ! StringUtils.isBlank(fetchKey)) {
                    id = fetchKey;
                }
                Metadata metadata = loadMetadata(fetchEmitKeyIndices, headers, record);
                tryToAdd(new FetchEmitTuple(id, new FetchKey(fetcherName, fetchKey),
                        new EmitKey(emitterName, emitKey), metadata, handlerConfig,
                        getOnParseException()));
            }
        }
    }

    private void checkFetchEmitValidity(String fetcherName, String emitterName,
                                        FetchEmitKeyIndices fetchEmitKeyIndices,
                                        List<String> headers) throws IOException {

        if (StringUtils.isBlank(emitterName)) {
            throw new IOException(new TikaConfigException("must specify at least an emitterName"));
        }

        if (StringUtils.isBlank(fetcherName) && !StringUtils.isBlank(fetchKeyColumn)) {
            throw new IOException(new TikaConfigException("If specifying a 'fetchKeyColumn', " +
                    "you must also specify a 'fetcherName'"));
        }

        if (StringUtils.isBlank(fetcherName)) {
            LOGGER.debug("No fetcher specified. This will be metadata only");
        }

        //if a fetchkeycolumn is specified, make sure that it was found
        if (!StringUtils.isBlank(fetchKeyColumn) && fetchEmitKeyIndices.fetchKeyIndex < 0) {
            throw new IOException(new TikaConfigException(
                    "Couldn't find fetchKeyColumn (" + fetchKeyColumn + " in header.\n" +
                            "These are the headers I see: " + headers));
        }

        //if an emitkeycolumn is specified, make sure that it was found
        if (!StringUtils.isBlank(emitKeyColumn) && fetchEmitKeyIndices.emitKeyIndex < 0) {
            throw new IOException(new TikaConfigException(
                    "Couldn't find emitKeyColumn (" + emitKeyColumn + " in header.\n" +
                            "These are the headers I see: " + headers));
        }

        if (StringUtils.isBlank(emitKeyColumn)) {
            LOGGER.debug("No emitKeyColumn specified. " +
                            "Will use fetchKeyColumn ({}) for both the fetch key and emit key",
                    fetchKeyColumn);
        }
    }

    private String getId(FetchEmitKeyIndices fetchEmitKeyIndices, CSVRecord record) {
        if (fetchEmitKeyIndices.idIndex > -1) {
            return record.get(fetchEmitKeyIndices.idIndex);
        }
        return StringUtils.EMPTY;
    }


    private String getFetchKey(FetchEmitKeyIndices fetchEmitKeyIndices, CSVRecord record) {
        if (fetchEmitKeyIndices.fetchKeyIndex > -1) {
            return record.get(fetchEmitKeyIndices.fetchKeyIndex);
        }
        return StringUtils.EMPTY;
    }

    private String getEmitKey(FetchEmitKeyIndices fetchEmitKeyIndices, CSVRecord record) {
        if (fetchEmitKeyIndices.emitKeyIndex > -1) {
            return record.get(fetchEmitKeyIndices.emitKeyIndex);
        }
        return getFetchKey(fetchEmitKeyIndices, record);
    }

    private Metadata loadMetadata(FetchEmitKeyIndices fetchEmitKeyIndices, List<String> headers,
                                  CSVRecord record) {
        Metadata metadata = new Metadata();
        for (int i = 0; i < record.size(); i++) {
            if (fetchEmitKeyIndices.shouldSkip(i)) {
                continue;
            }
            metadata.set(headers.get(i), record.get(i));
        }
        return metadata;
    }


    private FetchEmitKeyIndices loadHeaders(CSVRecord record, List<String> headers)
            throws IOException {
        int fetchKeyColumnIndex = -1;
        int emitKeyColumnIndex = -1;
        int idIndex = -1;
        for (int col = 0; col < record.size(); col++) {
            String header = record.get(col);
            if (StringUtils.isBlank(header)) {
                throw new IOException(
                        new TikaException("Header in column (" + col + ") must not be empty"));
            }
            headers.add(header);
            if (header.equals(fetchKeyColumn)) {
                fetchKeyColumnIndex = col;
            } else if (header.equals(emitKeyColumn)) {
                emitKeyColumnIndex = col;
            } else if (header.equals(idColumn)) {
                idIndex = col;
            }
        }
        return new FetchEmitKeyIndices(idIndex, fetchKeyColumnIndex, emitKeyColumnIndex);
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("csvPath", this.csvPath);
    }

    private static class FetchEmitKeyIndices {
        private final int idIndex;
        private final int fetchKeyIndex;
        private final int emitKeyIndex;

        public FetchEmitKeyIndices(int idIndex, int fetchKeyIndex, int emitKeyIndex) {
            this.idIndex = idIndex;
            this.fetchKeyIndex = fetchKeyIndex;
            this.emitKeyIndex = emitKeyIndex;
        }

        public boolean shouldSkip(int index) {
            return idIndex == index || fetchKeyIndex == index || emitKeyIndex == index;
        }
    }
}
