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
package org.apache.tika.server.core.metrics;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.apache.commons.io.output.CountingOutputStream;

import org.apache.tika.server.core.TikaServerProcess;

/**
 * Records {@code tika_server_requests} and the size summaries for every request on the
 * parse-side listener.
 * <p>
 * Timing starts pre-matching so unmatched (404) and aborted (413) requests are counted
 * too. The endpoint tag is the first path segment when it names one of the server's
 * endpoints; any other matched resource is {@code other} and an unmatched request is
 * {@code unmatched}. Response bytes are counted by wrapping the entity stream, since a
 * response filter runs before a streamed body is written.
 * <p>
 * Not counted: an exception no {@code ExceptionMapper} handles. CXF answers those from
 * its fault chain, which does not run response filters.
 */
@Provider
@PreMatching
public class TikaMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter,
        WriterInterceptor {

    static final String START_NANOS = TikaMetricsFilter.class.getName() + ".start";
    static final String ENDPOINT = TikaMetricsFilter.class.getName() + ".endpoint";
    static final String UNMATCHED = "unmatched";
    static final String OTHER = "other";

    private final TikaServerMetrics metrics;
    private final Set<String> knownEndpoints;

    public TikaMetricsFilter(TikaServerMetrics metrics) {
        this(metrics, Set.copyOf(TikaServerProcess.VALID_ENDPOINTS));
    }

    TikaMetricsFilter(TikaServerMetrics metrics, Set<String> knownEndpoints) {
        this.metrics = metrics;
        this.knownEndpoints = knownEndpoints;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_NANOS, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        String endpoint = endpoint(requestContext);
        requestContext.setProperty(ENDPOINT, endpoint);
        Object start = requestContext.getProperty(START_NANOS);
        long nanos = start instanceof Long s ? System.nanoTime() - s : 0L;
        metrics.recordRequest(endpoint, requestContext.getMethod(), responseContext.getStatus(),
                nanos);
        int length = requestContext.getLength();
        if (length >= 0) {
            metrics.recordRequestSize(endpoint, length);
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context)
            throws IOException, WebApplicationException {
        OutputStream original = context.getOutputStream();
        CountingOutputStream counting = new CountingOutputStream(original);
        context.setOutputStream(counting);
        try {
            context.proceed();
        } finally {
            Object endpoint = context.getProperty(ENDPOINT);
            metrics.recordResponseSize(endpoint instanceof String e ? e : OTHER,
                    counting.getByteCount());
        }
    }

    private String endpoint(ContainerRequestContext requestContext) {
        List<Object> matched = requestContext.getUriInfo().getMatchedResources();
        if (matched == null || matched.isEmpty()) {
            return UNMATCHED;
        }
        String path = requestContext.getUriInfo().getPath();
        int slash = path.indexOf('/');
        String root = slash < 0 ? path : path.substring(0, slash);
        return knownEndpoints.contains(root) ? root : OTHER;
    }
}
