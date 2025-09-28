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
package org.apache.tika.server.core;

import java.time.Instant;
import java.util.Optional;

public class TaskStatus {
    final ServerStatus.TASK task;
    final Instant started;
    final Optional<String> fileName;
    final long timeoutMillis;

    TaskStatus(ServerStatus.TASK task, Instant started, String fileName, long timeoutMillis) {
        this.task = task;
        this.started = started;
        this.fileName = Optional.ofNullable(fileName);
        this.timeoutMillis = timeoutMillis;
    }


    @Override
    public String toString() {
        return "TaskStatus{" + "task=" + task + ", started=" + started + ", fileName=" + fileName + ", timeoutMillis=" + timeoutMillis + '}';
    }
}
