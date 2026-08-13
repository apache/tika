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

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Rejects request bodies larger than {@code maxRequestSizeBytes}.
 * <p>
 * A declared Content-Length over the limit is refused before the body is read. Requests
 * without a usable Content-Length -- chunked transfer encoding, in particular -- are
 * counted as they are consumed, so the limit holds whether or not the client is honest
 * about the size.
 */
@Provider
public class MaxRequestSizeFilter implements ContainerRequestFilter {

    static final String TOO_LARGE_MESSAGE = "Request body exceeds maxRequestSizeBytes";

    private final long maxRequestSizeBytes;

    /**
     * @param maxRequestSizeBytes maximum request body in bytes; negative disables the limit
     */
    public MaxRequestSizeFilter(long maxRequestSizeBytes) {
        this.maxRequestSizeBytes = maxRequestSizeBytes;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (maxRequestSizeBytes < 0) {
            return;
        }
        if (requestContext.getLength() > maxRequestSizeBytes) {
            requestContext.abortWith(tooLarge());
            return;
        }
        requestContext.setEntityStream(
                new BoundedInputStream(requestContext.getEntityStream(), maxRequestSizeBytes));
    }

    private static Response tooLarge() {
        return Response
                .status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                .entity(TOO_LARGE_MESSAGE)
                .type(MediaType.TEXT_PLAIN)
                .build();
    }

    /**
     * Throws once more than {@code limit} bytes have been read. Deliberately not
     * silent truncation: a caller that sent too much must not receive a 200 describing
     * a prefix of their document.
     */
    private static final class BoundedInputStream extends FilterInputStream {

        private final long limit;
        private long count;

        private BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int c = super.read();
            if (c != -1) {
                add(1);
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(int n) {
            count += n;
            if (count > limit) {
                throw new RequestTooLargeException(TOO_LARGE_MESSAGE + " (" + limit + ")");
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

    @Provider
    public static final class RequestTooLargeExceptionMapper
            implements ExceptionMapper<RequestTooLargeException> {
        @Override
        public Response toResponse(RequestTooLargeException e) {
            return Response
                    .status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                    .entity(e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }
}
