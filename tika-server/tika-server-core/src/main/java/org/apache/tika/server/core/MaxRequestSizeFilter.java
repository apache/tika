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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeoutException;

import jakarta.servlet.ServletInputStream;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;

/**
 * Rejects request bodies larger than {@code maxRequestSizeBytes} (413), still arriving
 * after {@code maxRequestReadMillis} (408), or that would take the bytes all requests hold
 * past {@code maxInFlightRequestBytes} (429, see {@link RequestBodyBudget}).
 * <p>
 * A declared Content-Length over the limit is refused before the body is read. Requests
 * without a usable Content-Length -- chunked transfer encoding, in particular -- are
 * counted as they are consumed, so the limit holds whether or not the client is honest
 * about the size. The 408 comes from {@link RequestReadDeadlineInterceptor}, which puts
 * the deadline on the wire under this stream.
 */
@Provider
public class MaxRequestSizeFilter implements ContainerRequestFilter {

    static final String TOO_LARGE_MESSAGE = "Request body exceeds maxRequestSizeBytes";
    static final String TOO_SLOW_MESSAGE = "Request body not received within maxRequestReadMillis";
    static final String IDLE_MESSAGE = "Request body stalled past idleTimeoutMillis";
    static final String BUSY_MESSAGE = "Request body would exceed maxInFlightRequestBytes across requests in flight";
    // budget frees as soon as any in-flight request completes
    private static final long RETRY_AFTER_SECONDS = 1;

    private final long maxRequestSizeBytes;
    private final RequestBodyBudget budget;

    /**
     * @param maxRequestSizeBytes maximum request body in bytes; negative disables the limit
     */
    public MaxRequestSizeFilter(long maxRequestSizeBytes) {
        this(maxRequestSizeBytes, null);
    }

    /**
     * @param maxRequestSizeBytes maximum request body in bytes; negative disables the limit
     * @param budget bytes all requests may hold at once; null disables it
     */
    public MaxRequestSizeFilter(long maxRequestSizeBytes, RequestBodyBudget budget) {
        this.maxRequestSizeBytes = maxRequestSizeBytes;
        this.budget = budget;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (maxRequestSizeBytes >= 0 && requestContext.getLength() > maxRequestSizeBytes) {
            TransferLimitLog.log("declared Content-Length " + requestContext.getLength()
                    + " exceeds maxRequestSizeBytes (" + maxRequestSizeBytes + ")");
            requestContext.abortWith(tooLarge());
            return;
        }
        RequestBodyBudget.Reservation reservation =
                budget == null ? null : budget.open(JAXRSUtils.getCurrentMessage());
        // Entity bytes: after any Content-Encoding is undone.
        requestContext.setEntityStream(new BoundedInputStream(requestContext.getEntityStream(),
                maxRequestSizeBytes, -1, -1, -1, reservation));
    }

    private static Response tooLarge() {
        return Response
                .status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                .entity(TOO_LARGE_MESSAGE)
                .type(MediaType.TEXT_PLAIN)
                .build();
    }

    /**
     * Throws once more than {@code limit} bytes have been read, or on a read after the
     * deadline. Deliberately not silent truncation: a caller that sent too much must not
     * receive a 200 describing a prefix of their document. The deadline applies only to a
     * read that would wait on the client: bytes it already sent, such as a multipart closing
     * boundary CXF reads after a long parse, are not late. A client that sends nothing at all
     * is left to the connector's idle timeout.
     */
    static final class BoundedInputStream extends FilterInputStream {

        private final long limit;
        private final long startNanos;
        private final long maxReadNanos;
        private final long maxReadMillis;
        private final RequestBodyBudget.Reservation reservation;
        private long count;

        BoundedInputStream(InputStream in, long limit, long startNanos, long maxReadNanos,
                           long maxReadMillis, RequestBodyBudget.Reservation reservation) {
            super(in);
            this.limit = limit;
            this.startNanos = startNanos;
            this.maxReadNanos = maxReadNanos;
            this.maxReadMillis = maxReadMillis;
            this.reservation = reservation;
        }

