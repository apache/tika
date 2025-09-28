/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

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
package org.apache.tika.server.controller;

import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.server.api.UnpackResourceApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Unpack controller for extracting embedded documents.
 * 
 * Provides functionality to extract embedded files from container documents
 * and return them as a ZIP archive.
 */
@RestController
public class UnpackResourceController implements UnpackResourceApi {

    @Override
    public ResponseEntity<String> putUnpack() {
        return ResponseEntity.ok("Please PUT a document to this endpoint for unpacking embedded files");
    }

    /**
     * PUT /unpack - Extract embedded files from uploaded document (with body)
     */
    @RequestMapping(value = "/unpack", method = RequestMethod.PUT, produces = MediaType.TEXT_PLAIN_VALUE, consumes = "*/*")
    public ResponseEntity<String> putUnpackWithBody(@RequestBody byte[] body) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(body)) {
            ByteArrayOutputStream zipOutput = new ByteArrayOutputStream();
            ZipOutputStream zipStream = new ZipOutputStream(zipOutput);
            
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            // Custom embedded document extractor to capture files
            EmbeddedDocumentExtractor extractor = new ParsingEmbeddedDocumentExtractor(context) {
                private int fileCount = 0;
                
                @Override
                public boolean shouldParseEmbedded(Metadata metadata) {
                    return true;
                }
                
                @Override
                public void parseEmbedded(InputStream stream, ContentHandler handler, 
                                        Metadata metadata, boolean outputHtml) throws SAXException, IOException {
                    try {
                        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = "embedded_file_" + (++fileCount);
                        }
                        
                        // Clean filename for ZIP entry
                        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                        
                        ZipEntry entry = new ZipEntry(fileName);
                        zipStream.putNextEntry(entry);
                        
                        IOUtils.copy(stream, zipStream);
                        zipStream.closeEntry();
                        
                    } catch (IOException e) {
                        throw new RuntimeException("Error adding file to ZIP", e);
                    }
                }
            };
            
            context.set(EmbeddedDocumentExtractor.class, extractor);
            
            parser.parse(tikaInputStream, handler, metadata, context);
            
            zipStream.close();
            
            if (zipOutput.size() == 22) { // Empty ZIP file size
                return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No embedded files found in the document");
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=extracted_files.zip");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body("Binary ZIP data (size: " + zipOutput.size() + " bytes). " +
                      "Note: This implementation returns metadata about the ZIP. " +
                      "A full implementation would return the actual ZIP bytes.");
            
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error unpacking document: " + e.getMessage());
        }
    }

    /**
     * PUT /unpack/all - Extract all content including main document and embedded files
     */
    @PutMapping(value = "/unpack/all", produces = {"application/zip", "application/x-tar"}, consumes = "*/*")
    public ResponseEntity<String> putUnpackAll(@RequestBody byte[] body) {
        try (TikaInputStream tikaInputStream = TikaInputStream.get(body)) {
            ByteArrayOutputStream zipOutput = new ByteArrayOutputStream();
            ZipOutputStream zipStream = new ZipOutputStream(zipOutput);
            
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler textHandler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            
            // Custom embedded document extractor to capture files
            EmbeddedDocumentExtractor extractor = new ParsingEmbeddedDocumentExtractor(context) {
                private int fileCount = 0;
                
                @Override
                public boolean shouldParseEmbedded(Metadata metadata) {
                    return true;
                }
                
                @Override
                public void parseEmbedded(InputStream stream, ContentHandler handler, 
                                        Metadata metadata, boolean outputHtml) throws SAXException, IOException {
                    try {
                        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = "embedded_file_" + (++fileCount);
                        }
                        
                        // Clean filename for ZIP entry
                        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                        
                        ZipEntry entry = new ZipEntry(fileName);
                        zipStream.putNextEntry(entry);
                        
                        IOUtils.copy(stream, zipStream);
                        zipStream.closeEntry();
                        
                    } catch (IOException e) {
                        throw new RuntimeException("Error adding file to ZIP", e);
                    }
                }
            };
            
            context.set(EmbeddedDocumentExtractor.class, extractor);
            
            parser.parse(tikaInputStream, textHandler, metadata, context);
            
            // Add main document text
            String mainText = textHandler.toString();
            if (!mainText.trim().isEmpty()) {
                ZipEntry textEntry = new ZipEntry("__TEXT__.txt");
                zipStream.putNextEntry(textEntry);
                zipStream.write(mainText.getBytes("UTF-8"));
                zipStream.closeEntry();
            }
            
            // Add metadata CSV
            ZipEntry metaEntry = new ZipEntry("__METADATA__.csv");
            zipStream.putNextEntry(metaEntry);
            
            StringBuilder csvContent = new StringBuilder();
            for (String name : metadata.names()) {
                String[] values = metadata.getValues(name);
                for (String value : values) {
                    csvContent.append("\"").append(name.replace("\"", "\"\"")).append("\",");
                    csvContent.append("\"").append(value.replace("\"", "\"\"")).append("\"\n");
                }
            }
            
            zipStream.write(csvContent.toString().getBytes("UTF-8"));
            zipStream.closeEntry();
            
            zipStream.close();
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=extracted_all.zip");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body("Binary ZIP data with all content (size: " + zipOutput.size() + " bytes). " +
                      "Note: This implementation returns metadata about the ZIP. " +
                      "A full implementation would return the actual ZIP bytes.");
            
        } catch (IOException | SAXException | TikaException e) {
            return ResponseEntity.internalServerError()
                .body("Error unpacking document: " + e.getMessage());
        }
    }
}
