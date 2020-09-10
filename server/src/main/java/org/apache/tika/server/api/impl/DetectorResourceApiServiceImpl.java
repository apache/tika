/**
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

package org.apache.tika.server.api.impl;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.server.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.server.api.DetectorResourceApi;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class DetectorResourceApiServiceImpl implements DetectorResourceApi {
    private static final Logger LOG = LoggerFactory.getLogger(DetectorResourceApi.class);
    private final ServerStatus serverStatus;

    public DetectorResourceApiServiceImpl(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }
    @PUT
    @Path("stream")
    @Consumes("*/*")
    @Produces("text/plain")
    public String detect(final InputStream is,
                         @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        Metadata met = new Metadata();
        TikaInputStream tis = TikaInputStream.get(TikaResourceApiServiceImpl.getInputStream(is, met, httpHeaders));
        String filename = TikaResourceApiServiceImpl.detectFilename(httpHeaders
                .getRequestHeaders());
        LOG.info("Detecting media type for Filename: {}", filename);
        met.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        long taskId = serverStatus.start(ServerStatus.TASK.DETECT, filename);
        try {
            return TikaResourceApiServiceImpl.getConfig().getDetector().detect(tis, met).toString();
        } catch (IOException e) {
            LOG.warn("Unable to detect MIME type for file. Reason: {} ({})",
                    e.getMessage(), filename, e);
            return MediaType.OCTET_STREAM.toString();
        } catch (OutOfMemoryError e) {
            LOG.error("OOM while detecting: ({})", filename, e);
            serverStatus.setStatus(ServerStatus.STATUS.ERROR);
            throw e;
        } finally {
            serverStatus.complete(taskId);
        }
    }

    /**
     * PUT a document and use the default detector to identify the MIME/media type.
     *
     * PUT a document and use the default detector to identify the MIME/media type. The caveat here is that providing a hint for the filename can increase the quality of detection. Default return is a string of the Media type name.
     *
     */
    public String putStream() {
        // TODO: Implement...
        
        return null;
    }
    
}

