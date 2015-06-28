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

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.IOUtils;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.server.RichTextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Path("/unpack")
public class UnpackerResource {
    public static final String TEXT_FILENAME = "__TEXT__";
    private static final Log logger = LogFactory.getLog(UnpackerResource.class);
    private static final String META_FILENAME = "__METADATA__";

    public static void metadataToCsv(Metadata metadata, OutputStream outputStream) throws IOException {
        CSVWriter writer = new CSVWriter(new OutputStreamWriter(outputStream, org.apache.tika.io.IOUtils.UTF_8));

        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            ArrayList<String> list = new ArrayList<String>(values.length + 1);
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
        return process(is, httpHeaders, info, false);
    }

    @Path("/all{id:(/.*)?}")
    @PUT
    @Produces({"application/zip", "application/x-tar"})
    public Map<String, byte[]> unpackAll(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info
    ) throws Exception {
        return process(is, httpHeaders, info, true);
    }

    private Map<String, byte[]> process(
            InputStream is,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo info,
            boolean saveAll
    ) throws Exception {
        Metadata metadata = new Metadata();
        ParseContext pc = new ParseContext();

        Parser parser = TikaResource.createParser();
        if (parser instanceof DigestingParser) {
            //no need to digest for unwrapping
            parser = ((DigestingParser)parser).getWrappedParser();
        }

        TikaResource.fillMetadata(parser, metadata, pc, httpHeaders.getRequestHeaders());
        TikaResource.logRequest(logger, info, metadata);

        ContentHandler ch;
        ByteArrayOutputStream text = new ByteArrayOutputStream();

        if (saveAll) {
            ch = new BodyContentHandler(new RichTextContentHandler(new OutputStreamWriter(text, org.apache.tika.io.IOUtils.UTF_8)));
        } else {
            ch = new DefaultHandler();
        }

        Map<String, byte[]> files = new HashMap<String, byte[]>();
        MutableInt count = new MutableInt();

        pc.set(EmbeddedDocumentExtractor.class, new MyEmbeddedDocumentExtractor(count, files));
        is = TikaUtils.getInputSteam(is, httpHeaders);
        TikaResource.parse(parser, logger, info.getPath(), is, ch, metadata, pc);

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

        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b) throws SAXException, IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(inputStream, bos);
            byte[] data = bos.toByteArray();

            String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
            String contentType = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);

            if (name == null) {
                name = Integer.toString(count.intValue());
            }

            if (!name.contains(".") && contentType != null) {
                try {
                    String ext = TikaResource.getConfig().getMimeRepository().forName(contentType).getExtension();

                    if (ext != null) {
                        name += ext;
                    }
                } catch (MimeTypeException e) {
                    logger.warn("Unexpected MimeTypeException", e);
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
                        logger.warn("Skipping invalid part", ex);
                    }
                } else {
                    name += '.' + type.getExtension();
                }
            }

            final String finalName = name;

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

        protected void copy(DirectoryEntry sourceDir, DirectoryEntry destDir)
                throws IOException {
            for (Entry entry : sourceDir) {
                if (entry instanceof DirectoryEntry) {
                    // Need to recurse
                    DirectoryEntry newDir = destDir.createDirectory(entry.getName());
                    copy((DirectoryEntry) entry, newDir);
                } else {
                    // Copy entry
                    InputStream contents = new DocumentInputStream((DocumentEntry) entry);
                    try {
                        destDir.createDocument(entry.getName(), contents);
                    } finally {
                        contents.close();
                    }
                }
            }
        }
    }
}
