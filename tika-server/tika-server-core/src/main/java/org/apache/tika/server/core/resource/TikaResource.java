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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.cxf.attachment.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.Tika;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.server.core.ServerStatus;
import org.apache.tika.server.core.TikaServerParseException;

@Path("/tika")
public class TikaResource {

    public static final String GREETING = "This is Tika Server (" + Tika.getString() + "). Please PUT\n";
    /**
     * Header to specify the handler type for content extraction.
     * Valid values: text, html, xml, ignore (default: text)
     */
    public static final String HANDLER_TYPE_HEADER = "X-Tika-Handler";
    private static final String META_PREFIX = "meta_";
    private static final Logger LOG = LoggerFactory.getLogger(TikaResource.class);
    private static TikaLoader TIKA_LOADER;
    private static ServerStatus SERVER_STATUS = null;
    private static PipesParsingHelper PIPES_PARSING_HELPER = null;
    private static MetadataWriteLimiterFactory DEFAULT_METADATA_WRITE_LIMITER_FACTORY = null;

    /**
     * Initialize TikaResource with pipes-based parsing for process isolation.
     *
     * @param tikaLoader the Tika loader
     * @param serverStatus server status tracker
     * @param pipesParsingHelper helper for pipes-based parsing, may be null if /tika endpoint is not enabled
     */
    public static void init(TikaLoader tikaLoader, ServerStatus serverStatus,
                            PipesParsingHelper pipesParsingHelper) {
        TIKA_LOADER = tikaLoader;
        SERVER_STATUS = serverStatus;
        PIPES_PARSING_HELPER = pipesParsingHelper;
        // MetadataWriteLimiterFactory is now loaded dynamically via loadParseContext()
    }

    /**
     * Gets the PipesParsingHelper instance.
     *
     * @return the helper
     */
    public static PipesParsingHelper getPipesParsingHelper() {
        return PIPES_PARSING_HELPER;
    }

    /**
     * Creates a new ParseContext with defaults loaded from tika-config.
     * This loads components from "parse-context" such as DigesterFactory and MetadataWriteLimiterFactory.
     *
     * @return a new ParseContext with defaults applied
     */
    public static ParseContext createParseContext() {
        try {
            return TIKA_LOADER.loadParseContext();
        } catch (TikaConfigException e) {
            // Fall back to empty context if loading fails
            LOG.warn("Failed to load ParseContext from config, using empty context", e);
            return new ParseContext();
        }
    }


    @SuppressWarnings("serial")
    public static Parser createParser() throws TikaConfigException, IOException {
        return TIKA_LOADER.loadAutoDetectParser();
    }

    public static TikaLoader getTikaLoader() {
        return TIKA_LOADER;
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

        // this really should not be used, since it's not an official field
        return httpHeaders.getFirst("File-Name");
    }

    /**
     * Parses config JSON and merges parseContext entries into the provided ParseContext.
     *
     * @param configJson the JSON config string
     * @param context the ParseContext to merge into
     * @throws IOException if parsing fails
     */
    public static void mergeParseContextFromConfig(String configJson, ParseContext context) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(configJson);
        // Use root directly - the JSON should contain parser configs at the top level
        ParseContext configuredContext = ParseContextDeserializer.readParseContext(root, mapper);
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

        if (mediaType != null) {
            metadata.add(Metadata.CONTENT_TYPE, mediaType.toString());
            metadata.add(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE, mediaType.toString());
        }

        if (httpHeaders.containsKey("Content-Length")) {
            metadata.set(Metadata.CONTENT_LENGTH, httpHeaders.getFirst("Content-Length"));
        }

        for (Map.Entry<String, List<String>> e : httpHeaders.entrySet()) {
            if (e
                    .getKey()
                    .startsWith(META_PREFIX)) {
                String tikaKey = e
                        .getKey()
                        .substring(META_PREFIX.length());
                for (String value : e.getValue()) {
                    metadata.add(tikaKey, value);
                }
            }
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
    public static TikaInputStream setupMultipartConfig(List<Attachment> attachments,
                                                        Metadata metadata,
                                                        ParseContext context) throws IOException {
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

        if (fileAtt == null) {
            throw new IOException("Missing file attachment (use name='file' or send single unnamed attachment)");
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
                metadata.add(Metadata.CONTENT_TYPE, contentType);
                metadata.add(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE, contentType);
            }
        }

        // Create TikaInputStream and spool to temp file immediately.
        // This ensures the data is captured before any other processing
        // and TikaInputStream handles temp file cleanup automatically.
        TikaInputStream tis = TikaInputStream.get(fileAtt.getObject(InputStream.class));
        tis.getPath(); // Spool to temp file for pipes-based parsing

        // Process config JSON if provided
        if (configAtt != null) {
            String configJson = new String(configAtt.getObject(InputStream.class).readAllBytes(),
                    StandardCharsets.UTF_8);
            LOG.debug("setupMultipartConfig: processing config JSON of length {}", configJson.length());
            mergeParseContextFromConfig(configJson, context);
        }

        return tis;
    }

