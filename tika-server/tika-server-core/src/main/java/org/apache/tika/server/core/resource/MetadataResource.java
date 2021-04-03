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

import static org.apache.tika.server.core.resource.TikaResource.fillMetadata;
import static org.apache.tika.server.core.resource.TikaResource.fillParseContext;

import java.io.IOException;
import java.io.InputStream;
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

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;


@Path("/meta")
public class MetadataResource {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataResource.class);

    @POST
    @Consumes("multipart/form-data")
    @Produces({"text/csv", "application/json"})
    @Path("form")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info)
            throws Exception {
        return Response.ok(parseMetadata(att.getObject(InputStream.class), new Metadata(),
                att.getHeaders(), info)).build();
    }

    @PUT
    @Produces({"text/csv", "application/json"})
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders,
                                @Context UriInfo info) throws Exception {
        Metadata metadata = new Metadata();
        return Response
                .ok(parseMetadata(TikaResource.getInputStream(is, metadata, httpHeaders), metadata,
                        httpHeaders.getRequestHeaders(), info)).build();
    }

    /**
     * Get a specific metadata field. If the input stream cannot be parsed, but a
     * value was found for the given metadata field, then the value of the field
     * is returned as part of a 200 OK response; otherwise a
     * {@link javax.ws.rs.core.Response.Status#BAD_REQUEST} is generated. If the stream
     * was successfully parsed but the specific metadata field was not found, then a
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
     * @return one of {@link javax.ws.rs.core.Response.Status#OK},
     * {@link javax.ws.rs.core.Response.Status#NOT_FOUND}, or
     * {@link javax.ws.rs.core.Response.Status#BAD_REQUEST}
     * @throws Exception
     */
    @PUT
    @Path("{field}")
    @Produces({"text/csv", "application/json", "text/plain"})
    public Response getMetadataField(InputStream is, @Context HttpHeaders httpHeaders,
                                     @Context UriInfo info, @PathParam("field") String field)
            throws Exception {

        // use BAD request to indicate that we may not have had enough data to
        // process the request
        Response.Status defaultErrorResponse = Response.Status.BAD_REQUEST;
        Metadata metadata = new Metadata();
        boolean success = false;
        try {
            parseMetadata(TikaResource.getInputStream(is, metadata, httpHeaders), metadata,
                    httpHeaders.getRequestHeaders(), info);
            // once we've parsed the document successfully, we should use NOT_FOUND
            // if we did not see the field
            defaultErrorResponse = Response.Status.NOT_FOUND;
            success = true;
        } catch (Exception e) {
            LOG.info("Failed to process field {}", field, e);
        }

        if (success == false || metadata.get(field) == null) {
            return Response.status(defaultErrorResponse)
                    .entity("Failed to get metadata field " + field).build();
        }

        // remove fields we don't care about for the response
        for (String name : metadata.names()) {
            if (!field.equals(name)) {
                metadata.remove(name);
            }
        }
        return Response.ok(metadata).build();
    }

    protected Metadata parseMetadata(InputStream is, Metadata metadata,
                                     MultivaluedMap<String, String> httpHeaders, UriInfo info)
            throws IOException {
        final ParseContext context = new ParseContext();
        Parser parser = TikaResource.createParser();
        fillMetadata(parser, metadata, httpHeaders);
        fillParseContext(httpHeaders, metadata, context);
        //no need to parse embedded docs
        context.set(DocumentSelector.class, metadata1 -> false);

        TikaResource.logRequest(LOG, "/meta", metadata);
        TikaResource.parse(parser, LOG, info.getPath(), is, new LanguageHandler() {
            public void endDocument() {
                metadata.set("language", getLanguage().getLanguage());
            }
        }, metadata, context);
        return metadata;
    }
}
