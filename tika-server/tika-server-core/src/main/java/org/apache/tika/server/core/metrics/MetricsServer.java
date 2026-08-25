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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * The scrape listener: a separate, minimal Jetty server that answers {@code GET /metrics}
 * and nothing else. Deliberately not a connector on the CXF-owned server, so the parse
 * endpoints are unreachable on this port by construction and a slow parse can never
 * queue a scrape -- it has its own (tiny) thread pool.
 */
public final class MetricsServer implements AutoCloseable {

    public static final String PATH = "/metrics";
    static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";
    private static final int MAX_THREADS = 4;

    private final Server server;
    private final ServerConnector connector;

    public MetricsServer(String host, int port, TikaServerMetrics metrics) {
        QueuedThreadPool pool = new QueuedThreadPool(MAX_THREADS, 1);
        pool.setName("tika-metrics");
        server = new Server(pool);
        // No Server: header -- this port is unauthenticated by design; don't advertise a version.
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setHost(host);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new ScrapeHandler(metrics));
    }

    public void start() throws Exception {
        server.start();
    }

    /** The bound port; meaningful after {@link #start()} (port 0 picks a free one). */
    public int getPort() {
        return connector.getLocalPort();
    }

    @Override
    public void close() throws Exception {
        server.stop();
    }

    private static final class ScrapeHandler extends Handler.Abstract {

        private final TikaServerMetrics metrics;

        private ScrapeHandler(TikaServerMetrics metrics) {
            this.metrics = metrics;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!PATH.equals(Request.getPathInContext(request))) {
                Response.writeError(request, response, callback, HttpStatus.NOT_FOUND_404);
                return true;
            }
            if (!HttpMethod.GET.is(request.getMethod())) {
                Response.writeError(request, response, callback, HttpStatus.METHOD_NOT_ALLOWED_405);
                return true;
            }
            byte[] body = metrics.scrape().getBytes(StandardCharsets.UTF_8);
            response.setStatus(HttpStatus.OK_200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, CONTENT_TYPE);
            response.getHeaders().put(HttpHeader.CONTENT_LENGTH, body.length);
            response.write(true, ByteBuffer.wrap(body), callback);
            return true;
        }
    }
}
