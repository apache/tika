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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.attachment.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.Tika;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.apache.tika.sax.RichTextContentHandler;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.server.core.InputStreamFactory;
import org.apache.tika.server.core.ServerStatus;
import org.apache.tika.server.core.TikaServerConfig;
import org.apache.tika.server.core.TikaServerParseException;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.XMLReaderUtils;

@Path("/tika")
public class TikaResource {

    public static final String GREETING = "This is Tika Server (" + Tika.getString() + "). Please PUT\n";
    private static final String META_PREFIX = "meta_";
    private static final Logger LOG = LoggerFactory.getLogger(TikaResource.class);
    private static Pattern ALLOWABLE_HEADER_CHARS = Pattern.compile("(?i)^[-/_+\\.A-Z0-9 ]+$");
    private static TikaLoader TIKA_LOADER;
    private static TikaServerConfig TIKA_SERVER_CONFIG;
    private static InputStreamFactory INPUTSTREAM_FACTORY = null;
    private static ServerStatus SERVER_STATUS = null;
    private static PipesParsingHelper PIPES_PARSING_HELPER = null;

    /**
     * Initialize TikaResource without pipes-based parsing (legacy mode).
     */
    public static void init(TikaLoader tikaLoader, TikaServerConfig tikaServerConfig, InputStreamFactory inputStreamFactory,
                            ServerStatus serverStatus) {
        init(tikaLoader, tikaServerConfig, inputStreamFactory, serverStatus, null);
    }

    /**
     * Initialize TikaResource with pipes-based parsing for process isolation.
     *
     * @param tikaLoader the Tika loader
     * @param tikaServerConfig server configuration
     * @param inputStreamFactory input stream factory
     * @param serverStatus server status tracker
     * @param pipesParsingHelper helper for pipes-based parsing (may be null for legacy mode)
     */
    public static void init(TikaLoader tikaLoader, TikaServerConfig tikaServerConfig, InputStreamFactory inputStreamFactory,
                            ServerStatus serverStatus, PipesParsingHelper pipesParsingHelper) {
        TIKA_LOADER = tikaLoader;
        TIKA_SERVER_CONFIG = tikaServerConfig;
        INPUTSTREAM_FACTORY = inputStreamFactory;
        SERVER_STATUS = serverStatus;
        PIPES_PARSING_HELPER = pipesParsingHelper;
    }

    /**
     * Returns true if pipes-based parsing is enabled.
     */
    public static boolean isPipesParsingEnabled() {
        return PIPES_PARSING_HELPER != null;
    }

