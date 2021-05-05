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

package org.apache.tika.server.metrics;

import io.micrometer.prometheus.PrometheusMeterRegistry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * The resource class to serve metrics endpoints.
 */
@Path("/metrics")
public class MetricsResource {

    /**
     * The default metrics endpoint.
     * Exports prometheus style metrics.
     * @return the prometheus format metrics.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String scrapePrometheusRegistry() {
        PrometheusMeterRegistry prometheusMeterRegistry =
                MetricsHelper.getRegistry(PrometheusMeterRegistry.class);
        if (prometheusMeterRegistry != null) {
            return prometheusMeterRegistry.scrape();
        } else {
            throw new WebApplicationException("Prometheus exporter not initialized",
                    Response.Status.BAD_REQUEST);
        }
    }

}
