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

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.attachment.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.poi.ooxml.extractor.ExtractorFactory;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.html.BoilerpipeContentHandler;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.apache.tika.sax.RichTextContentHandler;
import org.apache.tika.server.InputStreamFactory;
import org.apache.tika.server.ServerStatus;
import org.apache.tika.server.TikaServerParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

@Path("/tika")
public class TikaResource {

    private static Pattern ALLOWABLE_HEADER_CHARS = Pattern.compile("(?i)^[-/_+\\.A-Z0-9 ]+$");

    public static final String GREETING = "This is Tika Server (" + new Tika().toString() + "). Please PUT\n";
    public static final String X_TIKA_OCR_HEADER_PREFIX = "X-Tika-OCR";
    public static final String X_TIKA_PDF_HEADER_PREFIX = "X-Tika-PDF";

    private static final Logger LOG = LoggerFactory.getLogger(TikaResource.class);

    private static TikaConfig tikaConfig;
    private static DigestingParser.Digester digester = null;
    private static InputStreamFactory inputStreamFactory = null;
    private static ServerStatus SERVER_STATUS = null;
    public static void init(TikaConfig config, DigestingParser.Digester digestr,
                            InputStreamFactory iSF, ServerStatus serverStatus) {
        tikaConfig = config;
        digester = digestr;
        inputStreamFactory = iSF;
        SERVER_STATUS = serverStatus;
    }

    static {
        ExtractorFactory.setAllThreadsPreferEventExtractors(true);
    }

