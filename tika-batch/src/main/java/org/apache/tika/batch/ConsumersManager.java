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

import java.util.Collections;
import java.util.List;

/**
 * Simple interface around a collection of consumers that allows
 * for initializing and shutting shared resources (e.g. db connection, index, writer, etc.)
 */
public abstract class ConsumersManager {

    //maximum time to allow the ConsumersManager for either init()
    //or shutdown()
    private long consumersManagerMaxMillis = 60000;
    private final List<FileResourceConsumer> consumers;

    public ConsumersManager(List<FileResourceConsumer> consumers) {
        this.consumers = Collections.unmodifiableList(consumers);
    }
    /**
     * Get the consumers
     * @return consumers
     */
    public List<FileResourceConsumer> getConsumers() {
        return consumers;
    }

    /**
     * This is called by BatchProcess before submitting the threads
     */
    public void init(){

    }

    /**
     * This is called by BatchProcess immediately before closing.
     * Beware! Some of the consumers may have hung or may not
     * have completed.
     */
    public void shutdown(){

    }

    /**
     * {@link org.apache.tika.batch.BatchProcess} will throw an exception
     * if the ConsumersManager doesn't complete init() or shutdown()
     * within this amount of time.
     * @return the maximum time allowed for init() or shutdown()
     */
    public long getConsumersManagerMaxMillis() {
        return consumersManagerMaxMillis;
    }

    /**
     * {@see #getConsumersManagerMaxMillis()}
     *
     * @param consumersManagerMaxMillis maximum number of milliseconds
     *                                  to allow for init() or shutdown()
     */
    public void setConsumersManagerMaxMillis(long consumersManagerMaxMillis) {
        this.consumersManagerMaxMillis = consumersManagerMaxMillis;
    }
}
