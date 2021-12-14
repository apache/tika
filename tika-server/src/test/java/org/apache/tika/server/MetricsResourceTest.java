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

package org.apache.tika.server;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.server.metrics.MetricsHelper;
import org.apache.tika.server.metrics.MetricsResource;
import org.apache.tika.server.writer.TextMessageBodyWriter;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

public class MetricsResourceTest extends CXFTestBase {

    private static final String METRICS_PROMETHEUS_PATH = "/metrics";

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(MetricsResource.class);
        sf.setResourceProvider(MetricsResource.class,
                new SingletonResourceProvider(new MetricsResource()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TextMessageBodyWriter());
        providers.add(new TikaServerParseExceptionMapper(false));
        sf.setProviders(providers);
    }

    @Override
    protected void setUpFeatures(JAXRSServerFactoryBean sf) {
        MetricsHelper.initMetrics(sf);
        MetricsHelper.registerPreStart(null, false);
    }

    @Override
    protected void setUpPostProcess(JAXRSServerFactoryBean sf, Server server) {
        MetricsHelper.registerPostStart(sf, server);
    }

    @Test
    public void testPrometheusInitialized() throws IOException {
        assertContains("jvm_buffer_count", getPrometheusResponse());
    }

    @Test
    public void testPrometheusContainsExtras() throws IOException {
        String response = getPrometheusResponse();
        assertContains("process_threads", response);
        assertContains("process_memory", response);
    }

    @Test
    public void testPrometheusContainsThreadPool() throws IOException {
        String response = getPrometheusResponse();
        assertContains("jetty_threads_config_min", response);
        assertTrue(getMetricValue(response, "jetty_threads_config_min") > 1.0);
        assertTrue(getMetricValue(response, "jetty_threads_current") > 1.0);
    }

    @Test
    public void testPrometheusContainsLog4j2() throws IOException {
        String response = getPrometheusResponse();
        assertContains("log4j2_events_total", response);
        assertTrue(getMetricValue(response,
                "log4j2_events_total{application=\"tika-server\",level=\"info\",}") > 1.0);
    }

    private String getPrometheusResponse() throws IOException {
        Response response = WebClient.create(endPoint + METRICS_PROMETHEUS_PATH)
                .type(MediaType.TEXT_PLAIN).accept(MediaType.TEXT_PLAIN).get();

        return getStringFromInputStream((InputStream) response.getEntity());
    }

    private double getMetricValue(String scrape, String metricName) {
        return Arrays.stream(scrape.split("\n"))
                .map(String::trim)
                .filter(s -> s.startsWith(metricName))
                .findFirst()
                .map(s -> Double.valueOf(s.split("\\s+")[s.split("\\s+").length - 1]))
                .get();
    }

    static String getStringFromInputStream(InputStream in) throws IOException {
        return IOUtils.toString(in, UTF_8);
    }

}
