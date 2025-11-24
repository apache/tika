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

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;
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
public class CSVPipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(CSVPipesIterator.class);

    private final Charset charset = StandardCharsets.UTF_8;
    private final CSVPipesIteratorConfig config;

    private CSVPipesIterator(CSVPipesIteratorConfig config, ExtensionConfig extensionConfig) throws TikaConfigException {
        super(extensionConfig);
        this.config = config;
        if (config.getCsvPath() == null) {
            throw new TikaConfigException("csvPath must not be empty");
        }
    }

    public static CSVPipesIterator build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        CSVPipesIteratorConfig config = CSVPipesIteratorConfig.load(extensionConfig.jsonConfig());
        return new CSVPipesIterator(config, extensionConfig);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherPluginId = config.getBaseConfig().fetcherId();
        String emitterName = config.getBaseConfig().emitterId();
        try (Reader reader = Files.newBufferedReader(config.getCsvPath(), charset)) {
            Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(reader);
            List<String> headers = new ArrayList<>();
            FetchEmitKeyIndices fetchEmitKeyIndices = null;
            for (CSVRecord record : records) {
                fetchEmitKeyIndices = loadHeaders(record, headers);
                break;
            }

            try {
                checkFetchEmitValidity(fetcherPluginId, emitterName, fetchEmitKeyIndices, headers);
            } catch (TikaConfigException e) {
                throw new IOException(e);
            }
            HandlerConfig handlerConfig = config.getBaseConfig().handlerConfig();
            for (CSVRecord record : records) {
                String id = record.get(fetchEmitKeyIndices.idIndex);
                String fetchKey = record.get(fetchEmitKeyIndices.fetchKeyIndex);
                String emitKey = record.get(fetchEmitKeyIndices.emitKeyIndex);
                if (StringUtils.isBlank(fetchKey) && !StringUtils.isBlank(fetcherPluginId)) {
                    LOGGER.debug("Fetcher specified ({}), but no fetchkey was found in ({})", fetcherPluginId, record);
                }
                if (StringUtils.isBlank(emitKey)) {
                    throw new IOException("emitKey must not be blank in :" + record);
                }

                Metadata metadata = loadMetadata(fetchEmitKeyIndices, headers, record);
                ParseContext parseContext = new ParseContext();
                parseContext.set(HandlerConfig.class, handlerConfig);
                tryToAdd(new FetchEmitTuple(id, new FetchKey(fetcherPluginId, fetchKey), new EmitKey(emitterName, emitKey), metadata, parseContext,
                        config.getBaseConfig().onParseException()));
            }
        }
    }

    private void checkFetchEmitValidity(String fetcherPluginId, String emitterName, FetchEmitKeyIndices fetchEmitKeyIndices, List<String> headers) throws TikaConfigException {
        String fetchKeyColumn = config.getFetchKeyColumn();
        String emitKeyColumn = config.getEmitKeyColumn();
        String idColumn = config.getIdColumn();

        if (StringUtils.isBlank(emitterName)) {
            throw new TikaConfigException("must specify at least an emitterName");
        }

        if (StringUtils.isBlank(fetcherPluginId) && !StringUtils.isBlank(fetchKeyColumn)) {
            throw new TikaConfigException("If specifying a 'fetchKeyColumn', " + "you must also specify a 'fetcherPluginId'");
        }

        if (StringUtils.isBlank(fetcherPluginId)) {
            LOGGER.info("No fetcher specified. This will be metadata only");
        }

        if (StringUtils.isBlank(fetchKeyColumn)) {
            throw new TikaConfigException("must specify fetchKeyColumn");
        }
        //if a fetchkeycolumn is specified, make sure that it was found
        if (!StringUtils.isBlank(fetchKeyColumn) && fetchEmitKeyIndices.fetchKeyIndex < 0) {
            throw new TikaConfigException("Couldn't find fetchKeyColumn (" + fetchKeyColumn + " in header.\n" + "These are the headers I see: " + headers);
        }

        //if an emitkeycolumn is specified, make sure that it was found
        if (!StringUtils.isBlank(emitKeyColumn) && fetchEmitKeyIndices.emitKeyIndex < 0) {
            throw new TikaConfigException("Couldn't find emitKeyColumn (" + emitKeyColumn + " in header.\n" + "These are the headers I see: " + headers);
        }

        //if an idcolumn is specified, make sure that it was found
        if (!StringUtils.isBlank(idColumn) && fetchEmitKeyIndices.idIndex < 0) {
            throw new TikaConfigException("Couldn't find idColumn (" + idColumn + " in header.\n" + "These are the headers I see: " + headers);
        }

        if (StringUtils.isBlank(emitKeyColumn)) {
            LOGGER.warn("No emitKeyColumn specified. " + "Will use fetchKeyColumn ({}) for both the fetch key and emit key", fetchKeyColumn);
        }

    }

    private Metadata loadMetadata(FetchEmitKeyIndices fetchEmitKeyIndices, List<String> headers, CSVRecord record) {
        Metadata metadata = new Metadata();
        for (int i = 0; i < record.size(); i++) {
            if (fetchEmitKeyIndices.shouldSkip(i)) {
                continue;
            }
            metadata.set(headers.get(i), record.get(i));
        }
        return metadata;
    }


    private FetchEmitKeyIndices loadHeaders(CSVRecord record, List<String> headers) throws IOException {
        String fetchKeyColumn = config.getFetchKeyColumn();
        String emitKeyColumn = config.getEmitKeyColumn();
        String idColumn = config.getIdColumn();

        int fetchKeyColumnIndex = -1;
        int emitKeyColumnIndex = -1;
        int idIndex = -1;
        for (int col = 0; col < record.size(); col++) {
            String header = record.get(col);
            if (StringUtils.isBlank(header)) {
                throw new IOException("Header in column (" + col + ") must not be empty");
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

        if (StringUtils.isBlank(idColumn)) {
            LOGGER.info("no idColumn specified, will use fetchKeyColumn");
            idIndex = fetchKeyColumnIndex;
        }

        if (StringUtils.isBlank(emitKeyColumn)) {
            LOGGER.info("no emitKeyColumn specified, will use fetchKeyColumn");
            emitKeyColumnIndex = fetchKeyColumnIndex;
        }
        return new FetchEmitKeyIndices(idIndex, fetchKeyColumnIndex, emitKeyColumnIndex);
    }

    private static class FetchEmitKeyIndices {
        private final int fetchKeyIndex;
        private int idIndex;
        private int emitKeyIndex;

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
