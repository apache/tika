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

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Returns the exception's message in the 400 response body. CXF's default
 * {@code WebApplicationExceptionMapper} drops it unless {@code addMessageToResponse}
 * is set, so a bare {@code throw new BadRequestException(msg)} -- e.g. the "unrecognized
 * handler type" and reserved-fetcher/emitter errors -- would otherwise reach the client
 * as an empty body with the reason only in the server log.
 */
@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

    public Response toResponse(BadRequestException e) {
        Response response = e.getResponse();
        if (response != null && response.getEntity() != null) {
            // A body was supplied explicitly; keep it.
            return response;
        }
        String message = e.getMessage();
        return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(message == null ? "" : message)
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
