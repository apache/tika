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

import javax.mail.internet.ContentDisposition;
import javax.mail.internet.ParseException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ExpandedTitleContentHandler;
import org.apache.tika.server.RichTextContentHandler;
import org.apache.tika.server.TikaServerParseException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

@Path("/tika")
public class TikaResource {
    public static final String GREETING = "This is Tika Server. Please PUT\n";
    public static final String X_TIKA_OCR_HEADER_PREFIX = "X-Tika-OCR";
    public static final String X_TIKA_PDF_HEADER_PREFIX = "X-Tika-PDF";


    private static final Log logger = LogFactory.getLog(TikaResource.class);

    private static TikaConfig tikaConfig;
    private static DigestingParser.Digester digester = null;

    public static void init(TikaConfig config, DigestingParser.Digester digestr) {
        tikaConfig = config;
        digester = digestr;
    }

    static {
        ExtractorFactory.setAllThreadsPreferEventExtractors(true);
    }

    @SuppressWarnings("serial")
    public static Parser createParser() {
        final Parser parser = new AutoDetectParser(tikaConfig);

        Map<MediaType, Parser> parsers = ((AutoDetectParser)parser).getParsers();
        parsers.put(MediaType.APPLICATION_XML, new HtmlParser());
        ((AutoDetectParser)parser).setParsers(parsers);

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
            try {
                ContentDisposition c = new ContentDisposition(disposition);

                // only support "attachment" dispositions
                if ("attachment".equals(c.getDisposition())) {
                    String fn = c.getParameter("filename");
                    if (fn != null) {
                        return fn;
                    }
                }
            } catch (ParseException e) {
                // not a valid content-disposition field
            	e.printStackTrace();
            	logger.warn(String.format(
                        Locale.ROOT,
                        "Parse exception %s determining content disposition",
                        e.getMessage()
                ), e);
            }
        }

