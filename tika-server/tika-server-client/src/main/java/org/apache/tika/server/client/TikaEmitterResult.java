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
package org.apache.tika.server.client;

public class TikaEmitterResult {


    private final STATUS status;
    private final String msg;//used for exceptions. will be null for status ok
    private final long timeElapsed;

    public TikaEmitterResult(STATUS status, long timeElapsed, String msg) {
        this.status = status;
        this.timeElapsed = timeElapsed;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "TikaEmitterResult{" + "status=" + status + ", msg='" + msg + '\'' +
                ", timeElapsed=" + timeElapsed + '}';
    }

    public STATUS getStatus() {
        return status;
    }

    public String getMsg() {
        return msg;
    }

    public long getTimeElapsed() {
        return timeElapsed;
    }

    enum STATUS {
        OK, NOT_OK, EXCEEDED_MAX_RETRIES, TIMED_OUT_WAITING_FOR_TIKA
    }


}
