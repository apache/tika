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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.tika.server.core.resource.RecursiveMetadataResource.DEFAULT_HANDLER_TYPE;
import static org.apache.tika.server.core.resource.RecursiveMetadataResource.HANDLER_TYPE_PARAM;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.cxf.attachment.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.Tika;
import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.OutputLimits;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.writelimiter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.server.core.ServerStatus;

@Path("/tika")
public class TikaResource {

    public static final String GREETING = "This is Tika Server (" + Tika.getString() + "). Please PUT\n";
    private static final Logger LOG = LoggerFactory.getLogger(TikaResource.class);

    // Instance (not static): production only ever creates one CXF server -- and so
    // one TikaResource -- per JVM, so this was never a functional requirement, just
    // a shortcut. Static state here made every TikaResource-derived server config
    // process-wide, so two CXF servers in the same JVM (as tests do, for speed --
    // real deployments never do this) silently stomped on each other's config.
    private final TikaLoader tikaLoader;
    private final ServerStatus serverStatus;
    private final PipesParsingHelper pipesParsingHelper;
    // Whether per-request config injection (multipart "config" parts) is permitted.
    // Enforced in setupMultipartConfig so every config-consuming endpoint honors it.
    private final boolean allowPerRequestConfig;

    // Config-level parse-context defaults, resolved once at startup and kept as values rather
    // than as a shared ParseContext. Requests carry only their own deltas (see
    // createRequestContext); the forked worker loads these same defaults from the same config.
    private final MetadataWriteLimiterFactory configMetadataWriteLimiterFactory;
    private final OutputLimits configOutputLimits;
    private final ExceptionReporting configExceptionReporting;
    private final boolean configSuppliesContentHandlerFactory;

    /**
     * @param tikaLoader the Tika loader
     * @param serverStatus server status tracker
     * @param pipesParsingHelper helper for pipes-based parsing, may be null if /tika endpoint is not enabled
     * @param allowPerRequestConfig whether per-request config injection is permitted
     */
    public TikaResource(TikaLoader tikaLoader, ServerStatus serverStatus,
                         PipesParsingHelper pipesParsingHelper, boolean allowPerRequestConfig) {
        this.tikaLoader = tikaLoader;
        this.serverStatus = serverStatus;
        this.pipesParsingHelper = pipesParsingHelper;
        this.allowPerRequestConfig = allowPerRequestConfig;

        ParseContext configDefaults = loadConfigDefaults();
        this.configMetadataWriteLimiterFactory = configDefaults.get(MetadataWriteLimiterFactory.class);
        this.configOutputLimits = OutputLimits.get(configDefaults);
        this.configExceptionReporting = ExceptionReporting.get(configDefaults);
        this.configSuppliesContentHandlerFactory =
                configDefaults.get(ContentHandlerFactory.class) != null;
    }

    /**
     * Gets the PipesParsingHelper instance.
     *
     * @return the helper
     */
    public PipesParsingHelper getPipesParsingHelper() {
        return pipesParsingHelper;
    }

    /** The config's exception-reporting policy, for channels that have no ParseContext. */
    public ExceptionReporting getExceptionReporting() {
        return configExceptionReporting;
    }

    /**
     * Reads the config's {@code parse-context} section. Private and called once: the values we
     * need are cached above, and a request must not carry these defaults (see
     * {@link #createRequestContext()}).
     */
    private ParseContext loadConfigDefaults() {
        try {
            return tikaLoader.loadParseContext();
        } catch (TikaConfigException e) {
            // Fall back to empty context if loading fails
            LOG.warn("Failed to load ParseContext from config, using empty context", e);
            return new ParseContext();
        }
    }

    /**
     * Creates a ParseContext holding only what this request itself specifies.
     * <p>
     * Config-level {@code parse-context} defaults are deliberately absent. The forked worker
     * loads them from the same config and overlays the request on top, so sending them is
     * redundant -- and worse than redundant at the trust boundary: the worker clamps
     * request-supplied timeout limits but trusts its own config's, so a default that arrives
     * as request data gets treated as caller input and clamped.
     *
     * @return an empty, request-scoped ParseContext
     */
    public ParseContext createRequestContext() {
        return new ParseContext();
    }

    /**
     * Creates request metadata bounded by the config's metadata write limiter.
     * <p>
     * The limiter no longer rides in the request context, so it is applied here instead. This
     * metadata holds caller-supplied values (filename, headers), which is exactly what the
     * limiter is meant to bound.
     */
    public Metadata newRequestMetadata() {
        return configMetadataWriteLimiterFactory == null ? new Metadata()
                : new Metadata(configMetadataWriteLimiterFactory.newInstance());
    }

