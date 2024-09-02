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

/**
 * Interface for pipesiterators that allow counting of total
 * documents.  This is useful for user-facing frontends where
 * the user does not have easy access to the total number of files
 * for processing.
 *
 * This is run in a daemon thread and is not guaranteed to complete before
 * the actual file processing has completed.
 *
 * This is an ancillary task, and should not throw runtime exceptions.
 *
 * Implementers should be careful to check for thread interrupts.
 *
 */
public interface TotalCounter {

    void startTotalCount();

    /**
     * Returns the total count so far.  Check the {@link TotalCountResult#getStatus()}
     * to figure out if the count has completed yet, if it is unsupported or if
     * there was an exception during the counting.
     * @return
     */
    TotalCountResult getTotalCount();
}
