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

import java.util.concurrent.TimeUnit;

import org.apache.cxf.io.DelegatingInputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * Runs on every request as it is received, before resource matching, so it also covers
 * requests CXF answers itself (OPTIONS, 404, 405, ...). Stops CXF spooling the unread body
 * to disk once a response starts, and puts the {@code maxRequestReadMillis} deadline on the
 * wire, so the up to 16 MiB CXF then discards cannot be dripped in forever.
 * {@link MaxRequestSizeFilter} bounds the entity stream of matched requests.
 */
public class RequestReadDeadlineInterceptor extends AbstractPhaseInterceptor<Message> {

    static final String START_NANOS = RequestReadDeadlineInterceptor.class.getName() + ".startNanos";

    // CXF's switch for caching the unread request body when the response starts
    private static final String CXF_CACHE_INPUT = "cxf.io.cacheinput";

    private final long maxRequestReadMillis;

    /**
     * @param maxRequestReadMillis how long reading the body may take; negative disables it
     */
    public RequestReadDeadlineInterceptor(long maxRequestReadMillis) {
        super(Phase.RECEIVE);
        this.maxRequestReadMillis = maxRequestReadMillis;
    }

    @Override
    public void handleMessage(Message message) {
        Exchange exchange = message.getExchange();
        if (exchange == null) {
            return;
        }
        long startNanos = System.nanoTime();
        exchange.put(CXF_CACHE_INPUT, Boolean.FALSE);
        exchange.put(START_NANOS, startNanos);
        DelegatingInputStream wire = message.getContent(DelegatingInputStream.class);
        if (maxRequestReadMillis >= 0 && wire != null) {
            // deadline only: a size cap here would cut off a client that reads only after sending
            wire.setInputStream(new MaxRequestSizeFilter.BoundedInputStream(wire.getInputStream(), -1,
                    startNanos, TimeUnit.MILLISECONDS.toNanos(maxRequestReadMillis), maxRequestReadMillis, null));
        }
    }
}
