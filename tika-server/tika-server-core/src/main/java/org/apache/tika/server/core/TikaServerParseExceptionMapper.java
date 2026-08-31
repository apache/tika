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


import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.utils.ExceptionUtils;

@Provider
public class TikaServerParseExceptionMapper implements ExceptionMapper<TikaServerParseException> {

    private final ExceptionReporting exceptionReporting;

    public TikaServerParseExceptionMapper() {
        this(new ExceptionReporting());
    }

    public TikaServerParseExceptionMapper(ExceptionReporting exceptionReporting) {
        this.exceptionReporting = exceptionReporting;
    }

    /**
     * Always 500: in 4.x a parse failure comes back as container-exception metadata from the
     * fork, so what reaches here is a fetch/spool/IPC failure, never a document's own
     * exception. The cause is formatted rather than the wrapper so the body does not open
     * with this server's own frames.
     */
    public Response toResponse(TikaServerParseException e) {
        Throwable cause = e.getCause();
        return Response
                .status(500)
                .entity(ExceptionUtils.format(cause != null ? cause : e, exceptionReporting))
                .type("text/plain")
                .build();
    }
}
