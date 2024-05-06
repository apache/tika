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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.pipesiterator.TotalCountResult;
import org.apache.tika.utils.StringUtils;

public class AsyncStatus {

    public enum ASYNC_STATUS {
        STARTED,
        COMPLETED,
        CRASHED
    }

    private final Instant started;

    private Instant lastUpdate;
    private TotalCountResult totalCountResult =
            new TotalCountResult(0, TotalCountResult.STATUS.NOT_COMPLETED);
    private Map<PipesResult.STATUS, Long> statusCounts = new HashMap<>();
    private ASYNC_STATUS asyncStatus = ASYNC_STATUS.STARTED;

    private String crashMessage = StringUtils.EMPTY;

    public AsyncStatus() {
        started = Instant.now();
        lastUpdate = started;
    }

    public synchronized void update(
            Map<PipesResult.STATUS, Long> statusCounts,
            TotalCountResult totalCountResult,
            ASYNC_STATUS status) {
        this.lastUpdate = Instant.now();
        this.statusCounts = statusCounts;
        this.totalCountResult = totalCountResult;
        this.asyncStatus = status;
    }

    public void updateCrash(String msg) {
        this.crashMessage = msg;
    }

    public Instant getStarted() {
        return started;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public TotalCountResult getTotalCountResult() {
        return totalCountResult;
    }

    public Map<PipesResult.STATUS, Long> getStatusCounts() {
        return statusCounts;
    }

    public ASYNC_STATUS getAsyncStatus() {
        return asyncStatus;
    }

    public String getCrashMessage() {
        return crashMessage;
    }

    @Override
    public String toString() {
        return "AsyncStatus{"
                + "started="
                + started
                + ", lastUpdate="
                + lastUpdate
                + ", totalCountResult="
                + totalCountResult
                + ", statusCounts="
                + statusCounts
                + ", asyncStatus="
                + asyncStatus
                + ", crashMessage='"
                + crashMessage
                + '\''
                + '}';
    }
}
