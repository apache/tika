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
package org.apache.tika.server.standard.resource;

import java.io.InputStream;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.server.core.resource.MetadataResource;
import org.apache.tika.server.core.resource.TikaResource;
import org.apache.tika.server.core.resource.TikaServerResource;

public class XMPMetadataResource extends MetadataResource implements TikaServerResource {

    @PUT
    @Path("{field}")
    @Produces({"application/rdf+xml"})
    @Override
    public Response getMetadataField(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info, @PathParam("field") String field) throws Exception {
        return super.getMetadataField(is, httpHeaders, info, field);
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces({"application/rdf+xml"})
    @Path("form")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info) throws Exception {
        return Response
                .ok(parseMetadata(att.getObject(InputStream.class), new Metadata(), att.getHeaders(), info))
                .build();
    }

    @PUT
    @Produces({"application/rdf+xml"})
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        Metadata metadata = new Metadata();
        return Response
                .ok(parseMetadata(TikaResource.getInputStream(is, metadata, httpHeaders, info), metadata, httpHeaders.getRequestHeaders(), info))
                .build();
    }
}
