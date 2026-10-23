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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.utils.ExceptionUtils;

/**
 * Last-resort mapper for exceptions no more specific mapper handles, so they don't fall
 * through to CXF's fault chain, which echoes raw exception messages to the caller.
 * <p>
 * The {@link WebApplicationException} passthrough is load-bearing, not defensive. CXF returns
 * a WAE's own response without consulting any mapper only when that response already carries
 * an entity and the WAE has no cause ({@code support.wae.spec.optimization}). Every other WAE
 * -- 404 and 405 from routing, an entity-less 204 or 503 a resource throws -- goes to mapper
 * selection, where {@code default.wae.mapper.least.specific} sorts CXF's own WAE mapper last
 * and hands it to this {@code ExceptionMapper<Throwable>}. Drop the branch and each of those
 * becomes a 500 trace.
 * <p>
 * CXF's mapper logged what it handled; taking that over means logging here, or 404s and 405s
 * vanish from the server log.
 */
@Provider
public class CatchAllExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = LoggerFactory.getLogger(CatchAllExceptionMapper.class);

    private final ExceptionReporting exceptionReporting;

    public CatchAllExceptionMapper(ExceptionReporting exceptionReporting) {
        this.exceptionReporting = exceptionReporting;
    }

    @Override
    public Response toResponse(Throwable t) {
        if (t instanceof WebApplicationException) {
            Response response = ((WebApplicationException) t).getResponse();
            if (response != null) {
                if (response.getStatus() >= 500) {
                    LOG.warn("returning {} for {}", response.getStatus(), t.getClass().getName(), t);
                } else {
                    LOG.debug("returning {} for {}", response.getStatus(), t.getClass().getName(), t);
                }
                return response;
            }
        }
        LOG.warn("unmapped exception; returning 500", t);
        return Response
                .status(500)
                .entity(ExceptionUtils.format(t, exceptionReporting))
                .type("text/plain")
                .build();
    }
}
