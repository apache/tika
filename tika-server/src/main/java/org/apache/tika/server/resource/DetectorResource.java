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

package org.apache.tika.server.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

@Path("/detect")
public class DetectorResource {

    private static final Log logger = LogFactory.getLog(DetectorResource.class
            .getName());

    private TikaConfig config = null;

    public DetectorResource(TikaConfig config) {
        this.config = config;
    }

    @PUT
    @Path("stream")
    @Consumes("*/*")
    @Produces("text/plain")
    public String detect(final InputStream is,
                         @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        Metadata met = new Metadata();
        TikaInputStream tis = TikaInputStream.get(is);
        String filename = TikaResource.detectFilename(httpHeaders
                .getRequestHeaders());
        logger.info("Detecting media type for Filename: " + filename);
        met.add(Metadata.RESOURCE_NAME_KEY, filename);
        try {
            return this.config.getDetector().detect(tis, met).toString();
        } catch (IOException e) {
            logger.warn("Unable to detect MIME type for file. Reason: "
                    + e.getMessage());
            e.printStackTrace();
            return MediaType.OCTET_STREAM.toString();
        }
    }

}
