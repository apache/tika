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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.attachment.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.apache.tika.sax.RichTextContentHandler;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.apache.tika.server.core.CompositeParseContextConfig;
import org.apache.tika.server.core.InputStreamFactory;
import org.apache.tika.server.core.ParseContextConfig;
import org.apache.tika.server.core.ServerStatus;
import org.apache.tika.server.core.TikaServerConfig;
import org.apache.tika.server.core.TikaServerParseException;
import org.apache.tika.utils.ExceptionUtils;

@Path("/tika")
public class TikaResource {

    public static final String GREETING =
            "This is Tika Server (" + new Tika().toString() + "). Please PUT\n";
    private static final String META_PREFIX = "meta_";
    private static final Logger LOG = LoggerFactory.getLogger(TikaResource.class);
    private static Pattern ALLOWABLE_HEADER_CHARS = Pattern.compile("(?i)^[-/_+\\.A-Z0-9 ]+$");
    private static TikaConfig TIKA_CONFIG;
    private static TikaServerConfig TIKA_SERVER_CONFIG;
    private static DigestingParser.Digester DIGESTER = null;
    private static InputStreamFactory INPUTSTREAM_FACTORY = null;
    private static ServerStatus SERVER_STATUS = null;

    private static ParseContextConfig PARSE_CONTEXT_CONFIG = new CompositeParseContextConfig();


    public static void init(TikaConfig config, TikaServerConfig tikaServerConfg,
                            DigestingParser.Digester digester,
                            InputStreamFactory inputStreamFactory, ServerStatus serverStatus) {
        TIKA_CONFIG = config;
        TIKA_SERVER_CONFIG = tikaServerConfg;
        DIGESTER = digester;
        INPUTSTREAM_FACTORY = inputStreamFactory;
        SERVER_STATUS = serverStatus;
    }


    @SuppressWarnings("serial")
    public static Parser createParser() {
        final Parser parser = new AutoDetectParser(TIKA_CONFIG);

        if (DIGESTER != null) {
            boolean skipContainer = false;
            if (TIKA_CONFIG.getAutoDetectParserConfig().getDigesterFactory() != null &&
                    TIKA_CONFIG.getAutoDetectParserConfig().getDigesterFactory().isSkipContainerDocument()) {
                skipContainer = true;
            }
            return new DigestingParser(parser, DIGESTER, skipContainer);
        }
        return parser;
    }

