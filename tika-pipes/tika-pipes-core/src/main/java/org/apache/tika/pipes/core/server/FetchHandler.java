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
package org.apache.tika.pipes.core.server;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.utils.ExceptionUtils;

class FetchHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FetchHandler.class);

    private final FetcherManager fetcherManager;

    public FetchHandler(FetcherManager fetcherManager) {
        this.fetcherManager = fetcherManager;
    }

    public TisOrResult fetch(FetchEmitTuple fetchEmitTuple, Metadata metadata) {
        FetcherOrResult fetcherResult = getFetcher(fetchEmitTuple);
        if (fetcherResult.pipesResult != null) {
            return new TisOrResult(null, fetcherResult.pipesResult);
        }
        try {
            TikaInputStream tis = fetcherResult.fetcher.fetch(
                    fetchEmitTuple.getFetchKey().getFetchKey(), metadata, fetchEmitTuple.getParseContext());
            return new TisOrResult(tis, null);
        } catch (IOException | TikaException e) {
            return new TisOrResult(null, new PipesResult(PipesResult.RESULT_STATUS.FETCH_EXCEPTION, ExceptionUtils.getStackTrace(e)));
        }
    }

    private FetcherOrResult getFetcher(FetchEmitTuple t) {
        try {
            return new FetcherOrResult(fetcherManager.getFetcher(t.getFetchKey().getFetcherId()), null);
        } catch (IllegalArgumentException e) {
            String noFetcherMsg = getNoFetcherMsg(t.getFetchKey().getFetcherId());
            LOG.warn(noFetcherMsg);
            return new FetcherOrResult(null, new PipesResult(PipesResult.RESULT_STATUS.FETCHER_NOT_FOUND, noFetcherMsg));
        } catch (IOException | TikaException e) {
            LOG.warn("Couldn't initialize fetcher for fetch id={}", t.getId(), e);
            return new FetcherOrResult(null, new PipesResult(PipesResult.RESULT_STATUS.FETCHER_INITIALIZATION_EXCEPTION,
                    ExceptionUtils.getStackTrace(e)));
        }
    }


    private String getNoFetcherMsg(String fetcherId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fetcher '").append(fetcherId).append("'");
        sb.append(" not found.");
        sb.append("\nThe configured FetcherManager supports:");
        int i = 0;
        for (String f : fetcherManager.getSupported()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(f);
        }
        return sb.toString();
    }

    private record FetcherOrResult(Fetcher fetcher, PipesResult pipesResult) {

    }

    record TisOrResult(TikaInputStream tis, PipesResult pipesResult) {

    }
}
