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

package org.apache.tika.server.api.impl;

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

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

import org.apache.tika.server.api.MetadataResourceApi;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class MetadataResourceApiServiceImpl implements MetadataResourceApi {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataResourceApi.class);

    @POST
    @Consumes("multipart/form-data")
    @Produces({"text/csv", "application/json", "application/rdf+xml"})
    @Path("form")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info) throws Exception {
        return Response.ok(
                parseMetadata(att.getObject(InputStream.class), new Metadata(),
                        att.getHeaders(), info)).build();
    }

    @PUT
    @Produces({"text/csv", "application/json", "application/rdf+xml"})
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        Metadata metadata = new Metadata();
        return Response.ok(
                parseMetadata(TikaResourceApiServiceImpl.getInputStream(is, metadata, httpHeaders), metadata, httpHeaders.getRequestHeaders(), info)).build();
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
        Metadata metadata = new Metadata();
        boolean success = false;
        try {
            parseMetadata(TikaResourceApiServiceImpl.getInputStream(is, metadata, httpHeaders),
                    metadata, httpHeaders.getRequestHeaders(), info);
            // once we've parsed the document successfully, we should use NOT_FOUND
            // if we did not see the field
            defaultErrorResponse = Response.Status.NOT_FOUND;
            success = true;
        } catch (Exception e) {
            LOG.info("Failed to process field {}", field, e);
        }

        if (success == false || metadata.get(field) == null) {
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

    private Metadata parseMetadata(InputStream is, Metadata metadata,
                                   MultivaluedMap<String, String> httpHeaders, UriInfo info) throws IOException {
        final ParseContext context = new ParseContext();
        Parser parser = TikaResourceApiServiceImpl.createParser();
        TikaResourceApiServiceImpl.fillMetadata(parser, metadata, context, httpHeaders);
        //no need to pass parser for embedded document parsing
        TikaResourceApiServiceImpl.fillParseContext(context, httpHeaders, null);
        TikaResourceApiServiceImpl.logRequest(LOG, info, metadata);
        TikaResourceApiServiceImpl.parse(parser, LOG, info.getPath(), is,
                new LanguageHandler() {
                    public void endDocument() {
                        metadata.set("language", getLanguage().getLanguage());
                    }},
                metadata, context);
        return metadata;
    }
    /**
     * PUT a document to the metadata extraction resource and get a specific metadata key&#39;s value.
     *
     * PUT a document to the metadata extraction resource and get a specific metadata key&#39;s value.
     *
     */
    public Map<String, String> putDocumentGetMetaValue(String metadataKey) {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a document to the metadata extraction resource.
     *
     * PUT a document to the metadata extraction resource.
     *
     */
    public Map<String, String> putDocumentMeta() {
        // TODO: Implement...
        
        return null;
    }
    
}