    public static TikaConfig getConfig() {
        return TIKA_CONFIG;
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

    public static void fillParseContext(MultivaluedMap<String, String> httpHeaders,
                                        Metadata metadata, ParseContext parseContext) {
        PARSE_CONTEXT_CONFIG.configure(httpHeaders, metadata, parseContext);
    }

    public static InputStream getInputStream(InputStream is, Metadata metadata,
                                             HttpHeaders headers, UriInfo uriInfo) {
        try {
            return INPUTSTREAM_FACTORY.getInputStream(is, metadata, headers, uriInfo);
        } catch (IOException e) {
            throw new TikaServerParseException(e);
        }
    }

    /**
     * Utility method to set a property on a class via reflection.
     *
     * @param object the <code>Object</code> to set the property on.
     * @param key    the key of the HTTP Header.
     * @param val    the value of HTTP header.
     * @param prefix the name of the HTTP Header prefix used to find property.
     * @throws WebApplicationException thrown when field cannot be found.
     */
    public static void processHeaderConfig(Object object, String key, String val, String prefix) {
        try {
            String property = StringUtils.removeStartIgnoreCase(key, prefix);
            Field field = null;
            try {
                field = object.getClass().getDeclaredField(StringUtils.uncapitalize(property));
            } catch (NoSuchFieldException e) {
                // try to match field case-insensitive way
                for (Field aField : object.getClass().getDeclaredFields()) {
                    if (aField.getName().equalsIgnoreCase(property)) {
                        field = aField;
                        break;
                    }
                }
            }
            String setter = field != null ? field.getName() : property;
            setter = "set" + setter.substring(0, 1).toUpperCase(Locale.US) + setter.substring(1);
            //default assume string class
            //if there's a more specific type, e.g. double, int, boolean
            //try that.
            Class clazz = String.class;
            if (field != null) {
                if (field.getType() == int.class || field.getType() == Integer.class) {
                    clazz = int.class;
                } else if (field.getType() == double.class) {
                    clazz = double.class;
                } else if (field.getType() == Double.class) {
                    clazz = Double.class;
                } else if (field.getType() == float.class) {
                    clazz = float.class;
                } else if (field.getType() == Float.class) {
                    clazz = Float.class;
                } else if (field.getType() == boolean.class) {
                    clazz = boolean.class;
                } else if (field.getType() == Boolean.class) {
                    clazz = Boolean.class;
                } else if (field.getType() == long.class) {
                    clazz = long.class;
                } else if (field.getType() == Long.class) {
                    clazz = Long.class;
                }
            }

            Method m = tryToGetMethod(object, setter, clazz);
            //if you couldn't find more specific setter, back off
            //to string setter and try that.
            if (m == null && clazz != String.class) {
                m = tryToGetMethod(object, setter, String.class);
            }

            if (m != null) {
                if (clazz == String.class) {
                    checkTrustWorthy(setter, val);
                    m.invoke(object, val);
                } else if (clazz == int.class || clazz == Integer.class) {
                    m.invoke(object, Integer.parseInt(val));
                } else if (clazz == double.class || clazz == Double.class) {
                    m.invoke(object, Double.parseDouble(val));
                } else if (clazz == boolean.class || clazz == Boolean.class) {
                    m.invoke(object, Boolean.parseBoolean(val));
                } else if (clazz == float.class || clazz == Float.class) {
                    m.invoke(object, Float.parseFloat(val));
                } else if (clazz == long.class || clazz == Long.class) {
                    m.invoke(object, Long.parseLong(val));
                } else {
                    throw new IllegalArgumentException(
                            "setter must be String, int, float, double or boolean...for now");
                }
            } else {
                throw new NoSuchMethodException("Couldn't find: " + setter);
            }

        } catch (Throwable ex) {
            // TIKA-3345
            String error = (!(ex.getCause() instanceof IllegalArgumentException)) ?
                    String.format(Locale.ROOT, "%s is an invalid %s header", key, prefix) :
                    String.format(Locale.ROOT, "%s is an invalid %s header value", val, key);
            throw new WebApplicationException(error, Response.Status.BAD_REQUEST);
        }
    }

    private static void checkTrustWorthy(String setter, String val) {
        if (setter == null || val == null) {
            throw new IllegalArgumentException("setter and val must not be null");
        }
        if (setter.toLowerCase(Locale.US).contains("trusted")) {
            throw new IllegalArgumentException(
                    "Can't call a trusted method via tika-server headers");
        }
        Matcher m = ALLOWABLE_HEADER_CHARS.matcher(val);
        if (!m.find()) {
            throw new IllegalArgumentException(
                    "Header val: " + val + " contains illegal characters. " +
                            "Must contain: TikaResource.ALLOWABLE_HEADER_CHARS");
        }
    }

    /**
     * Tries to get method. Silently swallows NoMethodException and returns
     * <code>null</code> if not found.
     *
     * @param object the object to get method from.
     * @param method the name of the method to get.
     * @param clazz  the parameter type of the method to get.
     * @return the found method instance
     */
    private static Method tryToGetMethod(Object object, String method, Class clazz) {
        try {
            return object.getClass().getMethod(method, clazz);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @SuppressWarnings("serial")
    public static void fillMetadata(Parser parser, Metadata metadata,
                                    MultivaluedMap<String, String> httpHeaders) {
        String fileName = detectFilename(httpHeaders);
        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }

        String contentTypeHeader = httpHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
        javax.ws.rs.core.MediaType mediaType =
                (contentTypeHeader == null || "*/*".equals(contentTypeHeader)) ? null :
                        javax.ws.rs.core.MediaType.valueOf(contentTypeHeader);
        if (mediaType != null && "xml".equals(mediaType.getSubtype())) {
            mediaType = null;
        }

        if (mediaType != null &&
                mediaType.equals(javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE)) {
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
            if (e.getKey().startsWith(META_PREFIX)) {
                String tikaKey = e.getKey().substring(META_PREFIX.length());
                for (String value : e.getValue()) {
                    metadata.add(tikaKey, value);
                }
            }
        }
    }

    /**
     * Use this to call a parser and unify exception handling.
     * NOTE: This call to parse closes the InputStream. DO NOT surround
     * the call in an auto-close block.
     *
     * @param parser       parser to use
     * @param logger       logger to use
     * @param path         file path
     * @param inputStream  inputStream (which is closed by this call!)
     * @param handler      handler to use
     * @param metadata     metadata
     * @param parseContext parse context
     * @throws IOException wrapper for all exceptions
     */
    public static void parse(Parser parser, Logger logger, String path, InputStream inputStream,
                             ContentHandler handler, Metadata metadata, ParseContext parseContext)
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
            if (! WriteLimitReachedException.isWriteLimitReached(e)) {
                logger.warn("{}: Text extraction failed ({})", path, fileName, e);
            }
            throw new TikaServerParseException(e);
        } catch (OutOfMemoryError e) {
            logger.warn("{}: OOM ({})", path, fileName, e);
            SERVER_STATUS.setStatus(ServerStatus.STATUS.ERROR);
            throw e;
        } finally {
            SERVER_STATUS.complete(taskId);
            inputStream.close();
        }
    }

