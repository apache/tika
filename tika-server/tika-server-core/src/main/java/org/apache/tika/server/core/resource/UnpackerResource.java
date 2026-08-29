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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.extractor.UnpackSelector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.extractor.StandardUnpackSelector;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.serialization.JsonMetadata;

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
 *   <li>PUT /unpack/thumbnail - Return the document thumbnail with its metadata</li>
 *   <li>POST /unpack/thumbnail - The same, multipart</li>
 * </ul>
 * <p>
 * <b>Thumbnail:</b>
 * <p>
 * {@code /unpack/thumbnail} returns the document's thumbnail as JSON: the
 * {@code /rmeta} metadata object of the embedded document that is the thumbnail
 * and the image as base64, or {@code 204} if the document has none. It parses
 * with the {@link ThumbnailDefaults} (the first PDF page rendered, the EMF/WMF
 * thumbnail rendered), without text extraction or OCR, and picks, in this
 * order, the raster THUMBNAIL directly below the document, the rendering of a
 * vector THUMBNAIL, or the RENDERING of the first page. It extracts what the
 * document carries; it does not resize or convert.
 * <p>
 * {@code ?renderThumbnails=true} on {@code /unpack} and {@code /unpack/all}
 * applies the same defaults under the request's own config, so the zip holds
 * the thumbnail as a raster image among the embedded documents.
 * <pre>
 * {
 *   "metadata": { "Content-Type": "image/png", "tiff:ImageWidth": "800", ... },
 *   "image": "iVBORw0KGgo..."
 * }
 * </pre>
 * <p>
 * <b>Configuration:</b>
 * <p>
 * None required. The server wires up its own {@code __}-prefixed fetcher and emitter against
 * temp directories it owns, confined by {@code basePath}. Those ids are reserved and a request
 * that names one is rejected.
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
 *     "standard-unpack-selector": {
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
 * <b>Frictionless Data Package Format:</b>
 * <p>
 * To receive output in Frictionless Data Package format (with datapackage.json manifest,
 * SHA256 hashes, and files in unpacked/ subdirectory), use:
 * <pre>
 * {
 *   "parse-context": {
 *     "unpack-config": {
 *       "outputFormat": "FRICTIONLESS",
 *       "outputMode": "ZIPPED",
 *       "includeFullMetadata": true
 *     }
 *   }
 * }
 * </pre>
 * <p>
 * The Frictionless zip structure:
 * <pre>
 * output.zip
 * ├── datapackage.json      # Manifest with file list, SHA256 hashes, mimetypes
 * ├── metadata.json         # Full RMETA metadata (if includeFullMetadata=true)
 * └── unpacked/
 *     ├── 00000001.pdf
 *     ├── 00000002.png
 *     └── ...
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

    private final TikaResource tikaResource;

    public UnpackerResource(TikaResource tikaResource) {
        this.tikaResource = tikaResource;
    }

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
    public Response unpack(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info,
                           @QueryParam("renderThumbnails") boolean renderThumbnails) throws Exception {
        ParseContext pc = tikaResource.createRequestContext();
        Metadata metadata = tikaResource.newRequestMetadata();
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            fillMetadata(null, metadata, httpHeaders.getRequestHeaders());
            TikaResource.logRequest(LOG, "/unpack", metadata);
            if (renderThumbnails) {
                tikaResource.getThumbnailDefaults().applyTo(pc);
            }
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
    public Response unpackWithConfig(List<Attachment> attachments, @Context HttpHeaders httpHeaders, @Context UriInfo info,
                                     @QueryParam("renderThumbnails") boolean renderThumbnails) throws Exception {
        ParseContext pc = tikaResource.createRequestContext();
        Metadata metadata = tikaResource.newRequestMetadata();
        try (TikaInputStream tis = tikaResource.setupMultipartConfig(attachments, metadata, pc)) {
            TikaResource.logRequest(LOG, "/unpack", metadata);
            if (renderThumbnails) {
                //under the request's config, which setupMultipartConfig has already merged
                tikaResource.getThumbnailDefaults().applyTo(pc);
            }
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
    public Response unpackAll(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info,
                           @QueryParam("renderThumbnails") boolean renderThumbnails) throws Exception {
        ParseContext pc = tikaResource.createRequestContext();
        Metadata metadata = tikaResource.newRequestMetadata();
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            fillMetadata(null, metadata, httpHeaders.getRequestHeaders());
            TikaResource.logRequest(LOG, "/unpack/all", metadata);
            if (renderThumbnails) {
                tikaResource.getThumbnailDefaults().applyTo(pc);
            }
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
    public Response unpackAllWithConfig(List<Attachment> attachments, @Context HttpHeaders httpHeaders, @Context UriInfo info,
                                     @QueryParam("renderThumbnails") boolean renderThumbnails) throws Exception {
        ParseContext pc = tikaResource.createRequestContext();
        Metadata metadata = tikaResource.newRequestMetadata();
        try (TikaInputStream tis = tikaResource.setupMultipartConfig(attachments, metadata, pc)) {
            TikaResource.logRequest(LOG, "/unpack/all", metadata);
            if (renderThumbnails) {
                //under the request's config, which setupMultipartConfig has already merged
                tikaResource.getThumbnailDefaults().applyTo(pc);
            }
            return doUnpack(tis, metadata, pc, true);
        }
    }

    /**
     * Returns the document thumbnail with its metadata (simple PUT).
     */
    @jakarta.ws.rs.Path("/thumbnail")
    @PUT
    @Produces("application/json")
    public Response unpackThumbnail(InputStream is, @Context HttpHeaders httpHeaders) throws Exception {
        ParseContext pc = tikaResource.createRequestContext();
        Metadata metadata = tikaResource.newRequestMetadata();
        try (TikaInputStream tis = TikaInputStream.get(is)) {
            fillMetadata(null, metadata, httpHeaders.getRequestHeaders());
            TikaResource.logRequest(LOG, "/unpack/thumbnail", metadata);
            return doUnpackThumbnail(tis, metadata, pc);
        }
    }

    /**
     * Returns the document thumbnail with its metadata (multipart POST, "file" part).
     */
    @jakarta.ws.rs.Path("/thumbnail")
    @POST
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public Response unpackThumbnailMultipart(List<Attachment> attachments, @Context HttpHeaders httpHeaders)
            throws Exception {
        ParseContext pc = tikaResource.createRequestContext();
        Metadata metadata = tikaResource.newRequestMetadata();
        try (TikaInputStream tis = tikaResource.setupMultipartConfig(attachments, metadata, pc)) {
            TikaResource.logRequest(LOG, "/unpack/thumbnail", metadata);
            return doUnpackThumbnail(tis, metadata, pc);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String METADATA_SUFFIX = ".metadata.json";
    /**
     * A thumbnail travels base64-encoded inside a JSON object, so it is
     * bounded here regardless of the unpack limits; camera previews and
     * page renderings are a few MB at most.
     */
    static final long MAX_THUMBNAIL_BYTES = 32L * 1024 * 1024;

    /**
     * Parses in unpack mode with the thumbnail configuration, then selects
     * the thumbnail among the extracted embedded documents.
     */
    private Response doUnpackThumbnail(TikaInputStream tis, Metadata metadata, ParseContext pc)
            throws Exception {
        PipesParsingHelper helper = tikaResource.getPipesParsingHelper();
        if (helper == null) {
            throw new WebApplicationException("Pipes-based parsing is not enabled", Response.Status.SERVICE_UNAVAILABLE);
        }
        configureThumbnailParse(pc);

        PipesParsingHelper.UnpackResult result = helper.parseUnpack(tis, metadata, pc, false);
        if (result.zipFile() == null) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }
        try (ZipFile zip = new ZipFile(result.zipFile().toFile())) {
            Map<String, Metadata> extracted = readExtractedMetadata(zip);
            Metadata thumbnail = ThumbnailSelector.select(new ArrayList<>(extracted.values()));
            if (thumbnail == null) {
                throw new WebApplicationException(Response.Status.NO_CONTENT);
            }
            String entryName = null;
            for (Map.Entry<String, Metadata> e : extracted.entrySet()) {
                if (e.getValue() == thumbnail) {
                    entryName = e.getKey();
                }
            }
            ZipEntry imageEntry = entryName == null ? null : zip.getEntry(entryName);
            if (imageEntry == null) {
                throw new WebApplicationException(Response.Status.NO_CONTENT);
            }
            if (imageEntry.getSize() > MAX_THUMBNAIL_BYTES) {
                throw new WebApplicationException("thumbnail larger than " + MAX_THUMBNAIL_BYTES + " bytes",
                        Response.Status.REQUEST_ENTITY_TOO_LARGE);
            }
            byte[] image;
            try (InputStream is = zip.getInputStream(imageEntry)) {
                //the entry size is a claim; read one byte past the limit to know
                image = is.readNBytes((int) MAX_THUMBNAIL_BYTES + 1);
            }
            if (image.length > MAX_THUMBNAIL_BYTES) {
                throw new WebApplicationException("thumbnail larger than " + MAX_THUMBNAIL_BYTES + " bytes",
                        Response.Status.REQUEST_ENTITY_TOO_LARGE);
            }
            StringWriter metadataJson = new StringWriter();
            JsonMetadata.toJson(thumbnail, metadataJson);
            ObjectNode root = MAPPER.createObjectNode();
            root.set("metadata", MAPPER.readTree(metadataJson.toString()));
            root.put("image", Base64.getEncoder().encodeToString(image));
            return Response.ok(MAPPER.writeValueAsString(root)).type("application/json").build();
        } finally {
            result.cleanup();
        }
    }

    /**
     * The {@link ThumbnailDefaults} plus what only makes sense when the thumbnail
     * is all the caller wants: no text, no OCR of the rendering, only THUMBNAIL
     * and RENDERING embedded documents extracted, together with their metadata,
     * down to the rendering of a thumbnail (depth 2). The request's own parser
     * configuration wins where present.
     */
    private void configureThumbnailParse(ParseContext pc) {
        //the text is not part of the answer: do not extract it
        tikaResource.setupContentHandlerFactory(pc, "ignore");
        tikaResource.getThumbnailDefaults()
                .with("{\"pdf-parser\": {\"ocr\": {\"strategy\": \"NO_OCR\"}}, "
                        + "\"tesseract-ocr-parser\": {\"skipOcr\": true}}")
                .applyTo(pc);
        StandardUnpackSelector selector = new StandardUnpackSelector();
        selector.setIncludeEmbeddedResourceTypes(new HashSet<>(Arrays.asList(
                TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.name(),
                TikaCoreProperties.EmbeddedResourceType.RENDERING.name())));
        pc.set(UnpackSelector.class, selector);
        if (pc.get(EmbeddedLimits.class) == null) {
            EmbeddedLimits limits = new EmbeddedLimits();
            //the thumbnail is at depth 1, its rendering at depth 2; 3 because of
            //TIKA-4857 (the limit is off by one), lower to 2 once that is fixed
            limits.setMaxDepth(3);
            pc.set(EmbeddedLimits.class, limits);
        }
        UnpackConfig unpackConfig = pc.get(UnpackConfig.class);
        if (unpackConfig == null) {
            unpackConfig = tikaResource.newConfigUnpackConfig();
            if (unpackConfig == null) {
                unpackConfig = new UnpackConfig();
            }
        }
        unpackConfig.setIncludeMetadataInZip(true);
        pc.set(UnpackConfig.class, unpackConfig);
    }

    /**
     * Reads the {@code *.metadata.json} entries of the unpack zip, keyed by
     * the name of the file they describe, in zip order.
     */
    private static Map<String, Metadata> readExtractedMetadata(ZipFile zip) throws IOException {
        Map<String, Metadata> extracted = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.endsWith(METADATA_SUFFIX)) {
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry),
                    StandardCharsets.UTF_8)) {
                extracted.put(name.substring(0, name.length() - METADATA_SUFFIX.length()),
                        JsonMetadata.fromJson(reader));
            }
        }
        return extracted;
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
        PipesParsingHelper helper = tikaResource.getPipesParsingHelper();
        if (helper == null) {
            throw new WebApplicationException("Pipes-based parsing is not enabled", Response.Status.SERVICE_UNAVAILABLE);
        }

        // parseUnpack mutates this and so overrides the worker's own config; seed it from the
        // config's unpack-config (a per-request instance) rather than from defaults. A config
        // supplied by the request itself already sits in pc and wins, as before.
        if (pc.get(UnpackConfig.class) == null) {
            UnpackConfig fromConfig = tikaResource.newConfigUnpackConfig();
            if (fromConfig != null) {
                pc.set(UnpackConfig.class, fromConfig);
            }
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
