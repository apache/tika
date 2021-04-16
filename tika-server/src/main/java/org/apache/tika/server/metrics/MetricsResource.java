package org.apache.tika.server.metrics;

import io.micrometer.prometheus.PrometheusMeterRegistry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/metrics")
public class MetricsResource {

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
