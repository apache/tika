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

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS filter that gates /config endpoints behind the enableUnsecureFeatures flag.
 * When enableUnsecureFeatures is false, requests to paths containing "/config" will
 * receive a 403 Forbidden response.
 */
@Provider
public class ConfigEndpointSecurityFilter implements ContainerRequestFilter {

    private final boolean enableUnsecureFeatures;

    public ConfigEndpointSecurityFilter(boolean enableUnsecureFeatures) {
        this.enableUnsecureFeatures = enableUnsecureFeatures;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!enableUnsecureFeatures && requestContext.getUriInfo().getPath().contains("/config")) {
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("Config endpoints are disabled. Set enableUnsecureFeatures=true in server config.")
                    .type(MediaType.TEXT_PLAIN)
                    .build());
        }
    }
}