        @Override
        public int read() throws IOException {
            checkDeadline();
            int c;
            try {
                c = super.read();
            } catch (IOException e) {
                throw idleOrRethrow(e);
            }
            if (c != -1) {
                add(1);
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            checkDeadline();
            int read;
            try {
                read = super.read(b, off, len);
            } catch (IOException e) {
                throw idleOrRethrow(e);
            }
            if (read > 0) {
                add(read);
            }
            return read;
        }

        // Jetty's idle timeout surfaces as an IOException caused by a TimeoutException
        private static IOException idleOrRethrow(IOException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof TimeoutException) {
                    throw new RequestTimeoutException(IDLE_MESSAGE + " (" + t.getMessage() + ")");
                }
            }
            return e;
        }

        // FilterInputStream.skip delegates, and the skipped bytes would go uncounted
        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) {
                return 0;
            }
            byte[] buffer = new byte[(int) Math.min(n, 8192)];
            long skipped = 0;
            while (skipped < n) {
                int read = read(buffer, 0, (int) Math.min(buffer.length, n - skipped));
                if (read == -1) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        private void checkDeadline() throws IOException {
            if (maxReadNanos >= 0 && System.nanoTime() - startNanos > maxReadNanos && wouldBlock()) {
                throw new RequestTimeoutException(TOO_SLOW_MESSAGE + " (" + maxReadMillis + " ms)");
            }
        }

        private boolean wouldBlock() throws IOException {
            if (in instanceof ServletInputStream servletIn && servletIn.isFinished()) {
                return false;
            }
            return in.available() == 0;
        }

        private void add(int n) {
            count += n;
            if (limit >= 0 && count > limit) {
                throw new RequestTooLargeException(TOO_LARGE_MESSAGE + " (" + limit + " bytes)");
            }
            if (reservation != null && !reservation.ensure(count)) {
                throw new RequestBudgetExceededException(BUSY_MESSAGE);
            }
        }
    }

    /**
     * Unchecked so it crosses whatever read path consumes the entity stream;
     * {@link RequestTooLargeExceptionMapper} turns it into the same 413 the
     * declared-Content-Length rejection produces. A plain IOException here
     * surfaced as an empty 500 on chunked uploads.
     */
    public static final class RequestTooLargeException extends RuntimeException {
        RequestTooLargeException(String message) {
            super(message);
        }
    }

    /** Unchecked for the same reason; {@link RequestTimeoutExceptionMapper} makes it a 408. */
    public static final class RequestTimeoutException extends RuntimeException {
        RequestTimeoutException(String message) {
            super(message);
        }
    }

    /** Unchecked for the same reason; {@link RequestBudgetExceededExceptionMapper} makes it a 429. */
    public static final class RequestBudgetExceededException extends RuntimeException {
        RequestBudgetExceededException(String message) {
            super(message);
        }
    }

    @Provider
    public static final class RequestBudgetExceededExceptionMapper
            implements ExceptionMapper<RequestBudgetExceededException> {
        @Override
        public Response toResponse(RequestBudgetExceededException e) {
            TransferLimitLog.log(e.getMessage());
            return Response
                    .status(Response.Status.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                    .entity(e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }

    @Provider
    public static final class RequestTimeoutExceptionMapper
            implements ExceptionMapper<RequestTimeoutException> {
        @Override
        public Response toResponse(RequestTimeoutException e) {
            TransferLimitLog.log(e.getMessage());
            return Response
                    .status(Response.Status.REQUEST_TIMEOUT)
                    .entity(e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }

    @Provider
    public static final class RequestTooLargeExceptionMapper
            implements ExceptionMapper<RequestTooLargeException> {
        @Override
        public Response toResponse(RequestTooLargeException e) {
            TransferLimitLog.log(e.getMessage());
            return Response
                    .status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                    .entity(e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }
}