    protected static long getTaskTimeout(ParseContext parseContext) {

        TikaTaskTimeout tikaTaskTimeout = parseContext.get(TikaTaskTimeout.class);
        long timeoutMillis = TIKA_SERVER_CONFIG.getTaskTimeoutMillis();

        if (tikaTaskTimeout != null) {
            if (tikaTaskTimeout.getTimeoutMillis() > TIKA_SERVER_CONFIG.getTaskTimeoutMillis()) {
                throw new IllegalArgumentException("Can't request a timeout ( " +
                        tikaTaskTimeout.getTimeoutMillis() +
                        "ms) greater than the taskTimeoutMillis set in the server config (" +
                        TIKA_SERVER_CONFIG.getTaskTimeoutMillis() + "ms)");
            }
            timeoutMillis = tikaTaskTimeout.getTimeoutMillis();
            if (timeoutMillis < TIKA_SERVER_CONFIG.getMinimumTimeoutMillis()) {
                throw new WebApplicationException(
                        new IllegalArgumentException("taskTimeoutMillis must be > " +
                        "minimumTimeoutMillis, currently set to (" + TIKA_SERVER_CONFIG.getMinimumTimeoutMillis() +
                        "ms)"), Response.Status.BAD_REQUEST);
            }
        }
        return timeoutMillis;
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
            logger.info("{} ({})", endpoint,
                    metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE));
        }
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
    public StreamingOutput getTextFromMultipart(Attachment att,
                                                @Context HttpHeaders httpHeaders,
                                                @Context final UriInfo info) {
        return produceText(att.getObject(InputStream.class), new Metadata(),
                preparePostHeaderMap(att, httpHeaders), info);
    }

    //this is equivalent to text-main in tika-app
    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    @Path("main")
    public StreamingOutput getTextMain(final InputStream is, @Context HttpHeaders httpHeaders,
                                       @Context final UriInfo info) {
        return produceTextMain(is, httpHeaders.getRequestHeaders(), info);
    }

    //this is equivalent to text-main (Boilerpipe handler) in tika-app
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain")
    @Path("form/main")
    public StreamingOutput getTextMainFromMultipart(final Attachment att,
                                                    @Context HttpHeaders httpHeaders,
                                                    @Context final UriInfo info) {
        return produceTextMain(att.getObject(InputStream.class),
                preparePostHeaderMap(att, httpHeaders), info);
    }

    public StreamingOutput produceTextMain(final InputStream is,
                                           MultivaluedMap<String, String> httpHeaders,
                                           final UriInfo info) {
        final Parser parser = createParser();
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);
        fillParseContext(httpHeaders, metadata, context);

        logRequest(LOG, "/tika", metadata);

        return outputStream -> {
            Writer writer = new OutputStreamWriter(outputStream, UTF_8);

            ContentHandler handler = new BoilerpipeContentHandler(writer);

            parse(parser, LOG, info.getPath(), is, handler, metadata, context);
        };
    }


    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    public StreamingOutput getText(final InputStream is, @Context HttpHeaders httpHeaders,
                                   @Context final UriInfo info) {
        final Metadata metadata = new Metadata();
        return produceText(getInputStream(is, metadata, httpHeaders, info), metadata,
                httpHeaders.getRequestHeaders(), info);
    }

    public StreamingOutput produceText(final InputStream is, final Metadata metadata,
                                       MultivaluedMap<String, String> httpHeaders,
                                       final UriInfo info) {
        final Parser parser = createParser();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);
        fillParseContext(httpHeaders, metadata, context);

        logRequest(LOG, "/tika", metadata);

        return outputStream -> {
            Writer writer = new OutputStreamWriter(outputStream, UTF_8);

            BodyContentHandler body =
                    new BodyContentHandler(new RichTextContentHandler(writer));

            parse(parser, LOG, info.getPath(), is, body, metadata, context);
        };
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/html")
    @Path("form")
    public StreamingOutput getHTMLFromMultipart(Attachment att,
                                                @Context HttpHeaders httpHeaders,
                                                @Context final UriInfo info) {
        return produceOutput(att.getObject(InputStream.class), new Metadata(),
                preparePostHeaderMap(att, httpHeaders), info, "html");
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/html")
    public StreamingOutput getHTML(final InputStream is, @Context HttpHeaders httpHeaders,
                                   @Context final UriInfo info) {
        Metadata metadata = new Metadata();
        return produceOutput(getInputStream(is, metadata, httpHeaders, info), metadata,
                httpHeaders.getRequestHeaders(), info, "html");
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/xml")
    @Path("form")
    public StreamingOutput getXMLFromMultipart(Attachment att,
                                               @Context HttpHeaders httpHeaders,
                                               @Context final UriInfo info) {
        return produceOutput(att.getObject(InputStream.class),
                new Metadata(), preparePostHeaderMap(att, httpHeaders), info, "xml");
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/xml")
    public StreamingOutput getXML(final InputStream is, @Context HttpHeaders httpHeaders,
                                  @Context final UriInfo info) {
        Metadata metadata = new Metadata();
        return produceOutput(getInputStream(is, metadata, httpHeaders, info), metadata,
                httpHeaders.getRequestHeaders(), info, "xml");
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("application/json")
    @Path("form{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Metadata getJsonFromMultipart(Attachment att,
                                                @Context HttpHeaders httpHeaders,
                                                @Context final UriInfo info,
                                                @PathParam(HANDLER_TYPE_PARAM)
                                                     String handlerTypeName)
            throws IOException, TikaException {
        Metadata metadata = new Metadata();
        parseToMetadata(getInputStream(
                att.getObject(InputStream.class), metadata, httpHeaders, info),
                metadata, preparePostHeaderMap(att, httpHeaders), info, handlerTypeName);
        TikaResource.getConfig().getMetadataFilter().filter(metadata);
        return metadata;
    }

    @PUT
    @Consumes("*/*")
    @Produces("application/json")
    @Path("{" + HANDLER_TYPE_PARAM + " : (\\w+)?}")
    public Metadata getJson(final InputStream is, @Context
            HttpHeaders httpHeaders,
                                   @Context final UriInfo info, @PathParam(HANDLER_TYPE_PARAM)
                                               String handlerTypeName)
            throws IOException, TikaException {
        Metadata metadata = new Metadata();
        parseToMetadata(getInputStream(is, metadata, httpHeaders, info), metadata,
                httpHeaders.getRequestHeaders(), info, handlerTypeName);
        TikaResource.getConfig().getMetadataFilter().filter(metadata);
        return metadata;
    }

    private void parseToMetadata(InputStream inputStream,
                                 Metadata metadata,
                                 MultivaluedMap<String, String> httpHeaders,
                                 UriInfo info, String handlerTypeName) throws IOException {
        final Parser parser = createParser();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);
        fillParseContext(httpHeaders, metadata, context);

        logRequest(LOG, "/tika", metadata);
        int writeLimit = -1;
        boolean throwOnWriteLimitReached = getThrowOnWriteLimitReached(httpHeaders);
        if (httpHeaders.containsKey("writeLimit")) {
            writeLimit = Integer.parseInt(httpHeaders.getFirst("writeLimit"));
        }

        BasicContentHandlerFactory.HANDLER_TYPE type =
                BasicContentHandlerFactory.parseHandlerType(handlerTypeName, DEFAULT_HANDLER_TYPE);
        BasicContentHandlerFactory fact = new BasicContentHandlerFactory(type, writeLimit,
                throwOnWriteLimitReached, context);
        ContentHandler contentHandler = fact.getNewContentHandler();

        try {
            parse(parser, LOG, info.getPath(), inputStream, contentHandler, metadata, context);
        } catch (TikaServerParseException e) {
            Throwable cause = e.getCause();
            boolean writeLimitReached = false;
            if (WriteLimitReachedException.isWriteLimitReached(cause)) {
                metadata.set(TikaCoreProperties.WRITE_LIMIT_REACHED, "true");
                writeLimitReached = true;
            }
            if (TIKA_SERVER_CONFIG.isReturnStackTrace()) {
                if (cause != null) {
                    metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION,
                            ExceptionUtils.getStackTrace(cause));
                } else {
                    metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION,
                            ExceptionUtils.getStackTrace(e));
                }
            } else if (! writeLimitReached) {
                throw e;
            }
        } catch (OutOfMemoryError e) {
            if (TIKA_SERVER_CONFIG.isReturnStackTrace()) {
                metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION,
                        ExceptionUtils.getStackTrace(e));
            } else {
                throw e;
            }
        } finally {
            metadata.add(TikaCoreProperties.TIKA_CONTENT, contentHandler.toString());
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
        return HandlerConfig.DEFAULT_HANDLER_CONFIG.isThrowOnWriteLimitReached();
    }

    private StreamingOutput produceOutput(final InputStream is, Metadata metadata,
                                          final MultivaluedMap<String, String> httpHeaders,
                                          final UriInfo info, final String format) {
        final Parser parser = createParser();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, httpHeaders);
        fillParseContext(httpHeaders, metadata, context);


        logRequest(LOG, "/tika", metadata);

        return outputStream -> {
            Writer writer = new OutputStreamWriter(outputStream, UTF_8);
            ContentHandler content;

            try {
                SAXTransformerFactory factory =
                        (SAXTransformerFactory) SAXTransformerFactory.newInstance();
                TransformerHandler handler = factory.newTransformerHandler();
                handler.getTransformer().setOutputProperty(OutputKeys.METHOD, format);
                handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
                handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, UTF_8.name());
                handler.getTransformer().setOutputProperty(OutputKeys.VERSION, "1.1");
                handler.setResult(new StreamResult(writer));
                content = new ExpandedTitleContentHandler(handler);
            } catch (TransformerConfigurationException e) {
                throw new WebApplicationException(e);
            }

            parse(parser, LOG, info.getPath(), is, content, metadata, context);
        };
    }

    /**
     * Prepares a multivalued map, combining attachment headers and request headers.
     * Gives priority to attachment headers.
     * @param att the attachment.
     * @param httpHeaders the http headers, fetched from context.
     * @return the case insensitive MetadataMap containing combined headers.
     */
    private MetadataMap<String, String> preparePostHeaderMap(Attachment att,
                                                             HttpHeaders httpHeaders) {
        if (att == null && httpHeaders == null) return null;
        MetadataMap<String, String> finalHeaders = new MetadataMap<>(false,
                true);
        if (httpHeaders != null && httpHeaders.getRequestHeaders() != null) {
            finalHeaders.putAll(httpHeaders.getRequestHeaders());
        }
        if (att != null && att.getHeaders() != null) {
            finalHeaders.putAll(att.getHeaders());
        }
        return finalHeaders;
    }
}