    /**
     * Use this to call a parser and unify exception handling.
     * NOTE: This call to parse closes the TikaInputStream. DO NOT surround
     * the call in an auto-close block.
     * <p>
     * This method is used by endpoints that don't yet use pipes-based parsing
     * (UnpackerResource, MetadataResource). For /tika and /rmeta endpoints,
     * use parseWithPipes() instead.
     *
     * @param parser       parser to use
     * @param logger       logger to use
     * @param path         file path
     * @param inputStream  TikaInputStream (which is closed by this call!)
     * @param handler      handler to use
     * @param metadata     metadata
     * @param parseContext parse context
     * @throws IOException wrapper for all exceptions
     */
    public static void parse(Parser parser, Logger logger, String path, TikaInputStream inputStream,
                             ContentHandler handler, Metadata metadata, ParseContext parseContext)
            throws IOException {

        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        long taskId = SERVER_STATUS.start(ServerStatus.TASK.PARSE, fileName);
        try {
            parser.parse(inputStream, handler, metadata, parseContext);
        } catch (SAXException e) {
            throw new TikaServerParseException(e);
        } catch (EncryptedDocumentException e) {
            logger.warn("{}: Encrypted document ({})", path, fileName, e);
            throw new TikaServerParseException(e);
        } catch (Exception e) {
            if (!WriteLimitReachedException.isWriteLimitReached(e)) {
                logger.warn("{}: Text extraction failed ({})", path, fileName, e);
            }
            throw new TikaServerParseException(e);
        } catch (OutOfMemoryError e) {
            logger.warn("{}: OOM ({})", path, fileName, e);
            throw new TikaServerParseException(new TikaException("Out of memory", e));
        } finally {
            SERVER_STATUS.complete(taskId);
            inputStream.close();
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
    public static List<Metadata> parseWithPipes(TikaInputStream tis, Metadata metadata,
                                                 ParseContext parseContext, ParseMode parseMode)
            throws IOException {
        if (PIPES_PARSING_HELPER == null) {
            throw new IllegalStateException("Pipes-based parsing is not enabled");
        }

        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        long taskId = SERVER_STATUS.start(ServerStatus.TASK.PARSE, fileName);
        try {
            return PIPES_PARSING_HELPER.parse(tis, metadata, parseContext, parseMode);
        } finally {
            SERVER_STATUS.complete(taskId);
        }
    }

    public static void logRequest(Logger logger, String endpoint, Metadata metadata) {

        if (metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE) == null) {
            logger.info("{} (autodetecting type)", endpoint);
        } else {
            logger.info("{} ({})", endpoint, metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE));
        }
    }

    public static boolean getThrowOnWriteLimitReached(MultivaluedMap<String, String> httpHeaders) {
        if (httpHeaders.containsKey("throwOnWriteLimitReached")) {
            String val = httpHeaders.getFirst("throwOnWriteLimitReached");
            if ("true".equalsIgnoreCase(val)) {
                return true;
            } else if ("false".equalsIgnoreCase(val)) {
                return false;
            } else {
                throw new IllegalArgumentException("'throwOnWriteLimitReached' must be either 'true' or 'false'");
            }
        }
        // Default: throw on write limit reached
        return true;
    }

    /**
     * Parses the writeLimit header value from HTTP headers.
     *
     * @param httpHeaders the HTTP headers
     * @return the write limit value, or -1 if not specified
     */
    public static int getWriteLimit(MultivaluedMap<String, String> httpHeaders) {
        if (httpHeaders.containsKey("writeLimit")) {
            return Integer.parseInt(httpHeaders.getFirst("writeLimit"));
        }
        return -1;
    }

    /**
     * Sets up the ContentHandlerFactory in the ParseContext based on handler type and HTTP headers.
     * This is a shared utility method used by both /tika and /rmeta endpoints.
     *
     * @param context the ParseContext to configure
     * @param handlerTypeName the handler type name (text, html, xml, ignore), may be null for default
     * @param httpHeaders the HTTP headers containing writeLimit and throwOnWriteLimitReached
     */
    public static void setupContentHandlerFactory(ParseContext context, String handlerTypeName,
                                                   MultivaluedMap<String, String> httpHeaders) {
        int writeLimit = getWriteLimit(httpHeaders);
        boolean throwOnWriteLimitReached = getThrowOnWriteLimitReached(httpHeaders);
        setupContentHandlerFactory(context, handlerTypeName, writeLimit, throwOnWriteLimitReached);
    }

