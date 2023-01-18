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
package org.apache.tika.async.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.async.AsyncProcessor;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class TikaAsyncCLI {

    private static final long TIMEOUT_MS = 600_000;
    private static final Logger LOG = LoggerFactory.getLogger(TikaAsyncCLI.class);

    public static void main(String[] args) throws Exception {
        Path tikaConfigPath = Paths.get(args[0]);
        PipesIterator pipesIterator = PipesIterator.build(tikaConfigPath);
        long start = System.currentTimeMillis();
        try (AsyncProcessor processor = new AsyncProcessor(tikaConfigPath, pipesIterator)) {

            for (FetchEmitTuple t : pipesIterator) {
                boolean offered = processor.offer(t, TIMEOUT_MS);
                if (! offered) {
                    throw new TimeoutException("timed out waiting to add a fetch emit tuple");
                }
            }
            processor.finished();
            while (true) {
                if (processor.checkActive()) {
                    Thread.sleep(500);
                } else {
                    break;
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("Successfully finished processing {} files in {} ms",
                    processor.getTotalProcessed(), elapsed);
        }
    }
}
