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

package org.apache.tika.server.resource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.tika.language.ProfilingHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;


@Path("/meta")
public class MetadataResource {
    private static final Log logger = LogFactory.getLog(MetadataResource.class);

    @POST
    @Consumes("multipart/form-data")
    @Produces({"text/csv", "application/json", "application/rdf+xml"})
    @Path("form")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info) throws Exception {
        return Response.ok(
                parseMetadata(att.getObject(InputStream.class), att.getHeaders(), info)).build();
    }

    @PUT
    @Produces({"text/csv", "application/json", "application/rdf+xml"})
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        return Response.ok(
                parseMetadata(TikaUtils.getInputSteam(is, httpHeaders), httpHeaders.getRequestHeaders(), info)).build();
    }

    /**
     * Get a specific metadata field. If the input stream cannot be parsed, but a
     * value was found for the given metadata field, then the value of the field
     * is returned as part of a 200 OK response; otherwise a
     * {@link javax.ws.rs.core.Response.Status#BAD_REQUEST} is generated. If the stream was successfully
     * parsed but the specific metadata field was not found, then a
     * {@link javax.ws.rs.core.Response.Status#NOT_FOUND} is returned.
     * <p/>
     * Note that this method handles multivalue fields and returns possibly more
     * metadata value than requested.
     * <p/>
     * If you want XMP, you must be careful to specify the exact XMP key.
     * For example, "Author" will return nothing, but "dc:creator" will return the correct value.
     *
     * @param is          inputstream
     * @param httpHeaders httpheaders
     * @param info        info
     * @param field       the tika metadata field name
     * @return one of {@link javax.ws.rs.core.Response.Status#OK}, {@link javax.ws.rs.core.Response.Status#NOT_FOUND}, or
     * {@link javax.ws.rs.core.Response.Status#BAD_REQUEST}
     * @throws Exception
     */
    @PUT
    @Path("{field}")
    @Produces({"text/csv", "application/json", "application/rdf+xml", "text/plain"})
    public Response getMetadataField(InputStream is, @Context HttpHeaders httpHeaders,
                                     @Context UriInfo info, @PathParam("field") String field) throws Exception {

        // use BAD request to indicate that we may not have had enough data to
        // process the request
        Response.Status defaultErrorResponse = Response.Status.BAD_REQUEST;
        Metadata metadata = null;
        try {
            metadata = parseMetadata(TikaUtils.getInputSteam(is, httpHeaders), httpHeaders.getRequestHeaders(), info);
            // once we've parsed the document successfully, we should use NOT_FOUND
            // if we did not see the field
            defaultErrorResponse = Response.Status.NOT_FOUND;
        } catch (Exception e) {
            logger.info("Failed to process field " + field, e);
        }

        if (metadata == null || metadata.get(field) == null) {
            return Response.status(defaultErrorResponse).entity("Failed to get metadata field " + field).build();
        }

        // remove fields we don't care about for the response
        for (String name : metadata.names()) {
            if (!field.equals(name)) {
                metadata.remove(name);
            }
        }
        return Response.ok(metadata).build();
    }

    private Metadata parseMetadata(InputStream is,
                                   MultivaluedMap<String, String> httpHeaders, UriInfo info) throws IOException {
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();
        Parser parser = TikaResource.createParser();
        TikaResource.fillMetadata(parser, metadata, context, httpHeaders);
        //no need to pass parser for embedded document parsing
        TikaResource.fillParseContext(context, httpHeaders, null);
        TikaResource.logRequest(logger, info, metadata);
        TikaResource.parse(parser, logger, info.getPath(), is,
                new ProfilingHandler() {
                    public void endDocument() {
                        metadata.set("language", getLanguage().getLanguage());
                    }},
                metadata, context);
        return metadata;
    }
}