    /**
     * Sets up the ContentHandlerFactory in the ParseContext based on explicit parameters.
     * This overload is used when the values have already been parsed (e.g., from ServerHandlerConfig).
     *
     * @param context the ParseContext to configure
     * @param handlerTypeName the handler type name (text, html, xml, ignore), may be null for default
     * @param writeLimit the write limit, or -1 for unlimited
     * @param throwOnWriteLimitReached whether to throw when write limit is reached
     */
    public static void setupContentHandlerFactory(ParseContext context, String handlerTypeName,
                                                   int writeLimit, boolean throwOnWriteLimitReached) {
        BasicContentHandlerFactory.HANDLER_TYPE type = BasicContentHandlerFactory.parseHandlerType(
                handlerTypeName, DEFAULT_HANDLER_TYPE);
        ContentHandlerFactory factory = new BasicContentHandlerFactory(type, writeLimit,
                throwOnWriteLimitReached, context);
        context.set(ContentHandlerFactory.class, factory);
    }

    /**
     * Sets up the ContentHandlerFactory in the ParseContext if not already set.
     * Used when a ParseContext may already have a factory configured.
     *
     * @param context the ParseContext to configure
     * @param handlerTypeName the handler type name
     * @param httpHeaders the HTTP headers
     */
    public static void setupContentHandlerFactoryIfNeeded(ParseContext context, String handlerTypeName,
                                                           MultivaluedMap<String, String> httpHeaders) {
        if (context.get(ContentHandlerFactory.class) == null) {
            setupContentHandlerFactory(context, handlerTypeName, httpHeaders);
        }
    }

    /**
     * Sets up the ContentHandlerFactory in the ParseContext if not already set.
     * This overload is used when the values have already been parsed.
     *
     * @param context the ParseContext to configure
     * @param handlerTypeName the handler type name
     * @param writeLimit the write limit, or -1 for unlimited
     * @param throwOnWriteLimitReached whether to throw when write limit is reached
     */
    public static void setupContentHandlerFactoryIfNeeded(ParseContext context, String handlerTypeName,
                                                           int writeLimit, boolean throwOnWriteLimitReached) {
        if (context.get(ContentHandlerFactory.class) == null) {
            setupContentHandlerFactory(context, handlerTypeName, writeLimit, throwOnWriteLimitReached);
        }
    }

    // ==================== GET ====================

    @GET
    @Produces("text/plain")
    public String getMessage() {
        return GREETING;
    }

    // ==================== PUT endpoints (raw bytes) ====================

    /**
     * Parse document and return XHTML content.
     */
    @PUT
    @Consumes("*/*")
    @Produces("text/xml")
    public StreamingOutput getXhtml(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        TikaInputStream tis = TikaInputStream.get(is);
        tis.getPath(); // Spool to temp file for pipes-based parsing
        ParseContext context = createParseContext();
        return produceRawOutput(tis, Metadata.newInstance(context), httpHeaders.getRequestHeaders(), "xml");
    }

    /**
     * Parse document and return plain text content.
     */
    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    @Path("text")
    public StreamingOutput getText(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        TikaInputStream tis = TikaInputStream.get(is);
        tis.getPath(); // Spool to temp file for pipes-based parsing
        ParseContext context = createParseContext();
        return produceRawOutput(tis, Metadata.newInstance(context), httpHeaders.getRequestHeaders(), "text");
    }

    /**
     * Parse document and return HTML content.
     */
    @PUT
    @Consumes("*/*")
    @Produces("text/html")
    @Path("html")
    public StreamingOutput getHtml(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        TikaInputStream tis = TikaInputStream.get(is);
        tis.getPath(); // Spool to temp file for pipes-based parsing
        ParseContext context = createParseContext();
        return produceRawOutput(tis, Metadata.newInstance(context), httpHeaders.getRequestHeaders(), "html");
    }

    /**
     * Parse document and return XML content.
     */
    @PUT
    @Consumes("*/*")
    @Produces("text/xml")
    @Path("xml")
    public StreamingOutput getXml(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        TikaInputStream tis = TikaInputStream.get(is);
        tis.getPath(); // Spool to temp file for pipes-based parsing
        ParseContext context = createParseContext();
        return produceRawOutput(tis, Metadata.newInstance(context), httpHeaders.getRequestHeaders(), "xml");
    }

