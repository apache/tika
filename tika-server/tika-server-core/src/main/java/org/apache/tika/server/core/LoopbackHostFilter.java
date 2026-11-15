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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * While the server listens only on loopback, refuses requests whose {@code Host} is not
 * {@code localhost}, a loopback IP or the configured host. A web page that points its own
 * domain at 127.0.0.1 (DNS rebinding) reaches the server as a same-origin peer, but its
 * requests still name that domain in {@code Host}.
 */
@Provider
@PreMatching
// after TikaMetricsFilter (100), so refusals are counted; before CXF's CORS filter (999)
@Priority(200)
public class LoopbackHostFilter implements ContainerRequestFilter {

    private static final String MESSAGE = "This server listens on a loopback address and answers only "
            + "requests addressed to localhost, a loopback IP or its configured host.";

    private static final Pattern IPV4 =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    // null when the server is not bound to loopback only, which turns the check off
    private final String boundHost;

    public LoopbackHostFilter(String host) {
        this.boundHost = bindsLoopbackOnly(host) ? normalize(host) : null;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (boundHost == null) {
            return;
        }
        String host = requestContext.getHeaderString(HttpHeaders.HOST);
        // HTTP/1.0 may omit Host; a browser never does
        if (host == null || host.isBlank() || isAllowed(host)) {
            return;
        }
        requestContext.abortWith(Response
                .status(Response.Status.FORBIDDEN)
                .entity(MESSAGE)
                .type(MediaType.TEXT_PLAIN)
                .build());
    }

    private boolean isAllowed(String hostHeader) {
        String name = normalize(stripPort(hostHeader.trim()));
        return !name.isEmpty() && (name.equals(boundHost) || name.equals("localhost")
                || name.endsWith(".localhost") || isLoopbackLiteral(name));
    }

    private static boolean bindsLoopbackOnly(String host) {
        // InetAddress reads a blank name as loopback; Jetty binds it to every interface
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!address.isLoopbackAddress()) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static String stripPort(String hostHeader) {
        if (hostHeader.startsWith("[")) {
            int close = hostHeader.indexOf(']');
            return close < 0 ? hostHeader : hostHeader.substring(0, close + 1);
        }
        int colon = hostHeader.indexOf(':');
        return colon >= 0 && colon == hostHeader.lastIndexOf(':')
                ? hostHeader.substring(0, colon) : hostHeader;
    }

    private static String normalize(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("[") && n.endsWith("]")) {
            n = n.substring(1, n.length() - 1);
        }
        return n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
    }

    // parsed here, not by InetAddress.getByName, which falls back to DNS for a name
    private static boolean isLoopbackLiteral(String name) {
        Matcher m = IPV4.matcher(name);
        if (m.matches()) {
            for (int i = 1; i <= 4; i++) {
                if (Integer.parseInt(m.group(i)) > 255) {
                    return false;
                }
            }
            return m.group(1).equals("127");
        }
        if (name.indexOf(':') < 0) {
            return false;
        }
        try {
            // bracketed, the JDK parses it as an IPv6 literal or throws; it never resolves it
            return InetAddress.getByName("[" + name + "]").isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