    /**
     * Gets the PipesParsingHelper instance.
     *
     * @return the helper, or null if pipes-based parsing is not enabled
     */
    public static PipesParsingHelper getPipesParsingHelper() {
        return PIPES_PARSING_HELPER;
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

    public static TikaInputStream getInputStream(InputStream is, Metadata metadata, HttpHeaders headers, UriInfo uriInfo) {
        try {
            return INPUTSTREAM_FACTORY.getInputStream(is, metadata, headers, uriInfo);
        } catch (IOException e) {
            throw new TikaServerParseException(e);
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

        for (Attachment att : attachments) {
            ContentDisposition cd = att.getContentDisposition();
            if (cd != null) {
                String name = cd.getParameter("name");
                if ("file".equals(name)) {
                    fileAtt = att;
                } else if ("config".equals(name)) {
                    configAtt = att;
                }
            }
        }

        if (fileAtt == null) {
            throw new IOException("Missing 'file' attachment");
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

        // Process config JSON if provided
        if (configAtt != null) {
            String configJson = new String(configAtt.getObject(InputStream.class).readAllBytes(),
                    StandardCharsets.UTF_8);
            mergeParseContextFromConfig(configJson, context);
        }

        return TikaInputStream.get(fileAtt.getObject(InputStream.class));
    }

    /**
     * Use this to call a parser and unify exception handling.
     * NOTE: This call to parse closes the TikaInputStream. DO NOT surround
     * the call in an auto-close block.
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
    public static void parse(Parser parser, Logger logger, String path, TikaInputStream inputStream, ContentHandler handler, Metadata metadata, ParseContext parseContext)
            throws IOException {

        checkIsOperating();
        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        long timeoutMillis = getTaskTimeout(parseContext);

        long taskId = SERVER_STATUS.start(ServerStatus.TASK.PARSE, fileName, timeoutMillis);
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
            SERVER_STATUS.setStatus(ServerStatus.STATUS.OOM);
        } finally {
            SERVER_STATUS.complete(taskId);
            inputStream.close();
        }
    }

    /**
     * Parses using pipes-based parsing with process isolation.
     * This method writes the input to a temp file, invokes PipesParser,
     * and returns the metadata list.
     *
     * @param inputStream the input stream to parse
     * @param metadata metadata to pass to the parser
     * @param parseContext parse context with handler configuration
     * @param parseMode RMETA or CONCATENATE
     * @return list of metadata objects from parsing
     * @throws IOException if parsing fails
     */
    public static List<Metadata> parseWithPipes(InputStream inputStream, Metadata metadata,
                                                 ParseContext parseContext, ParseMode parseMode)
            throws IOException {
        checkIsOperating();

        if (PIPES_PARSING_HELPER == null) {
            throw new IllegalStateException("Pipes-based parsing is not enabled");
        }

        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        LOG.debug("Parsing with pipes: {}", fileName);

        return PIPES_PARSING_HELPER.parse(inputStream, metadata, parseContext, parseMode,
                TIKA_SERVER_CONFIG.isReturnStackTrace());
    }

    public static void checkIsOperating() {
        //check that server is not in shutdown mode
        if (!SERVER_STATUS.isOperating()) {
            throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
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

    public static long getTaskTimeout(ParseContext parseContext) {
       return TikaTaskTimeout.getTimeoutMillis(parseContext, TIKA_SERVER_CONFIG.getTaskTimeoutMillis());
    }

    @GET
    @Produces("text/plain")
    public String getMessage() {
        checkIsOperating();
        return GREETING;
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain")
    @Path("form")
    public StreamingOutput getTextFromMultipart(Attachment att, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        return produceText(TikaInputStream.get(att.getObject(InputStream.class)), new Metadata(), preparePostHeaderMap(att, httpHeaders), info);
    }

    /**
     * Multipart endpoint with per-request JSON configuration.
     * Accepts two parts: "file" (the document) and "config" (JSON configuration).
     * <p>
     * The config JSON should contain parser configs at the root level, e.g.:
     * <pre>
     * {
     *   "pdf-parser": { "ocrStrategy": "no_ocr" },
     *   "tesseract-ocr-parser": { "language": "eng" }
     * }
     * </pre>
     */
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain")
    @Path("config")
    public StreamingOutput getTextWithConfig(
            List<Attachment> attachments,
            @Context HttpHeaders httpHeaders,
            @Context final UriInfo info) throws TikaConfigException, IOException {

        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();
        final TikaInputStream tis = setupMultipartConfig(attachments, metadata, context);

        final Parser parser = createParser();
        logRequest(LOG, "/tika/config", metadata);

        return new ParseStreamingOutput(tis, parser, LOG, info.getPath(), metadata, context,
                os -> new BodyContentHandler(new RichTextContentHandler(new OutputStreamWriter(os, UTF_8))));
    }

    //this is equivalent to text-main in tika-app
    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    @Path("main")
    public StreamingOutput getTextMain(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        return produceTextMain(TikaInputStream.get(is), httpHeaders.getRequestHeaders(), info);
    }

    //this is equivalent to text-main (Boilerpipe handler) in tika-app
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain")
    @Path("form/main")
    public StreamingOutput getTextMainFromMultipart(final Attachment att, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        return produceTextMain(TikaInputStream.get(att.getObject(InputStream.class)), preparePostHeaderMap(att, httpHeaders), info);
    }

    public StreamingOutput produceTextMain(final TikaInputStream tis, MultivaluedMap<String, String> httpHeaders, final UriInfo info) throws TikaConfigException, IOException {
        final Parser parser = createParser();
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);

        logRequest(LOG, "/tika", metadata);

        return new ParseStreamingOutput(tis, parser, LOG, info.getPath(), metadata, context,
                os -> new BoilerpipeContentHandler(new OutputStreamWriter(os, UTF_8)));
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    public StreamingOutput getText(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        final Metadata metadata = new Metadata();
        return produceText(getInputStream(is, metadata, httpHeaders, info), metadata, httpHeaders.getRequestHeaders(), info);
    }

    public StreamingOutput produceText(final TikaInputStream tis, final Metadata metadata, MultivaluedMap<String, String> httpHeaders, final UriInfo info)
            throws TikaConfigException, IOException {
        final Parser parser = createParser();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);

        logRequest(LOG, "/tika", metadata);

        return new ParseStreamingOutput(tis, parser, LOG, info.getPath(), metadata, context,
                os -> new BodyContentHandler(new RichTextContentHandler(new OutputStreamWriter(os, UTF_8))));
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/html")
    @Path("form")
    public StreamingOutput getHTMLFromMultipart(Attachment att, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        LOG.info("loading multipart html");

        return produceOutput(TikaInputStream.get(att.getObject(InputStream.class)), new Metadata(), preparePostHeaderMap(att, httpHeaders), info, "html");
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/html")
    public StreamingOutput getHTML(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        Metadata metadata = new Metadata();
        return produceOutput(getInputStream(is, metadata, httpHeaders, info), metadata, httpHeaders.getRequestHeaders(), info, "html");
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/xml")
    @Path("form")
    public StreamingOutput getXMLFromMultipart(Attachment att, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        LOG.info("loading multipart xml");

        return produceOutput(TikaInputStream.get(att.getObject(InputStream.class)), new Metadata(), preparePostHeaderMap(att, httpHeaders), info, "xml");
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/xml")
    public StreamingOutput getXML(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) throws TikaConfigException, IOException {
        Metadata metadata = new Metadata();
        return produceOutput(getInputStream(is, metadata, httpHeaders, info), metadata, httpHeaders.getRequestHeaders(), info, "xml");
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("application/json")
    @Path("form{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Metadata getJsonFromMultipart(Attachment att, @Context HttpHeaders httpHeaders, @Context final UriInfo info, @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName)
            throws IOException, TikaException {
        Metadata metadata = new Metadata();
        List<Metadata> metadataList;

        if (isPipesParsingEnabled()) {
            // Use pipes-based parsing
            TikaInputStream tis = getInputStream(att.getObject(InputStream.class), metadata, httpHeaders, info);
            MultivaluedMap<String, String> headers = preparePostHeaderMap(att, httpHeaders);
            fillMetadata(null, metadata, headers);
            logRequest(LOG, "/tika", metadata);

            ParseContext context = new ParseContext();
            setupHandlerFactory(context, handlerTypeName, headers);
            metadataList = parseWithPipes(tis, metadata, context, ParseMode.CONCATENATE);
        } else {
            // Legacy in-process parsing
            parseToMetadata(getInputStream(att.getObject(InputStream.class), metadata, httpHeaders, info), metadata, preparePostHeaderMap(att, httpHeaders), info, handlerTypeName);
            metadataList = new ArrayList<>();
            metadataList.add(metadata);
        }

        TikaResource.getTikaLoader().loadMetadataFilters().filter(metadataList);
        if (metadataList.isEmpty()) {
            return new Metadata();
        }
        return metadataList.get(0);
    }

    @PUT
    @Consumes("*/*")
    @Produces("application/json")
    @Path("{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Metadata getJson(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info, @PathParam(HANDLER_TYPE_PARAM) String handlerTypeName)
            throws IOException, TikaException {
        Metadata metadata = new Metadata();
        List<Metadata> metadataList;

        if (isPipesParsingEnabled()) {
            // Use pipes-based parsing
            TikaInputStream tis = getInputStream(is, metadata, httpHeaders, info);
            MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();
            fillMetadata(null, metadata, headers);
            logRequest(LOG, "/tika", metadata);

            ParseContext context = new ParseContext();
            setupHandlerFactory(context, handlerTypeName, headers);
            metadataList = parseWithPipes(tis, metadata, context, ParseMode.CONCATENATE);
        } else {
            // Legacy in-process parsing
            parseToMetadata(getInputStream(is, metadata, httpHeaders, info), metadata, httpHeaders.getRequestHeaders(), info, handlerTypeName);
            metadataList = new ArrayList<>();
            metadataList.add(metadata);
        }

        TikaResource.getTikaLoader().loadMetadataFilters().filter(metadataList);
        if (metadataList.isEmpty()) {
            return new Metadata();
        }
        return metadataList.get(0);
    }

    /**
     * Sets up the ContentHandlerFactory in the ParseContext based on handler type and HTTP headers.
     */
    private void setupHandlerFactory(ParseContext context, String handlerTypeName, MultivaluedMap<String, String> httpHeaders) {
        int writeLimit = -1;
        boolean throwOnWriteLimitReached = getThrowOnWriteLimitReached(httpHeaders);
        if (httpHeaders.containsKey("writeLimit")) {
            writeLimit = Integer.parseInt(httpHeaders.getFirst("writeLimit"));
        }

        BasicContentHandlerFactory.HANDLER_TYPE type = BasicContentHandlerFactory.parseHandlerType(handlerTypeName, DEFAULT_HANDLER_TYPE);
        ContentHandlerFactory factory = new BasicContentHandlerFactory(type, writeLimit, throwOnWriteLimitReached, context);
        context.set(ContentHandlerFactory.class, factory);
    }

    private void parseToMetadata(TikaInputStream tis, Metadata metadata, MultivaluedMap<String, String> httpHeaders, UriInfo info, String handlerTypeName)
            throws IOException, TikaConfigException {
        final Parser parser = createParser();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);

        logRequest(LOG, "/tika", metadata);
        int writeLimit = -1;
        boolean throwOnWriteLimitReached = getThrowOnWriteLimitReached(httpHeaders);
        if (httpHeaders.containsKey("writeLimit")) {
            writeLimit = Integer.parseInt(httpHeaders.getFirst("writeLimit"));
        }

        // Check if a ContentHandlerFactory was provided in ParseContext (e.g., from config JSON)
        ContentHandlerFactory fact = context.get(ContentHandlerFactory.class);
        if (fact == null) {
            // Fall back to creating one from HTTP headers
            BasicContentHandlerFactory.HANDLER_TYPE type = BasicContentHandlerFactory.parseHandlerType(handlerTypeName, DEFAULT_HANDLER_TYPE);
            fact = new BasicContentHandlerFactory(type, writeLimit, throwOnWriteLimitReached, context);
        }
        ContentHandler contentHandler = fact.createHandler();

        try {
            parse(parser, LOG, info.getPath(), tis, contentHandler, metadata, context);
        } catch (TikaServerParseException e) {
            Throwable cause = e.getCause();
            boolean writeLimitReached = false;
            if (WriteLimitReachedException.isWriteLimitReached(cause)) {
                metadata.set(TikaCoreProperties.WRITE_LIMIT_REACHED, "true");
                writeLimitReached = true;
            }
            if (TIKA_SERVER_CONFIG.isReturnStackTrace()) {
                if (cause != null) {
                    metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, ExceptionUtils.getStackTrace(cause));
                } else {
                    metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, ExceptionUtils.getStackTrace(e));
                }
            } else if (!writeLimitReached) {
                throw e;
            }
        } catch (OutOfMemoryError e) {
            if (TIKA_SERVER_CONFIG.isReturnStackTrace()) {
                metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, ExceptionUtils.getStackTrace(e));
            } else {
                throw e;
            }
        } finally {
            metadata.add(TikaCoreProperties.TIKA_CONTENT, contentHandler.toString());
            tis.close();
        }
    }

    private StreamingOutput produceOutput(final TikaInputStream tis, Metadata metadata, final MultivaluedMap<String, String> httpHeaders, final UriInfo info, final String format)
            throws TikaConfigException, IOException {
        final Parser parser = createParser();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);

        logRequest(LOG, "/tika", metadata);

        return outputStream -> {
            Writer writer = new OutputStreamWriter(outputStream, UTF_8);
            ContentHandler content;

            try {
                SAXTransformerFactory factory = XMLReaderUtils.getSAXTransformerFactory();
                TransformerHandler handler = factory.newTransformerHandler();
                handler
                        .getTransformer()
                        .setOutputProperty(OutputKeys.METHOD, format);
                handler
                        .getTransformer()
                        .setOutputProperty(OutputKeys.INDENT, "yes");
                handler
                        .getTransformer()
                        .setOutputProperty(OutputKeys.ENCODING, UTF_8.name());
                handler
                        .getTransformer()
                        .setOutputProperty(OutputKeys.VERSION, "1.1");
                handler.setResult(new StreamResult(writer));
                content = new ExpandedTitleContentHandler(handler);
            } catch (TransformerConfigurationException | TikaException e) {
                throw new WebApplicationException(e);
            }

            try {
                parse(parser, LOG, info.getPath(), tis, content, metadata, context);
                outputStream.flush();
            } finally {
                tis.close();
            }
        };
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

    //enables streaming output AND closing of the TikaInputStream after the parse.
    public class ParseStreamingOutput implements StreamingOutput {

        private final TikaInputStream tis;
        private final Parser parser;
        private final Logger logger;
        private final String path;
        private final Metadata metadata;
        private final ParseContext parseContext;
        private final Function<OutputStream, ContentHandler> handlerBuilder;

        public ParseStreamingOutput(TikaInputStream tis, Parser parser, Logger logger,
                                    String path, Metadata metadata, ParseContext parseContext,
                                    Function<OutputStream, ContentHandler> handlerBuilder) {
            this.tis = tis;
            this.parser = parser;
            this.logger = logger;
            this.path = path;
            this.metadata = metadata;
            this.parseContext = parseContext;
            this.handlerBuilder = handlerBuilder;
        }

        @Override
        public void write(OutputStream outputStream) throws IOException, WebApplicationException {
            ContentHandler contentHandler = handlerBuilder.apply(outputStream);
            try {
                parse(parser, logger, path, tis, contentHandler, metadata, parseContext);
                outputStream.flush();
            } finally {
                tis.close();
            }
        }

    }

}