    /**
     * Parse document and return JSON with metadata and text content.
     */
    @PUT
    @Consumes("*/*")
    @Produces("application/json")
    @Path("json")
    public Metadata getJsonDefault(final InputStream is, @Context HttpHeaders httpHeaders)
            throws IOException {
        TikaInputStream tis = TikaInputStream.get(is);
        tis.getPath(); // Spool to temp file for pipes-based parsing
        ParseContext context = createParseContext();
        return produceJson(tis, Metadata.newInstance(context), httpHeaders.getRequestHeaders(), "text");
    }

    /**
     * Parse document and return JSON with metadata and specified content type.
     *
     * @param handlerTypeName content handler type: text, html, or xml
     */
    @PUT
    @Consumes("*/*")
    @Produces("application/json")
    @Path("json/{" + HANDLER_TYPE_PARAM + "}")
    public Metadata getJson(final InputStream is, @Context HttpHeaders httpHeaders,
                            @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName)
            throws IOException {
        TikaInputStream tis = TikaInputStream.get(is);
        tis.getPath(); // Spool to temp file for pipes-based parsing
        ParseContext context = createParseContext();
        return produceJson(tis, Metadata.newInstance(context), httpHeaders.getRequestHeaders(), handlerTypeName);
    }

    // ==================== POST endpoints (multipart with optional config) ====================

    /**
     * Parse multipart document with optional config, return XHTML output.
     * <p>
     * Accepts multipart with:
     * - "file" part (required): the document to parse
     * - "config" part (optional): JSON configuration for parser settings and handler type
     * <p>
     * Returns XHTML by default. Use /tika/config/text, /tika/config/html, or /tika/config/xml for other formats.
     * <p>
     * This endpoint is gated behind enableUnsecureFeatures=true because per-request
     * configuration could enable dangerous operations.
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/xml")
    @Path("config")
    public StreamingOutput postRaw(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException {
        ParseContext context = createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        TikaInputStream tis = setupMultipartConfig(attachments, metadata, context);
        // Default to xml (XHTML) if no handler specified in config
        return produceRawOutput(tis, metadata, context, "xml");
    }

    /**
     * Parse multipart document with optional config, return plain text.
     * <p>
     * Accepts multipart with:
     * - "file" part (required): the document to parse
     * - "config" part (optional): JSON configuration for parser settings
     * <p>
     * This endpoint is gated behind enableUnsecureFeatures=true because per-request
     * configuration could enable dangerous operations.
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain")
    @Path("config/text")
    public StreamingOutput postText(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException {
        ParseContext context = createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        TikaInputStream tis = setupMultipartConfig(attachments, metadata, context);
        return produceRawOutput(tis, metadata, context, "text");
    }

    /**
     * Parse multipart document with optional config, return HTML.
     * <p>
     * Accepts multipart with:
     * - "file" part (required): the document to parse
     * - "config" part (optional): JSON configuration for parser settings
     * <p>
     * This endpoint is gated behind enableUnsecureFeatures=true because per-request
     * configuration could enable dangerous operations.
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/html")
    @Path("config/html")
    public StreamingOutput postHtml(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException {
        ParseContext context = createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        TikaInputStream tis = setupMultipartConfig(attachments, metadata, context);
        return produceRawOutput(tis, metadata, context, "html");
    }

    /**
     * Parse multipart document with optional config, return XML.
     * <p>
     * Accepts multipart with:
     * - "file" part (required): the document to parse
     * - "config" part (optional): JSON configuration for parser settings
     * <p>
     * This endpoint is gated behind enableUnsecureFeatures=true because per-request
     * configuration could enable dangerous operations.
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/xml")
    @Path("config/xml")
    public StreamingOutput postXml(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException {
        ParseContext context = createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        TikaInputStream tis = setupMultipartConfig(attachments, metadata, context);
        return produceRawOutput(tis, metadata, context, "xml");
    }

    /**
     * Parse multipart document with optional config, return JSON.
     * <p>
     * Accepts multipart with:
     * - "file" part (required): the document to parse
     * - "config" part (optional): JSON configuration for parser settings and handler type
     * <p>
     * Default handler is text. Use config to specify different handler type.
     * <p>
     * This endpoint is gated behind enableUnsecureFeatures=true because per-request
     * configuration could enable dangerous operations.
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces("application/json")
    @Path("config/json")
    public Metadata postJson(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws IOException {
        ParseContext context = createParseContext();
        Metadata metadata = Metadata.newInstance(context);
        TikaInputStream tis = setupMultipartConfig(attachments, metadata, context);
        return produceJson(tis, metadata, context, "text");
    }

    // ==================== Internal methods ====================

    /**
     * Produces raw streaming output (text, html, xml) using pipes-based parsing.
     */
    private StreamingOutput produceRawOutput(TikaInputStream tis, Metadata metadata,
                                              MultivaluedMap<String, String> httpHeaders,
                                              String handlerTypeName) throws IOException {
        fillMetadata(null, metadata, httpHeaders);
        ParseContext context = createParseContext();
        setupContentHandlerFactory(context, handlerTypeName, httpHeaders);
        return produceRawOutputWithContext(tis, metadata, context, handlerTypeName);
    }

