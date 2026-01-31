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
import static org.apache.tika.server.core.resource.TikaResource.setupMultipartConfig;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * JAX-RS resource for unpacking embedded documents from container files.
 * <p>
 * This endpoint uses process-isolated parsing via tika-pipes with ParseMode.UNPACK.
 * Embedded documents are extracted and returned as a zip archive.
 * <p>
 * <b>Endpoints:</b>
 * <ul>
 *   <li>PUT /unpack - Extract embedded documents (raw body)</li>
 *   <li>POST /unpack - Extract with config (multipart: file + optional JSON config)</li>
 *   <li>PUT /unpack/all - Extract embedded + container text/metadata</li>
 *   <li>POST /unpack/all - Extract all with config (multipart)</li>
 * </ul>
 * <p>
 * <b>Configuration Requirements:</b>
 * <p>
 * Your tika-config.json must include:
 * <pre>
 * {
 *   "fetchers": {
 *     "file-system-fetcher": {
 *       "class": "org.apache.tika.pipes.fetcher.fs.FileSystemFetcher",
 *       "allowAbsolutePaths": true
 *     }
 *   },
 *   "emitters": {
 *     "unpack-emitter": {
 *       "class": "org.apache.tika.pipes.emitter.fs.FileSystemEmitter",
 *       "basePath": "/tmp/tika-unpack",
 *       "onExists": "replace"
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * <b>Multipart Configuration (POST endpoints):</b>
 * <p>
 * Submit as multipart/form-data with:
 * <ul>
 *   <li>"file" part: the document to unpack</li>
 *   <li>"config" part (optional): JSON configuration</li>
 * </ul>
 * <p>
 * Example config JSON:
 * <pre>
 * {
 *   "parse-context": {
 *     "unpack-config": {
 *       "suffixStrategy": "DETECTED",
 *       "includeOriginal": true
 *     },
 *     "unpack-selector": {
 *       "includeMimeTypes": ["image/jpeg", "image/png"],
 *       "excludeMimeTypes": ["application/pdf"]
 *     },
 *     "embedded-limits": {
 *       "maxDepth": 5,
 *       "maxCount": 100
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * <b>Breaking Changes from Pre-4.0:</b>
 * <ul>
 *   <li>Parsing now runs in a separate process for memory safety</li>
 *   <li>Configuration via HTTP headers is no longer supported; use multipart JSON config</li>
 *   <li>Custom EmbeddedDocumentExtractor in ParseContext is ignored; use UnpackSelector</li>
 *   <li>The unpackMaxBytes header is removed; use embedded-limits in config</li>
 * </ul>
 */
@jakarta.ws.rs.Path("/unpack")
public class UnpackerResource {

    private static final Logger LOG = LoggerFactory.getLogger(UnpackerResource.class);

    /**
     * Extracts embedded documents from a container file (simple PUT, no config).
     * Returns a zip archive containing the extracted files.
     *
     * @param is input stream containing the document
     * @param httpHeaders HTTP headers
     * @param info URI info
     * @return streaming zip response
     */
    @jakarta.ws.rs.Path("/{id:(/.*)?}")
    @PUT
    @Produces("application/zip")
    public Response unpack(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        ParseContext pc = TikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(pc);
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            tis.getPath(); // Spool to temp file for pipes-based parsing
            fillMetadata(null, metadata, httpHeaders.getRequestHeaders());
            TikaResource.logRequest(LOG, "/unpack", metadata);
            return doUnpack(tis, metadata, pc, false);
        }
    }

    /**
     * Extracts embedded documents with configuration (multipart POST).
     * Accepts multipart/form-data with "file" and optional "config" parts.
     *
     * @param attachments multipart attachments
     * @param httpHeaders HTTP headers
     * @param info URI info
     * @return streaming zip response
     */
    @jakarta.ws.rs.Path("/{id:(/.*)?}")
    @POST
    @Consumes("multipart/form-data")
    @Produces("application/zip")
    public Response unpackWithConfig(List<Attachment> attachments, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        ParseContext pc = TikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(pc);
        try (TikaInputStream tis = setupMultipartConfig(attachments, metadata, pc)) {
            TikaResource.logRequest(LOG, "/unpack", metadata);
            return doUnpack(tis, metadata, pc, false);
        }
    }

    /**
     * Extracts embedded documents plus original document and metadata (simple PUT).
     * Returns a zip archive containing extracted files, original document, and metadata.
     *
     * @param is input stream containing the document
     * @param httpHeaders HTTP headers
     * @param info URI info
     * @return streaming zip response
     */
    @jakarta.ws.rs.Path("/all{id:(/.*)?}")
    @PUT
    @Produces("application/zip")
    public Response unpackAll(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        ParseContext pc = TikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(pc);
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            tis.getPath(); // Spool to temp file for pipes-based parsing
            fillMetadata(null, metadata, httpHeaders.getRequestHeaders());
            TikaResource.logRequest(LOG, "/unpack/all", metadata);
            return doUnpack(tis, metadata, pc, true);
        }
    }

    /**
     * Extracts embedded documents plus original/metadata with config (multipart POST).
     * Accepts multipart/form-data with "file" and optional "config" parts.
     *
     * @param attachments multipart attachments
     * @param httpHeaders HTTP headers
     * @param info URI info
     * @return streaming zip response
     */
    @jakarta.ws.rs.Path("/all{id:(/.*)?}")
    @POST
    @Consumes("multipart/form-data")
    @Produces("application/zip")
    public Response unpackAllWithConfig(List<Attachment> attachments, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {
        ParseContext pc = TikaResource.createParseContext();
        Metadata metadata = Metadata.newInstance(pc);
        try (TikaInputStream tis = setupMultipartConfig(attachments, metadata, pc)) {
            TikaResource.logRequest(LOG, "/unpack/all", metadata);
            return doUnpack(tis, metadata, pc, true);
        }
    }

    /**
     * Core unpack logic using pipes-based parsing.
     * The child process creates the zip file, and we stream it directly back.
     *
     * @param tis spooled input stream
     * @param metadata document metadata
     * @param pc parse context (may contain UnpackConfig, UnpackSelector, EmbeddedLimits)
     * @param saveAll if true, include original document and metadata in the zip
     * @return streaming response with the zip file
     */
    private Response doUnpack(TikaInputStream tis, Metadata metadata, ParseContext pc, boolean saveAll) throws Exception {
        PipesParsingHelper helper = TikaResource.getPipesParsingHelper();
        if (helper == null) {
            throw new WebApplicationException("Pipes-based parsing is not enabled", Response.Status.SERVICE_UNAVAILABLE);
        }

        PipesParsingHelper.UnpackResult result = helper.parseUnpack(tis, metadata, pc, saveAll);

        Path zipFile = result.zipFile();
        if (zipFile == null) {
            // No embedded files were extracted
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }

        // Stream the zip file and clean up after streaming
        StreamingOutput stream = output -> {
            try {
                Files.copy(zipFile, output);
            } finally {
                result.cleanup();
            }
        };

        return Response.ok(stream)
                .type("application/zip")
                .build();
    }
}
