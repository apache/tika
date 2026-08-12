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
import static org.apache.tika.server.core.resource.TikaResource.setupContentHandlerFactory;
import static org.apache.tika.server.core.resource.TikaResource.setupContentHandlerFactoryIfNeeded;

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
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.server.core.MetadataList;

@Path("/rmeta")
public class RecursiveMetadataResource {

    protected static final String HANDLER_TYPE_PARAM = "handler";
    protected static final BasicContentHandlerFactory.HANDLER_TYPE DEFAULT_HANDLER_TYPE = BasicContentHandlerFactory.HANDLER_TYPE.MARKDOWN;
    private static final Logger LOG = LoggerFactory.getLogger(RecursiveMetadataResource.class);

    private final TikaResource tikaResource;

    public RecursiveMetadataResource(TikaResource tikaResource) {
        this.tikaResource = tikaResource;
    }

    /**
     * Parses content and returns metadata list.
     * Metadata filtering is done in the child process, so no filtering needed here.
     */
    public List<Metadata> parseMetadata(TikaInputStream tis, Metadata metadata,
                                               MultivaluedMap<String, String> httpHeaders,
                                               String handlerTypeName)
            throws Exception {

        final ParseContext context = tikaResource.createParseContext();

        fillMetadata(null, metadata, httpHeaders);
        TikaResource.logRequest(LOG, "/rmeta", metadata);

        setupContentHandlerFactory(context, handlerTypeName);

        // Filtering is done in child process, no need to filter again
        return tikaResource.parseWithPipes(tis, metadata, context, ParseMode.RMETA);
    }

    /**
     * Returns an InputStream that can be deserialized as a list of
     * {@link Metadata} objects.
     * The first in the list represents the main document, and the
     * rest represent metadata for the embedded objects.  This works
     * recursively through all descendants of the main document, not
     * just the immediate children.
     * <p>
     * The extracted text content is stored with the key
     * {@link org.apache.tika.metadata.TikaCoreProperties#TIKA_CONTENT}.
     * <p>
     * Specify the handler for the content (xml, html, text, markdown, ignore)
     * in the path:<br/>
     * /rmeta/form (default: markdown)<br/>
     * /rmeta/form/xml      (store the content as xml)<br/>
     * /rmeta/form/text     (store the content as text)<br/>
     * /rmeta/form/markdown (store the content as markdown)<br/>
     * /rmeta/form/ignore   (don't record any content)<br/>
     *
     * @param att             attachment
     * @param info            uri info
     * @param handlerTypeName which type of handler to use
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces({"application/json"})
    @Path("form{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Response getMetadataFromMultipart(Attachment att, @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName) throws Exception {
        ParseContext context = tikaResource.createParseContext();
        try (TikaInputStream tis = TikaInputStream.get(att.getObject(InputStream.class))) {
            tis.getPath(); // Spool to temp file for pipes-based parsing
            List<Metadata> metadataList = parseMetadata(tis, Metadata.newInstance(context), att.getHeaders(),
                    handlerTypeName);
            return Response.ok(new MetadataList(metadataList)).build();
        }
    }

    /**
     * Multipart endpoint with per-request ParseContext configuration.
     * Accepts two parts: "file" (the document) and "config" (JSON configuration with parseContext).
     * Uses the default handler type (Markdown).
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces({"application/json"})
    @Path("config")
    public Response getMetadataWithConfig(
            List<Attachment> attachments,
            @Context HttpHeaders httpHeaders) throws Exception {

        ParseContext context = tikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        try (TikaInputStream tis = tikaResource.setupMultipartConfig(attachments, metadata, context)) {

            TikaResource.logRequest(LOG, "/rmeta/config", metadata);

            return Response
                    .ok(parseMetadataWithContext(tis, metadata, null, context))
                    .build();
        }
    }

    private MetadataList parseMetadataWithContext(TikaInputStream tis, Metadata metadata,
                                                  String handlerTypeName, ParseContext context) throws Exception {
        setupContentHandlerFactoryIfNeeded(context, handlerTypeName);

        // Filtering is done in child process, no need to filter again
        List<Metadata> metadataList = tikaResource.parseWithPipes(tis, metadata, context, ParseMode.RMETA);
        return new MetadataList(metadataList);
    }

    /**
     * Returns an InputStream that can be deserialized as a list of
     * {@link Metadata} objects.
     * The first in the list represents the main document, and the
     * rest represent metadata for the embedded objects.  This works
     * recursively through all descendants of the main document, not
     * just the immediate children.
     * <p>
     * The extracted text content is stored with the key
     * {@link org.apache.tika.metadata.TikaCoreProperties#TIKA_CONTENT}.
     * <p>
     * Specify the handler for the content (xml, html, text, markdown, ignore)
     * in the path:<br/>
     * /rmeta (default: markdown)<br/>
     * /rmeta/xml      (store the content as xml)<br/>
     * /rmeta/text     (store the content as text)<br/>
     * /rmeta/markdown (store the content as markdown)<br/>
     * /rmeta/ignore   (don't record any content)<br/>
     *
     * @param handlerTypeName which type of handler to use
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */

    @PUT
    @Produces("application/json")
    @Path("{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName) throws Exception {
        ParseContext context = tikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            tis.getPath(); // Spool to temp file for pipes-based parsing
            List<Metadata> metadataList = parseMetadata(tis, metadata, httpHeaders.getRequestHeaders(),
                    handlerTypeName);
            return Response.ok(new MetadataList(metadataList)).build();
        }
    }
}
