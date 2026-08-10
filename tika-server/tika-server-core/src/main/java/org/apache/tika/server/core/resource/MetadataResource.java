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

import java.io.InputStream;
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.server.core.TikaServerParseException;


@Path("/meta")
public class MetadataResource {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataResource.class);

    private TikaResource tikaResource;

    public MetadataResource(TikaResource tikaResource) {
        this.tikaResource = tikaResource;
    }

    /** For subclasses the service loader constructs; see {@link TikaResourceAware}. */
    protected MetadataResource() {
    }

    protected void setTikaResource(TikaResource tikaResource) {
        this.tikaResource = tikaResource;
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces({"text/csv", "application/json"})
    @Path("form")
    public Response getMetadataFromMultipart(Attachment att, @Context UriInfo info) throws Exception {
        ParseContext context = tikaResource.createParseContext();
        try (TikaInputStream tis = TikaInputStream.get(att.getObject(InputStream.class))) {
            tis.getPath(); // Spool to temp file for pipes-based parsing
            return Response
                    .ok(parseMetadata(tis, Metadata.newInstance(context), att.getHeaders(), context))
                    .build();
        }
    }

    /**
     * Multipart endpoint with per-request ParseContext configuration.
     * Accepts two parts: "file" (the document) and "config" (JSON configuration with parseContext).
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces({"text/csv", "application/json"})
    @Path("config")
    public Response getMetadataWithConfig(
            List<Attachment> attachments,
            @Context HttpHeaders httpHeaders) throws Exception {

        // Load default context from config, then overlay with request config
        ParseContext context = tikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        try (TikaInputStream tis = tikaResource.setupMultipartConfig(attachments, metadata, context)) {
            TikaResource.logRequest(LOG, "/meta/config", metadata);
            return Response.ok(parseMetadata(tis, metadata, httpHeaders.getRequestHeaders(), context)).build();
        }
    }

    @PUT
    @Produces({"text/csv", "application/json"})
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        ParseContext context = tikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            tis.getPath(); // Spool to temp file for pipes-based parsing
            return Response
                    .ok(parseMetadata(tis, metadata, httpHeaders.getRequestHeaders(), context))
                    .build();
        }
    }

    /**
     * Get a specific metadata field. If the document parses successfully but the
     * specific metadata field was not found, a
     * {@link javax.ws.rs.core.Response.Status#NOT_FOUND} is returned. Unlike the other
     * /meta endpoints, a bare field value has no envelope to embed a container-level
     * exception in, so that case is thrown (422) instead.
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
     * @return one of {@link javax.ws.rs.core.Response.Status#OK} or
     * {@link javax.ws.rs.core.Response.Status#NOT_FOUND}
     * @throws Exception
     */
    @PUT
    @Path("{field}")
    @Produces({"text/csv", "application/json", "text/plain"})
    public Response getMetadataField(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info, @PathParam("field") String field) throws Exception {
        ParseContext context = tikaResource.createParseContext();
        Metadata metadata;
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            tis.getPath(); // Spool to temp file for pipes-based parsing
            metadata = parseMetadata(tis, Metadata.newInstance(context), httpHeaders.getRequestHeaders(), context);
        }

        String containerException = metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION);
        if (containerException != null && !containerException.isEmpty()) {
            throw new TikaServerParseException(new TikaException(containerException));
        }

        if (metadata.get(field) == null) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity("Failed to get metadata field " + field)
                    .build();
        }

        // remove fields we don't care about for the response
        for (String name : metadata.names()) {
            if (!field.equals(name)) {
                metadata.remove(name);
            }
        }
        return Response
                .ok(metadata)
                .build();
    }

    /**
     * Parses via the shared pipes-backed PipesParser, stopping at the container document
     * (EmbeddedLimits maxDepth=0) with content capture off ("ignore" handler) -- metadata
     * only, matching /meta's contract. Set unconditionally so per-request config can't
     * turn content capture back on. A container-level exception is embedded in
     * CONTAINER_EXCEPTION here, not thrown; getMetadataField throws instead since it
     * returns a bare scalar with nowhere to embed it.
     */
    protected Metadata parseMetadata(TikaInputStream tis, Metadata metadata, MultivaluedMap<String, String> httpHeaders, ParseContext context)
            throws Exception {
        fillMetadata(null, metadata, httpHeaders);
        context.set(EmbeddedLimits.class, new EmbeddedLimits(0, false, EmbeddedLimits.UNLIMITED, false));
        context.set(ContentHandlerFactory.class,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));

        TikaResource.logRequest(LOG, "/meta", metadata);
        List<Metadata> metadataList = tikaResource.parseWithPipes(tis, metadata, context, ParseMode.RMETA);
        if (metadataList.isEmpty()) {
            return Metadata.newInstance(context);
        }
        return metadataList.get(0);
    }
}
