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

import java.util.List;
import java.util.Set;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

/**
 * Records {@code tika_server_requests} and the request-size summary for every request on
 * the parse-side listener. Timing starts pre-matching so unmatched (404) and aborted
 * requests are counted too.
 * <p>
 * Not counted: an exception no {@code ExceptionMapper} handles. CXF answers those from
 * its fault chain, which does not run response filters.
 */
@Provider
@PreMatching
public class TikaMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String START_NANOS = TikaMetricsFilter.class.getName() + ".start";
    static final String RECORDED = TikaMetricsFilter.class.getName() + ".recorded";
    static final String UNMATCHED = "unmatched";
    static final String OTHER = "other";

    private final TikaServerMetrics metrics;
    private final Set<String> knownEndpoints;

    public TikaMetricsFilter(TikaServerMetrics metrics, Set<String> knownEndpoints) {
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
        // CXF re-runs response filters when a mapped exception is thrown mid-write.
        if (requestContext.getProperty(RECORDED) != null) {
            return;
        }
        requestContext.setProperty(RECORDED, Boolean.TRUE);
        String endpoint = endpoint(requestContext);
        Object start = requestContext.getProperty(START_NANOS);
        long nanos = start instanceof Long s ? System.nanoTime() - s : 0L;
        metrics.recordRequest(endpoint, requestContext.getMethod(), responseContext.getStatus(),
                nanos);
        int length = requestContext.getLength();
        if (length >= 0) {
            metrics.recordRequestSize(endpoint, length);
        }
    }

    /**
     * First path segment when it names a known endpoint; otherwise {@code other} for a
     * matched resource and {@code unmatched} for a request answered before routing.
     */
    private String endpoint(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        int slash = path.indexOf('/');
        String root = slash < 0 ? path : path.substring(0, slash);
        if (knownEndpoints.contains(root)) {
            return root;
        }
        List<Object> matched = requestContext.getUriInfo().getMatchedResources();
        return matched == null || matched.isEmpty() ? UNMATCHED : OTHER;
    }
}
