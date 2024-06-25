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

package org.apache.tika.server.core.resource;

import java.io.IOException;
import java.io.InputStream;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.server.core.ServerStatus;

@Path("/detect")
public class DetectorResource {
    private static final Logger LOG = LoggerFactory.getLogger(DetectorResource.class);
    private final ServerStatus serverStatus;

    public DetectorResource(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    @PUT
    @Path("stream")
    @Consumes("*/*")
    @Produces("text/plain")
    public String detect(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        Metadata met = new Metadata();

        String filename = TikaResource.detectFilename(httpHeaders.getRequestHeaders());
        LOG.info("Detecting media type for Filename: {}", filename);
        met.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        ParseContext parseContext = new ParseContext();
        TikaResource.fillParseContext(httpHeaders.getRequestHeaders(), met, parseContext);
        long timeoutMillis = TikaResource.getTaskTimeout(parseContext);
        long taskId = serverStatus.start(ServerStatus.TASK.DETECT, filename, timeoutMillis);

        try (TikaInputStream tis = TikaInputStream.get(TikaResource.getInputStream(is, met, httpHeaders, info))) {
            return TikaResource
                    .getConfig()
                    .getDetector()
                    .detect(tis, met)
                    .toString();
        } catch (IOException e) {
            LOG.warn("Unable to detect MIME type for file. Reason: {} ({})", e.getMessage(), filename, e);
            return MediaType.OCTET_STREAM.toString();
        } catch (OutOfMemoryError e) {
            LOG.error("OOM while detecting: ({})", filename, e);
            serverStatus.setStatus(ServerStatus.STATUS.ERROR);
            throw e;
        } catch (Throwable e) {
            LOG.error("Exception while detecting: ({})", filename, e);
            throw e;
        } finally {
            serverStatus.complete(taskId);
        }
    }
}
