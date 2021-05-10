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
package org.apache.tika.pipes.async;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.PipesConfigBase;

public class AsyncConfig extends PipesConfigBase {

    private long emitWithinMillis = 10000;
    private long emitMaxEstimatedBytes = 100000;

    private long maxForEmitBatchBytes = 0;
    private int queueSize = 10000;
    private final int numEmitters = 1;

    public static AsyncConfig load(Path p) throws IOException, TikaConfigException {
        AsyncConfig asyncConfig = new AsyncConfig();
        try (InputStream is = Files.newInputStream(p)) {
            asyncConfig.configure("async", is);
        }
        return asyncConfig;
    }

    public long getEmitWithinMillis() {
        return emitWithinMillis;
    }

    /**
     * If nothing has been emitted in this amount of time
     * and the {@link #getEmitMaxEstimatedBytes()} has not been reached yet,
     * emit what's in the emit queue.
     *
     * @param emitWithinMillis
     */
    public void setEmitWithinMillis(long emitWithinMillis) {
        this.emitWithinMillis = emitWithinMillis;
    }

    /**
     * When the emit queue hits this estimated size (sum of
     * estimated extract sizes), emit the batch.
     * @return
     */
    public long getEmitMaxEstimatedBytes() {
        return emitMaxEstimatedBytes;
    }

    public void setEmitMaxEstimatedBytes(long emitMaxEstimatedBytes) {
        this.emitMaxEstimatedBytes = emitMaxEstimatedBytes;
    }


    /**
     *  What is the maximum bytes size per extract that
     *  will be allowed in the emit queue.  If an extract is too
     *  big, skip the emit queue and forward it directly from the processor.  If
     *  set to <code>0</code>, this will never send a an extract back for batch emitting,
     *  but will emit the extract directly from the processor.
     * @return
     */
    public long getMaxForEmitBatchBytes() {
        return maxForEmitBatchBytes;
    }

    public void setMaxForEmitBatchBytes(long maxForEmitBatchBytes) {
        this.maxForEmitBatchBytes = maxForEmitBatchBytes;
    }

    /**
     * FetchEmitTuple queue size
     * @return
     */
    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    /**
     * Number of emitters
     *
     * @return
     */
    public int getNumEmitters() {
        return numEmitters;
    }
}
