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
package org.apache.tika.batch;


import java.io.InputStream;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class that waits for input on System.in.  If this reads
 * EOF or if there is an exception from the parent's IO,
 * this will send a signal to shutdown the forked process.
 * <p>
 * This will call System.exit(-1) if the process
 * doesn't stop after {@link #pauseOnEarlyTermination}
 * milliseconds.
 * </p>
 */
public class Interrupter implements Callable<IFileProcessorFutureResult> {
    private static final Logger LOG = LoggerFactory.getLogger(Interrupter.class);

    private static final long EXTRA_GRACE_PERIOD_MILLIS = 1000;
    private final long pauseOnEarlyTermination;

    public Interrupter(long pauseOnEarlyTermination) {
        this.pauseOnEarlyTermination = pauseOnEarlyTermination;
    }

    public IFileProcessorFutureResult call() {
        try {
            InputStream is = System.in;
            int byt = is.read();
            while (byt > -1) {
                byt = is.read();
            }
        } catch (Throwable e) {
            LOG.warn("Exception from STDIN in CommandlineInterrupter.", e);
        }
        new Thread(new Doomsday()).start();
        return new InterrupterFutureResult();
    }

    private class Doomsday implements Runnable {
        @Override
        public void run() {
            if (pauseOnEarlyTermination < 0) {
                return;
            }
            long start = System.currentTimeMillis();
            long elapsed = System.currentTimeMillis() - start;
            while (elapsed < (pauseOnEarlyTermination + EXTRA_GRACE_PERIOD_MILLIS)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                elapsed = System.currentTimeMillis() - start;
            }
            LOG.error("Interrupter timed out; now calling System.exit.");
            System.exit(-1);
        }
    }
}