    @SuppressWarnings("serial")
    public static Parser createParser() {
        final Parser parser = new AutoDetectParser(tikaConfig);

        ((AutoDetectParser)parser).setFallback(new Parser() {
            public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
                return parser.getSupportedTypes(parseContext);
            }

            public void parse(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, ParseContext parseContext) {
                throw new WebApplicationException(Response.Status.UNSUPPORTED_MEDIA_TYPE);
            }
        });
        if (digester != null) {
            return new DigestingParser(parser, digester);
        }
        return parser;
    }

    public static TikaConfig getConfig() {
        return tikaConfig;
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

    public static void fillParseContext(ParseContext parseContext, MultivaluedMap<String, String> httpHeaders,
                                        Parser embeddedParser) {
        //lazily initialize configs
        //if a header is submitted, any params set in --tika-config tika-config.xml
        //upon server startup will be ignored.
        TesseractOCRConfig ocrConfig = null;
        PDFParserConfig pdfParserConfig = null;
        for (String key : httpHeaders.keySet()) {
            if (StringUtils.startsWith(key, X_TIKA_OCR_HEADER_PREFIX)) {
                ocrConfig = (ocrConfig == null) ? new TesseractOCRConfig() : ocrConfig;
                processHeaderConfig(httpHeaders, ocrConfig, key, X_TIKA_OCR_HEADER_PREFIX);
            } else if (StringUtils.startsWith(key, X_TIKA_PDF_HEADER_PREFIX)) {
                pdfParserConfig = (pdfParserConfig == null) ? new PDFParserConfig() : pdfParserConfig;
                processHeaderConfig(httpHeaders, pdfParserConfig, key, X_TIKA_PDF_HEADER_PREFIX);
            }
        }
        if (ocrConfig != null) {
            parseContext.set(TesseractOCRConfig.class, ocrConfig);
        }
        if (pdfParserConfig != null) {
            parseContext.set(PDFParserConfig.class, pdfParserConfig);
        }
        if (embeddedParser != null) {
            parseContext.set(Parser.class, embeddedParser);
        }
    }

    public static InputStream getInputStream(InputStream is, HttpHeaders headers) {
        try {
            return inputStreamFactory.getInputSteam(is, headers);
        } catch (IOException e) {
            throw new TikaServerParseException(e);
        }
    }

    /**
     * Utility method to set a property on a class via reflection.
     *
     * @param httpHeaders the HTTP headers set.
     * @param object      the <code>Object</code> to set the property on.
     * @param key         the key of the HTTP Header.
     * @param prefix      the name of the HTTP Header prefix used to find property.
     * @throws WebApplicationException thrown when field cannot be found.
     */
    private static void processHeaderConfig(MultivaluedMap<String, String> httpHeaders, Object object, String key, String prefix) {

        try {String property = StringUtils.removeStart(key, prefix);
            Field field = null;
            try {
                field = object.getClass().getDeclaredField(StringUtils.uncapitalize(property));
            } catch (NoSuchFieldException e) {
                //swallow
            }
            String setter = property;
            setter = "set"+setter.substring(0,1).toUpperCase(Locale.US)+setter.substring(1);
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
                String val = httpHeaders.getFirst(key);
                val = val.trim();
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
                    throw new IllegalArgumentException("setter must be String, int, float, double or boolean...for now");
                }
            } else {
                throw new NoSuchMethodException("Couldn't find: "+setter);
            }

        } catch (Throwable ex) {
            throw new WebApplicationException(String.format(Locale.ROOT,
                    "%s is an invalid %s header", key, X_TIKA_OCR_HEADER_PREFIX));
        }
    }

    private static void checkTrustWorthy(String setter, String val) {
        if (setter == null || val == null) {
            throw new IllegalArgumentException("setter and val must not be null");
        }
        if (setter.toLowerCase(Locale.US).contains("trusted")) {
            throw new IllegalArgumentException("Can't call a trusted method via tika-server headers");
        }
        Matcher m = ALLOWABLE_HEADER_CHARS.matcher(val);
        if (! m.find()) {
            throw new IllegalArgumentException("Header val: "+val +" contains illegal characters. " +
                    "Must contain: TikaResource.ALLOWABLE_HEADER_CHARS");
        }
    }

    /**
     * Tries to get method. Silently swallows NoMethodException and returns
     * <code>null</code> if not found.
     * @param object
     * @param method
     * @param clazz
     * @return
     */
    private static Method tryToGetMethod(Object object, String method, Class clazz) {
        try {
            return object.getClass().getMethod(method, clazz);
        } catch (NoSuchMethodException e) {
            //swallow
        }
        return null;
    }

    @SuppressWarnings("serial")
    public static void fillMetadata(Parser parser, Metadata metadata, ParseContext context, MultivaluedMap<String, String> httpHeaders) {
        String fileName = detectFilename(httpHeaders);
        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }

        String contentTypeHeader = httpHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
        javax.ws.rs.core.MediaType mediaType = contentTypeHeader == null ? null
                : javax.ws.rs.core.MediaType.valueOf(contentTypeHeader);
        if (mediaType != null && "xml".equals(mediaType.getSubtype())) {
            mediaType = null;
        }

        if (mediaType != null && mediaType.equals(javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE)) {
            mediaType = null;
        }

        if (mediaType != null) {
            metadata.add(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE, mediaType.toString());

            final Detector detector = getDetector(parser);

            setDetector(parser, new Detector() {
                public MediaType detect(InputStream inputStream, Metadata metadata) throws IOException {
                    String ct = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);
                    //make sure never to return null -- TIKA-1845
                    MediaType type = null;
                    if (ct != null) {
                        //this can return null if ct is not a valid mime type
                        type = MediaType.parse(ct);
                    }
                    if (type != null) {
                        return type;
                    } else {
                        return detector.detect(inputStream, metadata);
                    }
                }
            });
        }

        final String password = httpHeaders.getFirst("Password");
        if (password != null) {
            context.set(PasswordProvider.class, new PasswordProvider() {
                @Override
                public String getPassword(Metadata metadata) {
                    return password;
                }
            });
        }
    }

    public static void setDetector(Parser p, Detector detector) {
        AutoDetectParser adp = getAutoDetectParser(p);
        adp.setDetector(detector);
    }

    public static Detector getDetector(Parser p) {
        AutoDetectParser adp = getAutoDetectParser(p);
        return adp.getDetector();
    }

    private static AutoDetectParser getAutoDetectParser(Parser p) {
        //bit stinky
        if (p instanceof AutoDetectParser) {
            return (AutoDetectParser)p;
        } else if (p instanceof ParserDecorator) {
            Parser wrapped = ((ParserDecorator)p).getWrappedParser();
            if (wrapped instanceof AutoDetectParser) {
                return (AutoDetectParser)wrapped;
            }
            throw new RuntimeException("Couldn't find AutoDetectParser within: "+wrapped.getClass());

        }
        throw new RuntimeException("Couldn't find AutoDetectParser within: "+p.getClass());

    }

    /**
     * Use this to call a parser and unify exception handling.
     * NOTE: This call to parse closes the InputStream. DO NOT surround
     * the call in an auto-close block.
     *
     * @param parser parser to use
     * @param logger logger to use
     * @param path file path
     * @param inputStream inputStream (which is closed by this call!)
     * @param handler handler to use
     * @param metadata metadata
     * @param parseContext parse context
     * @throws IOException wrapper for all exceptions
     */
    public static void parse(Parser parser, Logger logger, String path, InputStream inputStream,
                             ContentHandler handler, Metadata metadata, ParseContext parseContext) throws IOException {

        checkIsOperating();
        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        long taskId = SERVER_STATUS.start(ServerStatus.TASK.PARSE,
                fileName);
        try {
            parser.parse(inputStream, handler, metadata, parseContext);
        } catch (SAXException e) {
            throw new TikaServerParseException(e);
        } catch (EncryptedDocumentException e) {
            logger.warn("{}: Encrypted document ({})", path, fileName, e);
            throw new TikaServerParseException(e);
        } catch (Exception e) {
            logger.warn("{}: Text extraction failed ({})", path, fileName, e);
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

    public static void checkIsOperating() {
        //check that server is not in shutdown mode
        if (! SERVER_STATUS.isOperating()) {
            throw new WebApplicationException(Response.Status.SERVICE_UNAVAILABLE);
        }
    }

    public static void logRequest(Logger logger, UriInfo info, Metadata metadata) {
        if (metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE) == null) {
            logger.info("{} (autodetecting type)", info.getPath());
        } else {
            logger.info("{} ({})", info.getPath(), metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE));
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
    public StreamingOutput getTextFromMultipart(Attachment att, @Context final UriInfo info) {
        return produceText(att.getObject(InputStream.class), att.getHeaders(), info);
    }

    //this is equivalent to text-main in tika-app
    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    @Path("main")
    public StreamingOutput getTextMain(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        return produceTextMain(is, httpHeaders.getRequestHeaders(), info);
    }

    //this is equivalent to text-main (Boilerpipe handler) in tika-app
    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain")
    @Path("form/main")
    public StreamingOutput getTextMainFromMultipart(final Attachment att, @Context final UriInfo info) {
        return produceTextMain(att.getObject(InputStream.class), att.getHeaders(), info);
    }

    public StreamingOutput produceTextMain(final InputStream is, MultivaluedMap<String, String> httpHeaders, final UriInfo info) {
        final Parser parser = createParser();
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, context, httpHeaders);
        fillParseContext(context, httpHeaders, parser);

        logRequest(LOG, info, metadata);

        return new StreamingOutput() {
            public void write(OutputStream outputStream) throws IOException, WebApplicationException {
                Writer writer = new OutputStreamWriter(outputStream, UTF_8);

                ContentHandler handler = new BoilerpipeContentHandler(writer);

                parse(parser, LOG, info.getPath(), is, handler, metadata, context);
            }
        };
    }


    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    public StreamingOutput getText(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        return produceText(getInputStream(is, httpHeaders), httpHeaders.getRequestHeaders(), info);
    }

    public StreamingOutput produceText(final InputStream is, MultivaluedMap<String, String> httpHeaders, final UriInfo info) {
        final Parser parser = createParser();
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, context, httpHeaders);
        fillParseContext(context, httpHeaders, parser);

        logRequest(LOG, info, metadata);

        return new StreamingOutput() {
            public void write(OutputStream outputStream) throws IOException, WebApplicationException {
                Writer writer = new OutputStreamWriter(outputStream, UTF_8);

                BodyContentHandler body = new BodyContentHandler(new RichTextContentHandler(writer));

                parse(parser, LOG, info.getPath(), is, body, metadata, context);
            }
        };
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/html")
    @Path("form")
    public StreamingOutput getHTMLFromMultipart(Attachment att, @Context final UriInfo info) {
        return produceOutput(att.getObject(InputStream.class), att.getHeaders(), info, "html");
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/html")
    public StreamingOutput getHTML(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        return produceOutput(getInputStream(is, httpHeaders), httpHeaders.getRequestHeaders(), info, "html");
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/xml")
    @Path("form")
    public StreamingOutput getXMLFromMultipart(Attachment att, @Context final UriInfo info) {
        return produceOutput(att.getObject(InputStream.class), att.getHeaders(), info, "xml");
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/xml")
    public StreamingOutput getXML(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        return produceOutput(getInputStream(is, httpHeaders), httpHeaders.getRequestHeaders(), info, "xml");
    }

    private StreamingOutput produceOutput(final InputStream is, final MultivaluedMap<String, String> httpHeaders,
                                          final UriInfo info, final String format) {
        final Parser parser = createParser();
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, context, httpHeaders);
        fillParseContext(context, httpHeaders, parser);


        logRequest(LOG, info, metadata);

        return new StreamingOutput() {
            public void write(OutputStream outputStream)
                    throws IOException, WebApplicationException {
                Writer writer = new OutputStreamWriter(outputStream, UTF_8);
                ContentHandler content;

                try {
                    SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
                    TransformerHandler handler = factory.newTransformerHandler();
                    handler.getTransformer().setOutputProperty(OutputKeys.METHOD, format);
                    handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
                    handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, UTF_8.name());
                    handler.setResult(new StreamResult(writer));
                    content = new ExpandedTitleContentHandler(handler);
                } catch (TransformerConfigurationException e) {
                    throw new WebApplicationException(e);
                }

                parse(parser, LOG, info.getPath(), is, content, metadata, context);
            }
        };
    }
}
