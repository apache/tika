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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.ws.rs.container.ContainerRequestContext;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.Test;

import org.apache.tika.server.core.resource.TikaVersion;

public class LoopbackHostFilterTest extends CXFTestBase {

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        sf.setResourceClasses(TikaVersion.class);
        sf.setResourceProvider(TikaVersion.class, new SingletonResourceProvider(new TikaVersion()));
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        sf.setProviders(List.of(new LoopbackHostFilter("localhost")));
    }

    @Test
    public void testLoopbackNamesAreServed() throws Exception {
        for (String host : new String[]{"localhost", "LOCALHOST", "localhost.", "127.0.0.1",
                "127.1.2.3", "[::1]", "tika.localhost"}) {
            assertEquals(200, status(host + ":" + testPort), host);
        }
        assertEquals(200, status("localhost"));
    }

    // what a page on a rebound domain sends once that domain resolves to 127.0.0.1
    @Test
    public void testOtherNamesAreRefused() throws Exception {
        for (String host : new String[]{"rebind.attacker.example", "localhost.attacker.example",
                "127.0.0.1.attacker.example", "192.168.1.10", "[2001:db8::1]"}) {
            assertEquals(403, status(host + ":" + testPort), host);
        }
    }

    @Test
    public void testNoCheckWhenNotBoundToLoopback() {
        assertFalse(aborts(new LoopbackHostFilter("0.0.0.0"), "rebind.attacker.example:9998"));
    }

    private int status(String host) throws IOException {
        try (Socket socket = new Socket("localhost", testPort)) {
            socket.setSoTimeout(30000);
            OutputStream os = socket.getOutputStream();
            os.write(("GET /version HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            os.flush();
            String statusLine = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    private static boolean aborts(LoopbackHostFilter filter, String host) {
        AtomicBoolean aborted = new AtomicBoolean();
        ContainerRequestContext context = (ContainerRequestContext) Proxy.newProxyInstance(
                LoopbackHostFilterTest.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getHeaderString")) {
                        return host;
                    }
                    if (method.getName().equals("abortWith")) {
                        aborted.set(true);
                    }
                    return null;
                });
        filter.filter(context);
        return aborted.get();
    }
}
