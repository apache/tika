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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.eclipse.jetty.ee11.servlet.ServletContextRequest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * Aborts a response still being written {@code maxResponseWriteMillis} after it is committed;
 * the idle timeout alone resets on every trickle a slow reader accepts.
 * <p>
 * A timer, not a check between writes: one blocking write to a client that accepts a byte at
 * a time can otherwise outlast the deadline indefinitely. From commit, not the first write:
 * CXF drains the unread request body inside that write, which the read deadline bounds.
 * Aborted, not closed: closing would end a chunked body cleanly, passing a truncated document
 * off as complete.
 */
@Provider
public class ResponseWriteDeadlineInterceptor implements WriterInterceptor {

    private final long maxResponseWriteMillis;

    /**
     * @param maxResponseWriteMillis how long writing a response body may take; negative disables it
     */
    public ResponseWriteDeadlineInterceptor(long maxResponseWriteMillis) {
        this.maxResponseWriteMillis = maxResponseWriteMillis;
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        Message message = JAXRSUtils.getCurrentMessage();
        ServletContextRequest request = JettyRequests.of(message);
        // without a Jetty connection there is nothing to abort; on HTTP/2 the abort would end
        // every stream on the connection (see allowHttp2)
        if (maxResponseWriteMillis >= 0 && request != null
                && request.getConnectionMetaData().getHttpVersion().getVersion() < 20) {
            Message inMessage = message.getExchange().getInMessage();
            BooleanSupplier committed = inMessage.get(AbstractHTTPDestination.HTTP_RESPONSE)
                    instanceof HttpServletResponse response ? response::isCommitted : () -> true;
            DeadlineOutputStream out = new DeadlineOutputStream(context.getOutputStream(),
                    maxResponseWriteMillis, request.getComponents().getScheduler(), committed,
                    () -> request.getConnectionMetaData().getConnection().getEndPoint()
                            .close(new ResponseWriteTimeoutException("maxResponseWriteMillis exceeded")),
                    inMessage);
            Request.addCompletionListener(request, failure -> out.complete());
            context.setOutputStream(out);
            // CXF closes the message's stream after the writer; ours skips that once aborted
            if (message.getContent(OutputStream.class) == out.getDelegate()) {
                message.setContent(OutputStream.class, out);
            }
            try {
                context.proceed();
            } catch (ResponseWriteTimeoutException e) {
                // the timer logged it and closed the connection; CXF would log it again as an error
                if (!out.isAborted()) {
                    throw e;
                }
            }
            return;
        }
        context.proceed();
    }

    /**
     * From the first write, waits for the response to commit, then arms the deadline. The
     * timer and the request's completion race to end it, and exactly one wins: once completed
     * it never aborts; once aborted, writes fail and flush and close do nothing.
     */
    static final class DeadlineOutputStream extends FilterOutputStream {

        // how often to look for the commit; the deadline may start this much late
        private static final long COMMIT_POLL_MILLIS = 1000;

        private static final int WRITING = 0;
        private static final int DONE = 1;
        private static final int ABORTED = 2;

        private final long maxWriteMillis;
        private final Scheduler scheduler;
        private final BooleanSupplier committed;
        private final Runnable abort;
        private final Message request;
        private final AtomicInteger state = new AtomicInteger(WRITING);
        private volatile Scheduler.Task timer;

        /**
         * @param abort closes the connection under any blocked write
         * @param request the inbound message, for the log line the timer thread writes
         */
        DeadlineOutputStream(OutputStream out, long maxWriteMillis, Scheduler scheduler,
                             BooleanSupplier committed, Runnable abort, Message request) {
            super(out);
            this.maxWriteMillis = maxWriteMillis;
            this.scheduler = scheduler;
            this.committed = committed;
            this.abort = abort;
            this.request = request;
        }

        private OutputStream getDelegate() {
            return out;
        }

        @Override
        public void write(int b) throws IOException {
            start();
            try {
                out.write(b);
            } catch (IOException e) {
                throw timedOutOr(e);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            start();
            try {
                out.write(b, off, len);
            } catch (IOException e) {
                throw timedOutOr(e);
            }
        }

        @Override
        public void flush() throws IOException {
            if (state.get() == ABORTED) {
                return;
            }
            try {
                out.flush();
            } catch (IOException e) {
                throw timedOutOr(e);
            }
        }

        @Override
        public void close() throws IOException {
            if (state.get() == ABORTED) {
                return;
            }
            try {
                out.close();
            } catch (IOException e) {
                // aborted during the final flush: already logged, and the connection is gone
                if (state.get() != ABORTED) {
                    throw e;
                }
            }
        }

        boolean isAborted() {
            return state.get() == ABORTED;
        }

        void complete() {
            if (state.compareAndSet(WRITING, DONE)) {
                Scheduler.Task t = timer;
                if (t != null) {
                    t.cancel();
                }
            }
        }

        private void start() throws IOException {
            if (state.get() == ABORTED) {
                throw timeout();
            }
            if (timer == null && state.get() == WRITING) {
                awaitCommit();
            }
        }

        private void awaitCommit() {
            if (state.get() != WRITING) {
                return;
            }
            timer = committed.getAsBoolean()
                    ? scheduler.schedule(this::expire, maxWriteMillis, TimeUnit.MILLISECONDS)
                    : scheduler.schedule(this::awaitCommit, Math.min(COMMIT_POLL_MILLIS, maxWriteMillis),
                            TimeUnit.MILLISECONDS);
        }

        private void expire() {
            if (state.compareAndSet(WRITING, ABORTED)) {
                TransferLimitLog.log(request, timeout().getMessage());
                abort.run();
            }
        }

        private IOException timedOutOr(IOException e) {
            return state.get() == ABORTED ? timeout() : e;
        }

        private ResponseWriteTimeoutException timeout() {
            return new ResponseWriteTimeoutException(
                    "Response not written within maxResponseWriteMillis (" + maxWriteMillis + ")");
        }
    }

    /** Distinct type so its mapper, not the catch-all, handles it. */
    public static final class ResponseWriteTimeoutException extends IOException {
        ResponseWriteTimeoutException(String message) {
            super(message);
        }
    }

    /** Entity-less: the connection is gone, so CXF has nothing to write; the timer already logged it. */
    @Provider
    public static final class ResponseWriteTimeoutExceptionMapper
            implements ExceptionMapper<ResponseWriteTimeoutException> {
        @Override
        public Response toResponse(ResponseWriteTimeoutException e) {
            return Response.status(Response.Status.REQUEST_TIMEOUT).build();
        }
    }
}
