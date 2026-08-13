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
import java.util.List;

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
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.server.core.ServerStatus;
import org.apache.tika.server.core.TikaServerParseException;

@Path("/detect")
public class DetectorResource {
    private static final Logger LOG = LoggerFactory.getLogger(DetectorResource.class);
    private final ServerStatus serverStatus;
    private final TikaResource tikaResource;

    public DetectorResource(ServerStatus serverStatus, TikaResource tikaResource) {
        this.serverStatus = serverStatus;
        this.tikaResource = tikaResource;
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    public String detect(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        ParseContext parseContext = tikaResource.createParseContext();
        Metadata met = Metadata.newInstance(parseContext);

        String filename = TikaResource.detectFilename(httpHeaders.getRequestHeaders());
        LOG.debug("Detecting media type for Filename: {}", filename);
        met.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        long taskId = serverStatus.start(ServerStatus.TASK.DETECT, filename);

        try (TikaInputStream tis = TikaInputStream.get(is)) {
            // NO_PARSE: the child detects (and digests, if configured) without parsing.
            // Detection still opens containers -- zip, OPC, POIFS -- over caller-supplied
            // bytes, so it belongs in the forked worker for the same reason parsing does.
            List<Metadata> metadataList =
                    tikaResource.parseWithPipes(tis, met, parseContext, ParseMode.NO_PARSE);
            String detected = metadataList.isEmpty()
                    ? null : metadataList.get(0).get(Metadata.CONTENT_TYPE);
            return detected == null ? MediaType.OCTET_STREAM.toString() : detected;
        } catch (IOException e) {
            // A failure reading/spooling the bytes is a server-side error, not a confident
            // "application/octet-stream" detection -- surface it as 500 rather than masking it.
            LOG.warn("Unable to detect MIME type for file. Reason: {} ({})", e.getMessage(), filename, e);
            throw new TikaServerParseException(e);
        } finally {
            serverStatus.complete(taskId);
        }
    }
}
