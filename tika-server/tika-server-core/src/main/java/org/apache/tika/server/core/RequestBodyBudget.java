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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.cxf.message.Message;
import org.eclipse.jetty.ee11.servlet.ServletContextRequest;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caps the request-body bytes all in-flight requests hold at once ({@code maxInFlightRequestBytes}).
 * <p>
 * Bytes are charged as they are read, not as declared, so a client that sends slowly holds only
 * what it has sent. Each request charges through its own {@link Reservation}, which is the only
 * way the total changes; it is released exactly once, when Jetty completes the request.
 */
public final class RequestBodyBudget {

    private static final Logger LOG = LoggerFactory.getLogger(RequestBodyBudget.class);

    private final long maxBytes;
    private final AtomicLong inFlight = new AtomicLong();
    private final AtomicBoolean warnedNegative = new AtomicBoolean();

    /**
     * @param maxBytes the most request-body bytes in flight at once; must be positive
     */
    public RequestBodyBudget(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public long getInFlightBytes() {
        return inFlight.get();
    }

    /**
     * Opens the reservation for the request behind {@code message}, released when Jetty
     * completes it. Fails rather than open one that could never be released.
     */
    Reservation open(Message message) {
        ServletContextRequest request = JettyRequests.of(message);
        if (request == null) {
            throw new IllegalStateException("no Jetty request to release the body budget on");
        }
        Reservation reservation = new Reservation();
        // registered before anything is charged, so a failure here leaks nothing
        Request.addCompletionListener(request, failure -> reservation.release());
        return reservation;
    }

    Reservation newReservation() {
        return new Reservation();
    }

    private boolean tryCharge(long bytes) {
        while (true) {
            long current = inFlight.get();
            if (bytes > maxBytes - current) {
                return false;
            }
            if (inFlight.compareAndSet(current, current + bytes)) {
                return true;
            }
        }
    }

    private void refund(long bytes) {
        if (inFlight.addAndGet(-bytes) < 0) {
            if (warnedNegative.compareAndSet(false, true)) {
                LOG.warn("request body budget went negative: accounting bug, clamped to 0");
            }
            inFlight.updateAndGet(v -> Math.max(v, 0));
        }
    }

    /** One request's share of the budget. */
    final class Reservation {

        private long charged;
        private boolean released;

        /**
         * Grows this request's charge to {@code bytes} read so far; false, with nothing
         * charged, if that would exceed the budget. After release, charges nothing.
         */
        synchronized boolean ensure(long bytes) {
            if (released || bytes <= charged) {
                return true;
            }
            if (!tryCharge(bytes - charged)) {
                return false;
            }
            charged = bytes;
            return true;
        }

        synchronized void release() {
            if (released) {
                return;
            }
            released = true;
            refund(charged);
            charged = 0;
        }
    }
}
