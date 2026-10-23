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

import jakarta.servlet.http.HttpServletRequest;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.eclipse.jetty.ee11.servlet.ServletContextRequest;

/** The Jetty request under a CXF message, for what JAX-RS does not expose. */
final class JettyRequests {

    private JettyRequests() {
    }

    /** Null when there is no Jetty HTTP request, as under CXF's local transport. */
    static ServletContextRequest of(Message message) {
        if (message == null || message.getExchange() == null) {
            return null;
        }
        Object request = message.getExchange().getInMessage().get(AbstractHTTPDestination.HTTP_REQUEST);
        if (!(request instanceof HttpServletRequest httpRequest)) {
            return null;
        }
        try {
            return ServletContextRequest.getServletContextRequest(httpRequest);
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