    /**
     * A fresh copy of the config's {@code unpack-config}, or null if none is declared.
     * <p>
     * The unpack path is the one place a config default must still travel: it is mutated
     * per request (zip, suffix strategy, emitter) and so overrides whatever the worker would
     * have loaded. Starting from a default-constructed instance instead would silently reset
     * operator settings the request never touches -- {@code maxUnpackBytes}, for one. Re-read
     * per call because the caller mutates the result; unpack requests are heavyweight enough
     * that the config read does not register.
     */
    public UnpackConfig newConfigUnpackConfig() {
        return loadConfigDefaults().get(UnpackConfig.class);
    }


    public TikaLoader getTikaLoader() {
        return tikaLoader;
    }

    public static String detectFilename(MultivaluedMap<String, String> httpHeaders) {

        String disposition = httpHeaders.getFirst("Content-Disposition");
        if (disposition != null) {
            ContentDisposition c = new ContentDisposition(disposition);

            // only support "attachment" dispositions
            if ("attachment".equals(c.getType())) {
                String fn = c.getParameter("filename");
                if (fn != null) {
                    return fn;
                }
            }
        }

        return null;
    }

    /**
     * Parses config JSON and merges parseContext entries into the provided ParseContext.
     *
     * @param configJson the JSON config string
     * @param context the ParseContext to merge into
     * @throws IOException if parsing fails
     */
    public static void mergeParseContextFromConfig(String configJson, ParseContext context) throws IOException, TikaConfigException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(configJson);
        // Request-supplied config: restrict so it cannot bind wire-blocked components.
        ParseContext configuredContext = ParseContextDeserializer.readParseContext(root, true);
        ParseContextUtils.resolveAll(configuredContext, Thread.currentThread().getContextClassLoader());
        // Copy resolved context entries
        for (Map.Entry<String, Object> entry : configuredContext.getContextMap().entrySet()) {
            try {
                Class<?> clazz = Class.forName(entry.getKey());
                context.set((Class) clazz, entry.getValue());
                LOG.debug("Merged contextMap entry {} into context", entry.getKey());
            } catch (ClassNotFoundException e) {
                LOG.warn("Could not load class for parseContext entry: {}", entry.getKey());
            }
        }
        // Copy jsonConfigs for lazy resolution by parsers (e.g., pdf-parser config)
        for (Map.Entry<String, JsonConfig> entry : configuredContext.getJsonConfigs().entrySet()) {
            context.setJsonConfig(entry.getKey(), entry.getValue().json());
            LOG.debug("Merged jsonConfig entry {} into context", entry.getKey());
        }
    }

    @SuppressWarnings("serial")
    public static void fillMetadata(Parser parser, Metadata metadata, MultivaluedMap<String, String> httpHeaders) {
        String fileName = detectFilename(httpHeaders);
        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }

        String contentTypeHeader = httpHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
        jakarta.ws.rs.core.MediaType mediaType = (contentTypeHeader == null || "*/*".equals(contentTypeHeader)) ? null : jakarta.ws.rs.core.MediaType.valueOf(contentTypeHeader);
        if (mediaType != null && "xml".equals(mediaType.getSubtype())) {
            mediaType = null;
        }

        if (mediaType != null && mediaType.equals(jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE)) {
            mediaType = null;
        }

        // Transport content types, not statements about the document: curl sends
        // x-www-form-urlencoded by default for --data-binary, and multipart/form-data
        // describes the envelope rather than the file inside it.
        if (mediaType != null
                && (mediaType.equals(jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                || mediaType.equals(jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA_TYPE))) {
            mediaType = null;
        }

        if (mediaType != null) {
            metadata.set(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE, mediaType.toString());
            metadata.add(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE, mediaType.toString());
        }

        if (httpHeaders.containsKey("Content-Length")) {
            metadata.set(org.apache.tika.metadata.HttpHeaders.CONTENT_LENGTH, httpHeaders.getFirst("Content-Length"));
        }
    }

    /**
     * Processes multipart attachments for /config endpoints.
     * Extracts the "file" and optional "config" attachments, sets up metadata
     * (filename, content-type) from the file attachment, and processes any
     * config JSON into the ParseContext.
     *
     * @param attachments the multipart attachments
     * @param metadata    metadata to populate with filename and content-type
     * @param context     parse context to populate from config JSON
     * @return TikaInputStream wrapping the file attachment's content
     * @throws IOException if file attachment is missing or config processing fails
     */
    public TikaInputStream setupMultipartConfig(List<Attachment> attachments,
                                                        Metadata metadata,
                                                        ParseContext context) throws IOException, TikaConfigException {
        Attachment fileAtt = null;
        Attachment configAtt = null;

        LOG.debug("setupMultipartConfig: received {} attachments", attachments.size());
        for (Attachment att : attachments) {
            ContentDisposition cd = att.getContentDisposition();
            String name = (cd != null) ? cd.getParameter("name") : null;
            String contentId = att.getContentId();
            LOG.debug("setupMultipartConfig: attachment contentId={}, cd name={}, contentType={}",
                    contentId, name, att.getContentType());
            if ("file".equals(name)) {
                fileAtt = att;
            } else if ("config".equals(name)) {
                configAtt = att;
            } else if ("config".equals(contentId)) {
                // Also check contentId for config (for simple attachment creation)
                LOG.debug("setupMultipartConfig: found config via contentId");
                configAtt = att;
            } else if (fileAtt == null && name == null) {
                // Unnamed attachment treated as the file (for simple single-file uploads)
                fileAtt = att;
            }
        }

        // Enforce the per-request config gate where the config part is actually
        // consumed, so every endpoint that accepts a config part honors
        // allowPerRequestConfig uniformly.
        if (configAtt != null && !allowPerRequestConfig) {
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN)
                    .entity("Per-request configuration is disabled. Set allowPerRequestConfig=true in server config.")
                    .type(MediaType.TEXT_PLAIN)
                    .build());
        }

        if (fileAtt == null) {
            // Caller error: 400 with the fix, not an unmapped IOException -> empty 500.
            throw new BadRequestException(
                    "Missing file attachment (use name='file' or send single unnamed attachment)");
        }

        // Set filename from content-disposition
        ContentDisposition cd = fileAtt.getContentDisposition();
        if (cd != null) {
            String filename = cd.getParameter("filename");
            if (filename != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
        }

        // Set content-type from the file attachment (not the multipart request headers)
        if (fileAtt.getContentType() != null) {
            String contentType = fileAtt.getContentType().toString();
            if (contentType != null && !contentType.startsWith("multipart/") &&
                    !"application/octet-stream".equals(contentType)) {
                metadata.set(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE, contentType);
                metadata.add(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE, contentType);
            }
        }

        // Lazy TikaInputStream: nothing is spooled until a consumer needs a file.
        TikaInputStream tis = TikaInputStream.get(fileAtt.getObject(InputStream.class));
        boolean handedOff = false;
        try {

            // Process config JSON if provided
            if (configAtt != null) {
                String configJson = new String(configAtt.getObject(InputStream.class).readAllBytes(),
                        StandardCharsets.UTF_8);
                LOG.debug("setupMultipartConfig: processing config JSON of length {}", configJson.length());
                try {
                    mergeParseContextFromConfig(configJson, context);
                } catch (IOException | TikaConfigException e) {
                    // Caller error (bad JSON, or a wire-blocked component): 400 with the
                    // reason, not a 500 whose reason the reporting policy may redact away.
                    throw new BadRequestException(
                            "Could not resolve the request config: " + e.getMessage(), e);
                }
            }
            handedOff = true;
            return tis;
        } finally {
            // A bad config part must not leak the already-spooled temp file: the caller
            // only closes the stream it receives, and on a throw it receives nothing.
            if (!handedOff) {
                try {
                    tis.close();
                } catch (IOException e) {
                    LOG.warn("Failed to close spooled input after a config error", e);
                }
            }
        }
    }

    /**
     * Parses using pipes-based parsing with process isolation.
     * <p>
     * The TikaInputStream should already be spooled to a temp file via {@link TikaInputStream#getPath()}.
     * The caller is responsible for closing the TikaInputStream after this method returns,
     * which will clean up any temp files.
     *
     * @param tis the TikaInputStream to parse
     * @param metadata metadata to pass to the parser
     * @param parseContext parse context with handler configuration
     * @param parseMode RMETA or CONCATENATE
     * @return list of metadata objects from parsing
     * @throws IOException if parsing fails
     */
    public List<Metadata> parseWithPipes(TikaInputStream tis, Metadata metadata,
                                                 ParseContext parseContext, ParseMode parseMode)
            throws IOException {
        if (pipesParsingHelper == null) {
            throw new IllegalStateException("Pipes-based parsing is not enabled");
        }

        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        long taskId = serverStatus.start(ServerStatus.TASK.PARSE, fileName);
        try {
            return pipesParsingHelper.parse(tis, metadata, parseContext, parseMode);
        } finally {
            serverStatus.complete(taskId);
        }
    }

    public static void logRequest(Logger logger, String endpoint, Metadata metadata) {

        if (metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE) == null) {
            logger.info("{} (autodetecting type)", endpoint);
        } else {
            logger.info("{} ({})", endpoint, metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE));
        }
    }

    /**
     * Sets up the ContentHandlerFactory in the ParseContext, taking the write limits from
     * {@link org.apache.tika.config.OutputLimits} in the context.
     *
     * @param context the ParseContext to configure
     * @param handlerTypeName the handler type name (text, html, xml, ignore), may be null for default
     */
    public void setupContentHandlerFactory(ParseContext context, String handlerTypeName) {
        BasicContentHandlerFactory.HANDLER_TYPE type;
        try {
            type = BasicContentHandlerFactory.parseHandlerType(handlerTypeName, DEFAULT_HANDLER_TYPE);
        } catch (IllegalArgumentException e) {
            // The name comes from the URL path, so this is the caller's typo, not our failure.
            throw new BadRequestException(e.getMessage());
        }
        // Request-supplied limits (per-request config) win; the cached config defaults are the
        // fallback. Neither can be left to OutputLimits.get(context) alone: it returns plain
        // defaults when absent, so a deltas-only context would silently drop the operator's
        // configured write limit -- and this factory is the one the worker honors.
        OutputLimits limits = context.get(OutputLimits.class);
        if (limits == null) {
            limits = configOutputLimits;
        }
        context.set(ContentHandlerFactory.class,
                new BasicContentHandlerFactory(type, limits.getWriteLimit(),
                        limits.isThrowOnWriteLimit(), context));
    }

    /**
     * Sets up the ContentHandlerFactory in the ParseContext if not already set.
     * Used when a ParseContext may already have a factory configured.
     *
     * @param context the ParseContext to configure
     * @param handlerTypeName the handler type name
     */
    public void setupContentHandlerFactoryIfNeeded(ParseContext context, String handlerTypeName) {
        // A config-declared factory still takes precedence; it is no longer visible in the
        // request context, so leaving the context untouched lets the worker resolve it from
        // the same config.
        if (context.get(ContentHandlerFactory.class) == null
                && !configSuppliesContentHandlerFactory) {
            setupContentHandlerFactory(context, handlerTypeName);
        }
    }

    // ==================== GET ====================

    @GET
    @Produces("text/plain;charset=UTF-8")
    public String getMessage() {
        return GREETING;
    }

    // ==================== PUT endpoints (raw bytes) ====================

    // try-with-resources in the helpers: the spooled temp file must be deleted even
    // if context setup or metadata filling throws before the parse begins.

    private Response putRaw(InputStream is, HttpHeaders httpHeaders, String handlerTypeName)
            throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            return produceRawOutput(tis, newRequestMetadata(),
                    httpHeaders.getRequestHeaders(), handlerTypeName);
        }
    }

    private Metadata putJson(InputStream is, HttpHeaders httpHeaders, String handlerTypeName)
            throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            return produceJson(tis, newRequestMetadata(),
                    httpHeaders.getRequestHeaders(), handlerTypeName);
        }
    }

    /**
     * Parse document and return Markdown content. This is the default output of the bare
     * /tika endpoint; use /tika/xml, /tika/html, /tika/text for the other formats.
     */
    @PUT
    @Consumes("*/*")
    @Produces("text/plain;charset=UTF-8")
    public Response getDefault(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        return putRaw(is, httpHeaders, "md");
    }

    /** Parse document and return body-only plain text. */
    @PUT
    @Consumes("*/*")
    @Produces("text/plain;charset=UTF-8")
    @Path("text")
    public Response getText(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        return putRaw(is, httpHeaders, "body");
    }

    /** Parse document and return HTML content. */
    @PUT
    @Consumes("*/*")
    @Produces("text/html;charset=UTF-8")
    @Path("html")
    public Response getHtml(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        return putRaw(is, httpHeaders, "html");
    }

    /** Parse document and return XML content. */
    @PUT
    @Consumes("*/*")
    @Produces("text/xml;charset=UTF-8")
    @Path("xml")
    public Response getXml(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        return putRaw(is, httpHeaders, "xml");
    }

    /** Parse document and return Markdown content. */
    @PUT
    @Consumes("*/*")
    @Produces("text/plain;charset=UTF-8")
    @Path("md")
    public Response getMarkdown(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        return putRaw(is, httpHeaders, "md");
    }

    /** Parse document and return JSON with metadata and Markdown content (the default handler). */
    @PUT
    @Consumes("*/*")
    @Produces("application/json")
    @Path("json")
    public Metadata getJsonDefault(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        return putJson(is, httpHeaders, null);
    }

    /**
     * Parse document and return JSON with metadata and the named handler's content.
     *
     * @param handlerTypeName content handler type: text, html, xml, body, markdown, ignore
     */
    @PUT
    @Consumes("*/*")
    @Produces("application/json")
    @Path("json/{" + HANDLER_TYPE_PARAM + "}")
    public Metadata getJson(final InputStream is, @Context HttpHeaders httpHeaders,
                            @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName)
            throws IOException {
        return putJson(is, httpHeaders, handlerTypeName);
    }

    // ==================== POST endpoints (multipart with optional config) ====================

    // All /tika/config* endpoints take a required "file" part and an optional "config"
    // part (JSON parser settings and handler type). They are gated behind
    // allowPerRequestConfig=true because per-request configuration could enable
    // dangerous operations. try-with-resources in the helpers: the spooled file part
    // must be deleted even when config processing or the parse throws.

    private Response postConfigured(List<Attachment> attachments, String handlerTypeName)
            throws IOException, TikaConfigException {
        ParseContext context = createRequestContext();
        Metadata metadata = newRequestMetadata();
        try (TikaInputStream tis = setupMultipartConfig(attachments, metadata, context)) {
            return produceRawOutput(tis, metadata, context, handlerTypeName);
        }
    }

    private Metadata postConfiguredJson(List<Attachment> attachments, String handlerTypeName)
            throws IOException, TikaConfigException {
        ParseContext context = createRequestContext();
        Metadata metadata = newRequestMetadata();
        try (TikaInputStream tis = setupMultipartConfig(attachments, metadata, context)) {
            return produceJson(tis, metadata, context, handlerTypeName);
        }
    }

    /** Multipart document with optional config; returns Markdown unless the config names a handler. */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain;charset=UTF-8")
    @Path("config")
    public Response postRaw(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException, TikaConfigException {
        return postConfigured(attachments, "md");
    }

    /** Multipart document with optional config; returns body-only plain text. */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain;charset=UTF-8")
    @Path("config/text")
    public Response postText(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException, TikaConfigException {
        return postConfigured(attachments, "body");
    }

    /** Multipart document with optional config; returns HTML. */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/html;charset=UTF-8")
    @Path("config/html")
    public Response postHtml(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException, TikaConfigException {
        return postConfigured(attachments, "html");
    }

    /** Multipart document with optional config; returns XML. */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/xml;charset=UTF-8")
    @Path("config/xml")
    public Response postXml(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException, TikaConfigException {
        return postConfigured(attachments, "xml");
    }

    /** Multipart document with optional config; returns Markdown. */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain;charset=UTF-8")
    @Path("config/md")
    public Response postMarkdown(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException, TikaConfigException {
        return postConfigured(attachments, "md");
    }

    /** Multipart document with optional config; returns JSON with the default (markdown) handler. */
    @POST
    @Consumes("multipart/form-data")
    @Produces("application/json")
    @Path("config/json")
    public Metadata postJson(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException, TikaConfigException {
        return postConfiguredJson(attachments, null);
    }

    /**
     * Multipart sibling of {@code PUT /tika/json/{handlerType}}. Without it, a POST caller
     * who wants text-in-JSON has nowhere to go: /tika/config/text returns raw text with no
     * metadata envelope.
     *
     * @param handlerTypeName content handler type: text, html, xml, body, markdown, ignore
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces("application/json")
    @Path("config/json/{" + HANDLER_TYPE_PARAM + "}")
    public Metadata postJsonWithHandler(List<Attachment> attachments, @Context HttpHeaders httpHeaders,
                                        @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName)
            throws IOException, TikaConfigException {
        return postConfiguredJson(attachments, handlerTypeName);
    }

    // ==================== Internal methods ====================

    /**
     * Produces raw streaming output (text, html, xml, md) using pipes-based parsing.
     */
    private Response produceRawOutput(TikaInputStream tis, Metadata metadata,
                                              MultivaluedMap<String, String> httpHeaders,
                                              String handlerTypeName) throws IOException {
        fillMetadata(null, metadata, httpHeaders);
        ParseContext context = createRequestContext();
        setupContentHandlerFactory(context, handlerTypeName);
        return produceRawOutputWithContext(tis, metadata, context, handlerTypeName);
    }

    /**
     * Produces raw streaming output with a pre-configured ParseContext (for PUT endpoints).
     * A container-level parse exception doesn't discard content already captured -- status
     * is 422 (no field to embed the exception in, unlike the JSON endpoints), but the body
     * still carries whatever content was actually extracted.
     */
    private Response produceRawOutputWithContext(TikaInputStream tis, Metadata metadata,
                                              ParseContext context,
                                              String handlerTypeName) throws IOException {
        logRequest(LOG, "/tika", metadata);

        // Ensure content handler factory is set (config may have set it)
        setupContentHandlerFactoryIfNeeded(context, handlerTypeName);

        LOG.debug("produceRawOutput: handlerType={}, contentHandlerFactory={}",
                handlerTypeName, context.get(ContentHandlerFactory.class));

        // Parse with pipes using CONTENT_ONLY mode - the metadata filter in
        // EmitHandler will strip everything except tk:content
        List<Metadata> metadataList =
                parseWithPipes(tis, metadata, context, ParseMode.CONTENT_ONLY);

        LOG.debug("produceRawOutput: parseWithPipes returned {} metadata objects", metadataList.size());

        // Extract content before checking for an exception -- content must not be
        // discarded just because a container-level exception also occurred.
        String content = "";
        boolean hasException = false;
        String exceptionMessage = null;
        if (!metadataList.isEmpty()) {
            String extracted = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
            LOG.debug("produceRawOutput: TIKA_CONTENT length={}", extracted != null ? extracted.length() : 0);
            if (extracted != null) {
                content = extracted;
            }
            exceptionMessage = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
            hasException = exceptionMessage != null && !exceptionMessage.isEmpty();
            if (hasException) {
                LOG.debug("produceRawOutput: parse exception: {}", exceptionMessage);
            }
        }
        // Raw endpoints have no envelope for the exception (unlike the JSON bodies), so the
        // 422 status signals the partial parse and the body carries the extracted content
        // only -- never the server-side exception/stack trace. Clients that need the
        // container exception should use /rmeta.
        final String finalContent = content;

        StreamingOutput streamingOutput = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, UTF_8)) {
                writer.write(finalContent);
                writer.flush();
            }
        };
        return Response.status(hasException ? 422 : Response.Status.OK.getStatusCode())
                .entity(streamingOutput)
                .build();
    }

    /**
     * Produces raw streaming output with a pre-configured ParseContext (for POST endpoints).
     */
    private Response produceRawOutput(TikaInputStream tis, Metadata metadata,
                                              ParseContext context,
                                              String handlerTypeName) throws IOException {
        return produceRawOutputWithContext(tis, metadata, context, handlerTypeName);
    }

    /**
     * Produces JSON output with metadata and content.
     */
    private Metadata produceJson(TikaInputStream tis, Metadata metadata,
                                  MultivaluedMap<String, String> headers,
                                  String handlerTypeName) throws IOException {
        fillMetadata(null, metadata, headers);
        ParseContext context = createRequestContext();
        setupContentHandlerFactory(context, handlerTypeName);
        return produceJsonWithContext(tis, metadata, context, handlerTypeName);
    }

    /**
     * Produces JSON output with a pre-configured ParseContext.
     */
    private Metadata produceJson(TikaInputStream tis, Metadata metadata,
                                  ParseContext context,
                                  String handlerTypeName) throws IOException {
        return produceJsonWithContext(tis, metadata, context, handlerTypeName);
    }

    /**
     * Produces JSON output with a pre-configured ParseContext.
     */
    private Metadata produceJsonWithContext(TikaInputStream tis, Metadata metadata,
                                  ParseContext context,
                                  String handlerTypeName) throws IOException {
        logRequest(LOG, "/tika", metadata);

        // Ensure content handler factory is set (config may have set it)
        setupContentHandlerFactoryIfNeeded(context, handlerTypeName);

        List<Metadata> metadataList =
                parseWithPipes(tis, metadata, context, ParseMode.CONCATENATE);

        if (metadataList.isEmpty()) {
            return newRequestMetadata();
        }
        return metadataList.get(0);
    }

}