    /**
     * Produces raw streaming output with a pre-configured ParseContext (for PUT endpoints).
     */
    private StreamingOutput produceRawOutputWithContext(TikaInputStream tis, Metadata metadata,
                                              ParseContext context,
                                              String handlerTypeName) throws IOException {
        logRequest(LOG, "/tika", metadata);

        // Ensure content handler factory is set (config may have set it)
        setupContentHandlerFactoryIfNeeded(context, handlerTypeName, -1, true);

        LOG.debug("produceRawOutput: handlerType={}, contentHandlerFactory={}",
                handlerTypeName, context.get(ContentHandlerFactory.class));

        // Parse with pipes
        List<Metadata> metadataList;
        try {
            metadataList = parseWithPipes(tis, metadata, context, ParseMode.CONCATENATE);
        } finally {
            tis.close();
        }

        LOG.debug("produceRawOutput: parseWithPipes returned {} metadata objects", metadataList.size());

        // For raw streaming endpoints, throw exception if there was a parse error
        // (JSON endpoints return exceptions in metadata)
        if (!metadataList.isEmpty()) {
            String exception = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
            if (exception != null && !exception.isEmpty()) {
                LOG.debug("produceRawOutput: parse exception: {}", exception);
                // Wrap in TikaException so TikaServerParseExceptionMapper returns 422
                throw new TikaServerParseException(new TikaException(exception));
            }
        }

        // Extract content from result
        String content = "";
        if (!metadataList.isEmpty()) {
            String extracted = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
            LOG.debug("produceRawOutput: TIKA_CONTENT length={}", extracted != null ? extracted.length() : 0);
            if (extracted != null) {
                content = extracted;
            }
        }
        final String finalContent = content;

        return outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, UTF_8)) {
                writer.write(finalContent);
                writer.flush();
            }
        };
    }

    /**
     * Produces raw streaming output with a pre-configured ParseContext (for POST endpoints).
     */
    private StreamingOutput produceRawOutput(TikaInputStream tis, Metadata metadata,
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
        ParseContext context = createParseContext();
        setupContentHandlerFactory(context, handlerTypeName, headers);
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
        setupContentHandlerFactoryIfNeeded(context, handlerTypeName, -1, true);

        List<Metadata> metadataList;
        try {
            metadataList = parseWithPipes(tis, metadata, context, ParseMode.CONCATENATE);
        } finally {
            tis.close();
        }

        if (metadataList.isEmpty()) {
            return Metadata.newInstance(context);
        }
        return metadataList.get(0);
    }

    /**
     * Prepares a multivalued map, combining attachment headers and request headers.
     * For multipart requests, the attachment's Content-Type takes priority over the
     * request's Content-Type (which is multipart/form-data).
     *
     * @param att         the attachment.
     * @param httpHeaders the http headers, fetched from context.
     * @return the case insensitive MetadataMap containing combined headers.
     */
    public static MetadataMap<String, String> preparePostHeaderMap(Attachment att, HttpHeaders httpHeaders) {
        if (att == null && httpHeaders == null) {
            return null;
        }
        MetadataMap<String, String> finalHeaders = new MetadataMap<>(false, true);
        if (httpHeaders != null && httpHeaders.getRequestHeaders() != null) {
            finalHeaders.putAll(httpHeaders.getRequestHeaders());
        }
        if (att != null && att.getHeaders() != null) {
            finalHeaders.putAll(att.getHeaders());
        }
        // For multipart, get the attachment's Content-Type which overrides the request's
        // multipart/form-data Content-Type. Check multiple sources:
        if (att != null) {
            String attachmentContentType = null;
            // First try getContentType() which returns the MediaType set via constructor
            if (att.getContentType() != null) {
                attachmentContentType = att.getContentType().toString();
            }
            // Also check the attachment's headers directly
            if (attachmentContentType == null && att.getHeaders() != null) {
                attachmentContentType = att.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            }
            if (attachmentContentType != null && !attachmentContentType.startsWith("multipart/")) {
                finalHeaders.putSingle(HttpHeaders.CONTENT_TYPE, attachmentContentType);
            }
        }
        return finalHeaders;
    }

}
