package org.apache.tika.batch;

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

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.apache.tika.util.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic class to use for reporting status from both the crawler and the consumers.
 * This wakes up roughly every {@link #sleepMillis} and log.info's a status report.
 */

public class StatusReporter implements Callable<IFileProcessorFutureResult> {

    private final Logger logger = LoggerFactory.getLogger(StatusReporter.class);

    //require references to these so that the
    //StatusReporter can query them when it wakes up
    private final ConsumersManager consumersManager;
    private final FileResourceCrawler crawler;

    //local time that the StatusReporter started
    private final long start;
    //how long to sleep between reporting intervals
    private long sleepMillis = 1000;

    //how long before considering a parse "stale" (potentially hung forever)
    private long staleThresholdMillis = 100000;

    private volatile boolean isShuttingDown = false;

    /**
     * Initialize with the crawler and consumers
     *
     * @param crawler   crawler to ping at intervals
     * @param consumersManager consumers to ping at intervals
     */
    public StatusReporter(FileResourceCrawler crawler, ConsumersManager consumersManager) {
        this.consumersManager = consumersManager;
        this.crawler = crawler;
        start = new Date().getTime();
    }

    /**
     * Override for different behavior.
     * <p/>
     * This reports the string at the info level to this class' logger.
     *
     * @param s string to report
     */
    protected void report(String s) {
        logger.info(s);
    }

    /**
     * Startup the reporter.
     */
    public IFileProcessorFutureResult call() {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.ROOT);
        try {
            while (true) {
                Thread.sleep(sleepMillis);
                int cnt = getRoughCountConsumed();
                int exceptions = getRoughCountExceptions();
                long elapsed = new Date().getTime() - start;
                double elapsedSecs = (double) elapsed / (double) 1000;
                int avg = (elapsedSecs > 5 || cnt > 100) ? (int) ((double) cnt / elapsedSecs) : -1;

                String elapsedString = DurationFormatUtils.formatMillis(new Date().getTime() - start);
                String docsPerSec = avg > -1 ? String.format(Locale.ROOT,
                        " (%s docs per sec)",
                        numberFormat.format(avg)) : "";
                String msg =
                        String.format(
                                Locale.ROOT,
                                "Processed %s documents in %s%s.",
                                numberFormat.format(cnt), elapsedString, docsPerSec);
                report(msg);
                if (exceptions == 1){
                    msg = "There has been one handled exception.";
                } else {
                    msg =
                            String.format(Locale.ROOT,
                                    "There have been %s handled exceptions.",
                                    numberFormat.format(exceptions));
                }
                report(msg);

                reportStale();

                int stillAlive = getStillAlive();
                if (stillAlive == 1) {
                    msg = "There is one file processor still active.";
                } else {
                    msg = "There are " + numberFormat.format(stillAlive) + " file processors still active.";
                }
                report(msg);

                int crawled = crawler.getConsidered();
                int added = crawler.getAdded();
                if (crawled == 1) {
                    msg = "The directory crawler has considered 1 file,";
                } else {
                    msg = "The directory crawler has considered " +
                            numberFormat.format(crawled) + " files, ";
                }
                if (added == 1) {
                    msg += "and it has added 1 file.";
                } else {
                    msg += "and it has added " +
                            numberFormat.format(crawler.getAdded()) + " files.";
                }
                msg += "\n";
                report(msg);

                if (! crawler.isActive()) {
                    msg = "The directory crawler has completed its crawl.\n";
                    report(msg);
                }
                if (isShuttingDown) {
                    msg = "Process is shutting down now.";
                    report(msg);
                }
            }
        } catch (InterruptedException e) {
            //swallow
        }
        return new StatusReporterFutureResult();
    }


    /**
     * Set the amount of time to sleep between reports.
     * @param sleepMillis length to sleep btwn reports in milliseconds
     */
    public void setSleepMillis(long sleepMillis) {
        this.sleepMillis = sleepMillis;
    }

    /**
     * Set the amount of time in milliseconds to use as the threshold for determining
     * a stale parse.
     *
     * @param staleThresholdMillis threshold for determining whether or not to report a stale
     */
    public void setStaleThresholdMillis(long staleThresholdMillis) {
        this.staleThresholdMillis = staleThresholdMillis;
    }


    private void reportStale() {
        for (FileResourceConsumer consumer : consumersManager.getConsumers()) {
            FileStarted fs = consumer.getCurrentFile();
            if (fs == null) {
                continue;
            }
            long elapsed = fs.getElapsedMillis();
            if (elapsed > staleThresholdMillis) {
                String elapsedString = Double.toString((double) elapsed / (double) 1000);
                report("A thread has been working on " + fs.getResourceId() +
                        " for " + elapsedString + " seconds.");
            }
        }
    }

    /*
     * This returns a rough (unsynchronized) count of resources consumed.
     */
    private int getRoughCountConsumed() {
        int ret = 0;
        for (FileResourceConsumer consumer : consumersManager.getConsumers()) {
            ret += consumer.getNumResourcesConsumed();
        }
        return ret;
    }

    private int getStillAlive() {
        int ret = 0;
        for (FileResourceConsumer consumer : consumersManager.getConsumers()) {
            if ( consumer.isStillActive()) {
                ret++;
            }
        }
        return ret;
    }

    /**
     * This returns a rough (unsynchronized) count of caught/handled exceptions.
     * @return rough count of exceptions
     */
    public int getRoughCountExceptions() {
        int ret = 0;
        for (FileResourceConsumer consumer : consumersManager.getConsumers()) {
            ret += consumer.getNumHandledExceptions();
        }
        return ret;
    }

    /**
     * Set whether the main process is in the process of shutting down.
     * @param isShuttingDown
     */
    public void setIsShuttingDown(boolean isShuttingDown){
        this.isShuttingDown = isShuttingDown;
    }
}
