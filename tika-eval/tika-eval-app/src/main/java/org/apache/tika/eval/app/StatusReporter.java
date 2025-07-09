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

import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.pipesiterator.CallablePipesIterator;
import org.apache.tika.utils.DurationFormatUtils;

public class StatusReporter implements Callable<Long> {

    public static final long COMPLETED_VAL = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusReporter.class);
    private final CallablePipesIterator pipesIterator;
    private final AtomicInteger filesProcessed;
    private final AtomicInteger activeWorkers;
    private final AtomicBoolean crawlerIsActive;
    private final long start;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.ROOT);


    public StatusReporter(CallablePipesIterator pipesIterator, AtomicInteger filesProcessed, AtomicInteger activeWorkers, AtomicBoolean crawlerIsActive) {
        this.pipesIterator = pipesIterator;
        this.filesProcessed = filesProcessed;
        this.activeWorkers = activeWorkers;
        this.crawlerIsActive = crawlerIsActive;
        this.start = System.currentTimeMillis();
    }

    @Override
    public Long call() throws Exception {
        while (true) {

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOGGER.info("Interrupted?");
                //expected
                return COMPLETED_VAL;
            }
            report();
            if (activeWorkers.get() == 0) {
                LOGGER.info("Completed successfully.");
                return COMPLETED_VAL;
            }
        }
    }

    private void report() {
        int cnt = filesProcessed.get();
        long elapsed = System.currentTimeMillis() - start;
        double elapsedSecs = (double) elapsed / (double) 1000;
        int avg = (elapsedSecs > 5 || cnt > 100) ? (int) ((double) cnt / elapsedSecs) : -1;

        String elapsedString = DurationFormatUtils.formatMillis(System.currentTimeMillis() - start);
        String docsPerSec = avg > -1 ? String.format(Locale.ROOT, " (%s docs per sec)", numberFormat.format(avg)) : "";
        String msg = String.format(Locale.ROOT, "Processed %s documents in %s%s.", numberFormat.format(cnt), elapsedString, docsPerSec);
        LOGGER.info(msg);

        int stillAlive = activeWorkers.get();
        if (stillAlive == 1) {
            msg = "There is one file processor still active.";
        } else {
            msg = "There are " + numberFormat.format(stillAlive) + " file processors still active.";
        }
        LOGGER.info(msg);

        long enqueued = pipesIterator.getEnqueued();

        if (enqueued == 1) {
            msg = "The crawler has enqueued 1 file.";
        } else {
            msg = "The crawler has enqueued " + numberFormat.format(enqueued) + " files.";
        }
        LOGGER.info(msg);

        if (! crawlerIsActive.get()) {
            msg = "The directory crawler has completed its crawl.\n";
            LOGGER.info(msg);
        }
    }
}
