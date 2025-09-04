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
package org.apache.tika.parser.ntfs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.Collections;
import java.util.Set;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ntfs.utils.stream.AbstractFileInputStream;

/**
 * Tika parser for analyzing NTFS disk images using SleuthKit.
 * This parser extracts file metadata and content from an NTFS volume.
 */
public class NTFSParser implements Parser {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Auto parser for handling embedded file content. */
    private final Parser autoParser = new AutoDetectParser();

    /** Maximum buffer size for stream copying. */
    private static final int CHUNK_SIZE = 8192;

    /**
     * Returns the set of media types supported by this parser.
     *
     * @param context the parse context
     * @return a singleton set containing {@code application/x-ntfs}
     */
    @Override
    public Set<MediaType> getSupportedTypes(final ParseContext context) {
        return Collections.singleton(MediaType.application("x-ntfs"));
    }

    /**
     * Parses an NTFS disk image stream using SleuthKit to
     * extract and process files.
     *
     * @param stream   the input stream containing the NTFS image
     * @param handler  the SAX content handler to receive XHTML SAX events
     * @param metadata the metadata associated with the document
     * @param context  the parser context
     * @throws IOException   if an I/O error occurs
     * @throws TikaException if an error occurs during parsing
     */
    @Override
    public void parse(final InputStream stream, final ContentHandler handler,
                      final Metadata metadata, final ParseContext context)
            throws IOException, TikaException {

        // Copy input stream to a temporary file for SleuthKit
        var tempImage = File.createTempFile("ntfs", ".img");

        try (var out = new FileOutputStream(tempImage)) {
            var buffer = new byte[CHUNK_SIZE];
            int len;
            while ((len = stream.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }

        SleuthkitCase skCase = null;
        try {
            skCase = SleuthkitCase.newCase("ntfs-case.db");

            var transaction = skCase.beginTransaction();
            var image = skCase.addImage(
                    TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_DETECT,
                    0,
                    tempImage.length(),
                    tempImage.getName(),
                    Collections.singletonList(tempImage.getAbsolutePath()),
                    "UTC",
                    null, null, null,
                    null,
                    transaction
            );
            transaction.commit();

            for (var fileSystem : skCase.getImageFileSystems(image)) {
                var root = fileSystem.getRootDirectory();
                if (root != null) {
                    processFileRecursively(root, handler, context);
                }
            }

        } catch (TskCoreException e) {
            throw new TikaException("SleuthKit parsing failed", e);
        } finally {
            if (skCase != null) {
                try {
                    skCase.close();
                } catch (Exception ignored) {
                    // Swallow close exception
                }
            }
            tempImage.delete();
        }
    }

    /**
     * Recursively traverses and processes files and directories
     * within the NTFS volume.
     *
     * @param file    the file or directory to process
     * @param handler the content handler to which parsed content is sent
     * @param context the parse context for embedded content parsing
     */
    private void processFileRecursively(final AbstractFile file,
                                        final ContentHandler handler,
                                        final ParseContext context) {
        var metaType = TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG;
        try {
            if (file.isDir()) {
                for (AbstractFile child : file.listFiles()) {
                    processFileRecursively(child, handler, context);
                }
            } else if (file.getMetaType() == metaType && file.getSize() > 0) {

                var fileMetadata = new Metadata();
                fileMetadata.set("Filename", file.getName());
                fileMetadata.set("Size", String.valueOf(file.getSize()));
                fileMetadata.set("Created",
                        String.valueOf(file.getCrtimeAsDate()));
                fileMetadata.set("Modified",
                        String.valueOf(file.getMtimeAsDate()));

                try (var fileStream = new AbstractFileInputStream(file)) {
                    autoParser.parse(fileStream, handler,
                            fileMetadata, context);
                }
            }
        } catch (Exception e) {
            // Log and skip problematic files
            System.err.println("Failed to parse file: "
                    + file.getName() + " - " + e.getMessage());
        }
    }
}
