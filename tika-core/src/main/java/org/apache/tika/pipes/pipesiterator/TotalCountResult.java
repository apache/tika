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
package org.apache.tika.pipes.pipesiterator;

public class TotalCountResult {

    public enum STATUS {
        UNSUPPORTED,
        EXCEPTION,
        NOT_COMPLETED,
        COMPLETED
    }

    public static TotalCountResult UNSUPPORTED = new TotalCountResult(-1, STATUS.UNSUPPORTED);
    private long totalCount;
    private STATUS status;

    public TotalCountResult() {
        totalCount = 0;
        status = STATUS.NOT_COMPLETED;
    }

    public TotalCountResult(long totalCount, STATUS status) {
        this.totalCount = totalCount;
        this.status = status;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public STATUS getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "TotalCountResult{" + "totalCount=" + totalCount + ", status=" + status + '}';
    }
}