        // this really should not be used, since it's not an official field
        return httpHeaders.getFirst("File-Name");
    }

    public static void fillParseContext(ParseContext parseContext, MultivaluedMap<String, String> httpHeaders,
                                        Parser embeddedParser) {
        TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        for (String key : httpHeaders.keySet()) {
            if (StringUtils.startsWith(key, X_TIKA_OCR_HEADER_PREFIX)) {
                processHeaderConfig(httpHeaders, ocrConfig, key, X_TIKA_OCR_HEADER_PREFIX);
            } else if (StringUtils.startsWith(key, X_TIKA_PDF_HEADER_PREFIX)) {
                processHeaderConfig(httpHeaders, pdfParserConfig, key, X_TIKA_PDF_HEADER_PREFIX);
            }
        }
        parseContext.set(TesseractOCRConfig.class, ocrConfig);
        parseContext.set(PDFParserConfig.class, pdfParserConfig);
        if (embeddedParser != null) {
            parseContext.set(Parser.class, embeddedParser);
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
        try {
            String property = StringUtils.removeStart(key, prefix);
            Field field = object.getClass().getDeclaredField(StringUtils.uncapitalize(property));
            field.setAccessible(true);
            if (field.getType() == String.class) {
                field.set(object, httpHeaders.getFirst(key));
            } else if (field.getType() == int.class) {
                field.setInt(object, Integer.parseInt(httpHeaders.getFirst(key)));
            } else if (field.getType() == double.class) {
                field.setDouble(object, Double.parseDouble(httpHeaders.getFirst(key)));
            } else if (field.getType() == boolean.class) {
                field.setBoolean(object, Boolean.parseBoolean(httpHeaders.getFirst(key)));
            }
        } catch (Throwable ex) {
            throw new WebApplicationException(String.format(Locale.ROOT,
                    "%s is an invalid %s header", key, X_TIKA_OCR_HEADER_PREFIX));
        }
    }

    @SuppressWarnings("serial")
    public static void fillMetadata(Parser parser, Metadata metadata, ParseContext context, MultivaluedMap<String, String> httpHeaders) {
        String fileName = detectFilename(httpHeaders);
        if (fileName != null) {
            metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, fileName);
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

                    if (ct != null) {
                        return MediaType.parse(ct);
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

    public static void parse(Parser parser, Log logger, String path, InputStream inputStream,
                             ContentHandler handler, Metadata metadata, ParseContext parseContext) throws IOException {
        inputStream = TikaInputStream.get(inputStream);
        try {
            parser.parse(inputStream, handler, metadata, parseContext);
        } catch (SAXException e) {
            throw new TikaServerParseException(e);
        } catch (EncryptedDocumentException e) {
            logger.warn(String.format(
                    Locale.ROOT,
                    "%s: Encrypted document",
                    path
            ), e);
            throw new TikaServerParseException(e);
        } catch (Exception e) {
            logger.warn(String.format(
                    Locale.ROOT,
                    "%s: Text extraction failed",
                    path
            ), e);
            throw new TikaServerParseException(e);
        } finally {
            inputStream.close();
        }
    }

    public static void logRequest(Log logger, UriInfo info, Metadata metadata) {
        if (metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE) == null) {
            logger.info(String.format(
                    Locale.ROOT,
                    "%s (autodetecting type)",
                    info.getPath()
            ));
        } else {
            logger.info(String.format(
                    Locale.ROOT,
                    "%s (%s)",
                    info.getPath(),
                    metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE)
            ));
        }
    }

    @GET
    @Produces("text/plain")
    public String getMessage() {
        return GREETING;
    }

    @POST
    @Consumes("multipart/form-data")
    @Produces("text/plain")
    @Path("form")
    public StreamingOutput getTextFromMultipart(Attachment att, @Context final UriInfo info) {
        return produceText(att.getObject(InputStream.class), att.getHeaders(), info);
    }

    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    public StreamingOutput getText(final InputStream is, @Context HttpHeaders httpHeaders, @Context final UriInfo info) {
        return produceText(is, httpHeaders.getRequestHeaders(), info);
    }

    public StreamingOutput produceText(final InputStream is, MultivaluedMap<String, String> httpHeaders, final UriInfo info) {
        final Parser parser = createParser();
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, context, httpHeaders);
        fillParseContext(context, httpHeaders, parser);

        logRequest(logger, info, metadata);

        return new StreamingOutput() {
            public void write(OutputStream outputStream) throws IOException, WebApplicationException {
                Writer writer = new OutputStreamWriter(outputStream, IOUtils.UTF_8);

                BodyContentHandler body = new BodyContentHandler(new RichTextContentHandler(writer));

                try {
                    parse(parser, logger, info.getPath(), is, body, metadata, context);
                } finally {
                    is.close();
                }
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
        return produceOutput(is, httpHeaders.getRequestHeaders(), info, "html");
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
        return produceOutput(is, httpHeaders.getRequestHeaders(), info, "xml");
    }

    private StreamingOutput produceOutput(final InputStream is, final MultivaluedMap<String, String> httpHeaders,
                                          final UriInfo info, final String format) {
        final Parser parser = createParser();
        final Metadata metadata = new Metadata();
        final ParseContext context = new ParseContext();

        fillMetadata(parser, metadata, context, httpHeaders);
        fillParseContext(context, httpHeaders, parser);


        logRequest(logger, info, metadata);

        return new StreamingOutput() {
            public void write(OutputStream outputStream)
                    throws IOException, WebApplicationException {
                Writer writer = new OutputStreamWriter(outputStream, IOUtils.UTF_8);
                ContentHandler content;

                try {
                    SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
                    TransformerHandler handler = factory.newTransformerHandler();
                    handler.getTransformer().setOutputProperty(OutputKeys.METHOD, format);
                    handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
                    handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, IOUtils.UTF_8.name());
                    handler.setResult(new StreamResult(writer));
                    content = new ExpandedTitleContentHandler(handler);
                } catch (TransformerConfigurationException e) {
                    throw new WebApplicationException(e);
                }

                parse(parser, logger, info.getPath(), is, content, metadata, context);
            }
        };
    }
}
