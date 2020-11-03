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

import static java.nio.charset.StandardCharsets.UTF_8;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.IOUtils;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.RichTextContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.server.api.UnpackResourceApi;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class UnpackResourceApiServiceImpl implements UnpackResourceApi {
    private static final long MAX_ATTACHMENT_BYTES = 100*1024*1024;

    public static final String TEXT_FILENAME = "__TEXT__";
    private static final String META_FILENAME = "__METADATA__";

    private static final Logger LOG = LoggerFactory.getLogger(UnpackResourceApi.class);

    public static void metadataToCsv(Metadata metadata, OutputStream outputStream) throws IOException {
        CSVWriter writer = new CSVWriter(new OutputStreamWriter(outputStream, UTF_8));

        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            ArrayList<String> list = new ArrayList<>(values.length + 1);
            list.add(name);
            list.addAll(Arrays.asList(values));
            writer.writeNext(list.toArray(values));
        }

        writer.close();
    }

    @Path("/{id:(/.*)?}")
    @PUT
    @Produces({"application/zip", "application/x-tar"})
    public Map<String, byte[]> unpack(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception {
        return process(TikaResourceApiServiceImpl.getInputStream(is, new Metadata(), httpHeaders), httpHeaders, info, false);
    }

    @Path("/all{id:(/.*)?}")
    @PUT
    @Produces({"application/zip", "application/x-tar"})
    public Map<String, byte[]> unpackAll(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception {
        return process(TikaResourceApiServiceImpl.getInputStream(is, new Metadata(), httpHeaders), httpHeaders, info, true);
    }

    private Map<String, byte[]> process(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info,
            boolean saveAll
    ) throws Exception {
        Metadata metadata = new Metadata();
        ParseContext pc = new ParseContext();

        Parser parser = TikaResourceApiServiceImpl.createParser();
        if (parser instanceof DigestingParser) {
            //no need to digest for unwrapping
            parser = ((DigestingParser)parser).getWrappedParser();
        }
        TikaResourceApiServiceImpl.fillParseContext(pc, httpHeaders.getRequestHeaders(), null);
        TikaResourceApiServiceImpl.fillMetadata(parser, metadata, pc, httpHeaders.getRequestHeaders());
        TikaResourceApiServiceImpl.logRequest(LOG, info, metadata);
        //even though we aren't currently parsing embedded documents,
        //we need to add this to allow for "inline" use of other parsers.
        pc.set(Parser.class, parser);
        ContentHandler ch;
        ByteArrayOutputStream text = new ByteArrayOutputStream();

        if (saveAll) {
            ch = new BodyContentHandler(new RichTextContentHandler(new OutputStreamWriter(text, UTF_8)));
        } else {
            ch = new DefaultHandler();
        }

        Map<String, byte[]> files = new HashMap<>();
        MutableInt count = new MutableInt();

        pc.set(EmbeddedDocumentExtractor.class, new MyEmbeddedDocumentExtractor(count, files));
        TikaResourceApiServiceImpl.parse(parser, LOG, info.getPath(), is, ch, metadata, pc);

        if (count.intValue() == 0 && !saveAll) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }

        if (saveAll) {
            files.put(TEXT_FILENAME, text.toByteArray());

            ByteArrayOutputStream metaStream = new ByteArrayOutputStream();
            metadataToCsv(metadata, metaStream);

            files.put(META_FILENAME, metaStream.toByteArray());
        }

        return files;
    }

    private class MyEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        private final MutableInt count;
        private final Map<String, byte[]> zout;

        MyEmbeddedDocumentExtractor(MutableInt count, Map<String, byte[]> zout) {
            this.count = count;
            this.zout = zout;
        }

        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b)
                throws SAXException, IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BoundedInputStream bis = new BoundedInputStream(MAX_ATTACHMENT_BYTES, inputStream);
            IOUtils.copy(bis, bos);
            if (bis.hasHitBound()) {
                throw new IOExceptionWithCause(
                        new TikaMemoryLimitException(MAX_ATTACHMENT_BYTES+1, MAX_ATTACHMENT_BYTES));
            }
            byte[] data = bos.toByteArray();

            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            String contentType = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);

            if (name == null) {
                name = Integer.toString(count.intValue());
            }

            if (!name.contains(".") && contentType != null) {
                try {
                    String ext = TikaResourceApiServiceImpl.getConfig().getMimeRepository().forName(contentType).getExtension();

                    if (ext != null) {
                        name += ext;
                    }
                } catch (MimeTypeException e) {
                    LOG.warn("Unexpected MimeTypeException", e);
                }
            }

            if ("application/vnd.openxmlformats-officedocument.oleObject".equals(contentType)) {
                POIFSFileSystem poifs = new POIFSFileSystem(new ByteArrayInputStream(data));
                OfficeParser.POIFSDocumentType type = OfficeParser.POIFSDocumentType.detectType(poifs);

                if (type == OfficeParser.POIFSDocumentType.OLE10_NATIVE) {
                    try {
                        Ole10Native ole = Ole10Native.createFromEmbeddedOleObject(poifs);
                        if (ole.getDataSize() > 0) {
                            String label = ole.getLabel();

                            if (label.startsWith("ole-")) {
                                label = Integer.toString(count.intValue()) + '-' + label;
                            }

                            name = label;

                            data = ole.getDataBuffer();
                        }
                    } catch (Ole10NativeException ex) {
                        LOG.warn("Skipping invalid part", ex);
                    }
                } else {
                    name += '.' + type.getExtension();
                }
            }

            final String finalName = getFinalName(name, zout);

            if (data.length > 0) {
                zout.put(finalName, data);

                count.increment();
            } else {
                if (inputStream instanceof TikaInputStream) {
                    TikaInputStream tin = (TikaInputStream) inputStream;

                    if (tin.getOpenContainer() != null && tin.getOpenContainer() instanceof DirectoryEntry) {
                        POIFSFileSystem fs = new POIFSFileSystem();
                        copy((DirectoryEntry) tin.getOpenContainer(), fs.getRoot());
                        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
                        fs.writeFilesystem(bos2);
                        bos2.close();

                        zout.put(finalName, bos2.toByteArray());
                    }
                }
            }
        }

        private String getFinalName(String name, Map<String, byte[]> zout) {
            name = name.replaceAll("\u0000", " ");
            String normalizedName = FilenameUtils.normalize(name);

            if (normalizedName == null) {
                normalizedName = FilenameUtils.getName(name);
            }

            if (normalizedName == null) {
                normalizedName = count.toString();
            }
            //strip off initial C:/ or ~/ or /
            int prefixLength = FilenameUtils.getPrefixLength(normalizedName);
            if (prefixLength > -1) {
                normalizedName = normalizedName.substring(prefixLength);
            }
            if (zout.containsKey(normalizedName)) {
                return UUID.randomUUID().toString()+"-"+normalizedName;
            }
            return normalizedName;
        }

        protected void copy(DirectoryEntry sourceDir, DirectoryEntry destDir)
                throws IOException {
            for (Entry entry : sourceDir) {
                if (entry instanceof DirectoryEntry) {
                    // Need to recurse
                    DirectoryEntry newDir = destDir.createDirectory(entry.getName());
                    copy((DirectoryEntry) entry, newDir);
                } else {
                    // Copy entry
                    try (InputStream contents = new DocumentInputStream((DocumentEntry) entry)) {
                        destDir.createDocument(entry.getName(), contents);
                    }
                }
            }
        }
    }

    /**
     * PUT an embedded document and unpack it to get back the raw bytes of embedded files.
     *
     * PUT an embedded document and unpack it to get back the raw bytes of embedded files. Default return type is ZIP &lt;b&gt;NOTE&lt;/b&gt;: this does not operate recursively
     *
     */
    public String putUnpack() {
        // TODO: Implement...
        
        return null;
    }
    
}

