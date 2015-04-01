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

public class ParallelFileProcessingResult {
    private final int considered;
    private final int added;
    private final int consumed;
    private final int numberHandledExceptions;
    private final double secondsElapsed;
    private final int exitStatus;
    private final String causeForTermination;

    public ParallelFileProcessingResult(int considered, int added,
                                        int consumed, int numberHandledExceptions,
                                        double secondsElapsed,
                                        int exitStatus,
                                        String causeForTermination) {
        this.considered = considered;
        this.added = added;
        this.consumed = consumed;
        this.numberHandledExceptions = numberHandledExceptions;
        this.secondsElapsed = secondsElapsed;
        this.exitStatus = exitStatus;
        this.causeForTermination = causeForTermination;
    }

    /**
     * Returns the number of file resources considered.
     * If a filter causes the crawler to ignore a number of resources,
     * this number could be higher than that returned by {@link #getConsumed()}.
     *
     * @return number of file resources considered
     */
    public int getConsidered() {
        return considered;
    }

    /**
     * @return number of resources added to the queue
     */
    public int getAdded() {
        return added;
    }

    /**
     * @return number of resources that were tried to be consumed.  There
     * may have been an exception.
     */
    public int getConsumed() {
        return consumed;
    }

    /**
     * @return whether the {@link BatchProcess} was interrupted
     * by an {@link Interrupter}.
     */
    public String getCauseForTermination() {
        return causeForTermination;
    }

    /**
     *
     * @return seconds elapsed since the start of the batch processing
     */
    public double secondsElapsed() {
        return secondsElapsed;
    }

    public int getNumberHandledExceptions() {
        return numberHandledExceptions;
    }

    /**
     *
     * @return intendedExitStatus
     */
    public int getExitStatus() {
        return exitStatus;
    }

    @Override
    public String toString() {
        return "ParallelFileProcessingResult{" +
                "considered=" + considered +
                ", added=" + added +
                ", consumed=" + consumed +
                ", numberHandledExceptions=" + numberHandledExceptions +
                ", secondsElapsed=" + secondsElapsed +
                ", exitStatus=" + exitStatus +
                ", causeForTermination='" + causeForTermination + '\'' +
                '}';
    }
}
