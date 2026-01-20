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
import static org.apache.tika.server.core.resource.TikaResource.getTikaLoader;
import static org.apache.tika.server.core.resource.TikaResource.getWriteLimit;
import static org.apache.tika.server.core.resource.TikaResource.setupContentHandlerFactory;
import static org.apache.tika.server.core.resource.TikaResource.setupContentHandlerFactoryIfNeeded;
import static org.apache.tika.server.core.resource.TikaResource.setupMultipartConfig;

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
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.server.core.MetadataList;

@Path("/rmeta")
public class RecursiveMetadataResource {

    protected static final String HANDLER_TYPE_PARAM = "handler";
    protected static final BasicContentHandlerFactory.HANDLER_TYPE DEFAULT_HANDLER_TYPE = BasicContentHandlerFactory.HANDLER_TYPE.XML;
    private static final Logger LOG = LoggerFactory.getLogger(RecursiveMetadataResource.class);

    public static List<Metadata> parseMetadata(TikaInputStream tis, Metadata metadata, MultivaluedMap<String, String> httpHeaders,
                                               ServerHandlerConfig handlerConfig)
            throws Exception {

        final ParseContext context = new ParseContext();

        fillMetadata(null, metadata, httpHeaders);
        TikaResource.logRequest(LOG, "/rmeta", metadata);

        // Set up handler factory in context using shared utility
        setupContentHandlerFactory(context, handlerConfig.type().toString(), handlerConfig.writeLimit(),
                handlerConfig.throwOnWriteLimitReached());

        List<Metadata> metadataList = TikaResource.parseWithPipes(tis, metadata, context, ParseMode.RMETA);
        MetadataFilter metadataFilter = context.get(MetadataFilter.class, getTikaLoader().loadMetadataFilters());
        metadataFilter.filter(metadataList);
        return metadataList;
    }

    static ServerHandlerConfig buildHandlerConfig(MultivaluedMap<String, String> httpHeaders, String handlerTypeName, ParseMode parseMode) {
        int maxEmbeddedResources = -1;
        if (httpHeaders.containsKey("maxEmbeddedResources")) {
            maxEmbeddedResources = Integer.parseInt(httpHeaders.getFirst("maxEmbeddedResources"));
        }
        return new ServerHandlerConfig(BasicContentHandlerFactory.parseHandlerType(handlerTypeName, DEFAULT_HANDLER_TYPE), parseMode,
                getWriteLimit(httpHeaders), maxEmbeddedResources, TikaResource.getThrowOnWriteLimitReached(httpHeaders));
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
     * Specify the handler for the content (xml, html, text, ignore)
     * in the path:<br/>
     * /rmeta/form (default: xml)<br/>
     * /rmeta/form/xml    (store the content as xml)<br/>
     * /rmeta/form/text   (store the content as text)<br/>
     * /rmeta/form/ignore (don't record any content)<br/>
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
        try (TikaInputStream tis = TikaInputStream.get(att.getObject(InputStream.class))) {
            return Response
                    .ok(parseMetadataToMetadataList(tis, new Metadata(), att.getHeaders(),
                            buildHandlerConfig(att.getHeaders(), handlerTypeName, ParseMode.RMETA)))
                    .build();
        }
    }

    /**
     * Multipart endpoint with per-request ParseContext configuration.
     * Accepts two parts: "file" (the document) and "config" (JSON configuration with parseContext).
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces({"application/json"})
    @Path("config{" + HANDLER_TYPE_PARAM + " : (/\\w+)?}")
    public Response getMetadataWithConfig(
            List<Attachment> attachments,
            @Context HttpHeaders httpHeaders,
            @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName) throws Exception {

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        try (TikaInputStream tis = setupMultipartConfig(attachments, metadata, context)) {

            TikaResource.logRequest(LOG, "/rmeta/config", metadata);

            return Response
                    .ok(parseMetadataWithContext(tis, metadata, httpHeaders.getRequestHeaders(),
                            buildHandlerConfig(httpHeaders.getRequestHeaders(), handlerTypeName != null ? handlerTypeName.substring(1) : null, ParseMode.RMETA),
                            context))
                    .build();
        }
    }

    private MetadataList parseMetadataWithContext(TikaInputStream tis, Metadata metadata, MultivaluedMap<String, String> httpHeaders,
                                                  ServerHandlerConfig handlerConfig, ParseContext context) throws Exception {
        // Set up handler factory in context if not already set using shared utility
        setupContentHandlerFactoryIfNeeded(context, handlerConfig.type().toString(),
                handlerConfig.writeLimit(), handlerConfig.throwOnWriteLimitReached());

        List<Metadata> metadataList = TikaResource.parseWithPipes(tis, metadata, context, ParseMode.RMETA);
        MetadataFilter metadataFilter = context.get(MetadataFilter.class, getTikaLoader().loadMetadataFilters());
        metadataFilter.filter(metadataList);
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
     * Specify the handler for the content (xml, html, text, ignore)
     * in the path:<br/>
     * /rmeta (default: xml)<br/>
     * /rmeta/xml    (store the content as xml)<br/>
     * /rmeta/text   (store the content as text)<br/>
     * /rmeta/ignore (don't record any content)<br/>
     *
     * @param handlerTypeName which type of handler to use
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */

    @PUT
    @Produces("application/json")
    @Path("{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Response getMetadata(InputStream is, @Context HttpHeaders httpHeaders, @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            return Response
                    .ok(parseMetadataToMetadataList(tis, metadata, httpHeaders.getRequestHeaders(),
                            buildHandlerConfig(httpHeaders.getRequestHeaders(), handlerTypeName, ParseMode.RMETA)))
                    .build();
        }
    }

    private MetadataList parseMetadataToMetadataList(TikaInputStream tis, Metadata metadata,
                                                     MultivaluedMap<String, String> httpHeaders, ServerHandlerConfig handlerConfig)
            throws Exception {
        return new MetadataList(parseMetadata(tis, metadata, httpHeaders, handlerConfig));
    }
}
