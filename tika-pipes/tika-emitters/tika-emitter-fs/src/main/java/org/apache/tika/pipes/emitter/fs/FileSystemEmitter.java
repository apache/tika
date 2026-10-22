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
package org.apache.tika.pipes.emitter.fs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import org.apache.tika.config.Field;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.StreamEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * Emitter to write to a file system.
 * <p>
 * This calculates the path to write to based on the {@link #basePath}
 * and the value of the {@link TikaCoreProperties#SOURCE_PATH} value.
 *
 * <pre class="prettyprint">
 *  &lt;properties&gt;
 *      &lt;emitters&gt;
 *          &lt;emitter class="org.apache.tika.pipes.emitter.fs.FileSystemEmitter&gt;
 *              &lt;params&gt;
 *                  &lt;!-- required --&gt;
 *                  &lt;param name="name" type="string"&gt;fs&lt;/param&gt;
 *                  &lt;!-- required --&gt;
 *                  &lt;param name="basePath" type="string"&gt;/path/to/output&lt;/param&gt;
 *                  &lt;!-- optional; default is 'json' --&gt;
 *                  &lt;param name="fileExtension" type="string"&gt;json&lt;/param&gt;
 *                  &lt;!-- optional; if the file already exists,
 *                       options ('skip', 'replace', 'exception')
 *                  default is 'exception' --&gt;
 *                  &lt;param name="onExists" type="string"&gt;skip&lt;/param&gt;
 *                  &lt;!-- optional; whether or not to pretty print the output
 *                      default is false --&gt;
 *                     &lt;param name="prettyPrint" type="boolean"&gt;true&lt;/param&gt;
 *                  &lt;!-- optional; 'none' or 'gzip'; gzip appends '.gz' to the file name
 *                      default is 'none' --&gt;
 *                     &lt;param name="compression" type="string"&gt;gzip&lt;/param&gt;
 *              &lt;/params&gt;
 *          &lt;/emitter&gt;
 *      &lt;/emitters&gt;
 *  &lt;/properties&gt;</pre>
 */
public class FileSystemEmitter extends AbstractEmitter implements StreamEmitter {

    private static final int GZIP_BUFFER_SIZE = 64 * 1024;

    private Path basePath = null;
    private String fileExtension = "json";
    private ON_EXISTS onExists = ON_EXISTS.EXCEPTION;

    private boolean prettyPrint = false;

    private COMPRESSION compression = COMPRESSION.NONE;

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException, TikaEmitterException {
        Path output;
        if (metadataList == null || metadataList.isEmpty()) {
            throw new TikaEmitterException("metadata list must not be null or of size 0");
        }

        if (fileExtension != null && ! fileExtension.isEmpty()) {
            emitKey += "." + fileExtension;
        }
        if (compression == COMPRESSION.GZIP) {
            emitKey += ".gz";
        }
        if (basePath != null) {
            output = basePath.resolve(emitKey);
            if (!output.toAbsolutePath().normalize().startsWith(basePath.toAbsolutePath().normalize())) {
                throw new TikaEmitterException("path traversal?! " + output.toAbsolutePath());
            }
        } else {
            output = Paths.get(emitKey);
        }

        if (output.getParent() != null && !Files.isDirectory(output.getParent())) {
            Files.createDirectories(output.getParent());
        }
        try (Writer writer = newWriter(output)) {
            JsonMetadataList.toJson(metadataList, writer, prettyPrint);
        }
    }

    private Writer newWriter(Path output) throws IOException {
        if (compression != COMPRESSION.GZIP) {
            return Files.newBufferedWriter(output, StandardCharsets.UTF_8);
        }
        return new BufferedWriter(new OutputStreamWriter(gzip(Files.newOutputStream(output)),
                StandardCharsets.UTF_8));
    }

    static OutputStream gzip(OutputStream os) throws IOException {
        try {
            return new GZIPOutputStream(os, GZIP_BUFFER_SIZE) {
                // GZIPOutputStream.close() skips closing os when finish() throws (e.g. disk full)
                @Override
                public void close() throws IOException {
                    try (os) {
                        super.close();
                    }
                }
            };
        } catch (IOException e) {
            os.close();
            throw e;
        }
    }

    @Field
    public void setBasePath(String basePath) {
        this.basePath = Paths.get(basePath);
    }

    /**
     * If you want to customize the output file's file extension.
     * Do not include the "."
     *
     * @param fileExtension
     */
    @Field
    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    /**
     * What to do if the target file already exists.  NOTE: if more than one
     * thread is trying write to the same file and {@link ON_EXISTS#REPLACE} is chosen,
     * you still might get a {@link FileAlreadyExistsException}.
     *
     * @param onExists
     */
    @Field
    public void setOnExists(String onExists) {
        switch (onExists) {
            case "skip":
                this.onExists = ON_EXISTS.SKIP;
                break;
            case "replace":
                this.onExists = ON_EXISTS.REPLACE;
                break;
            case "exception":
                this.onExists = ON_EXISTS.EXCEPTION;
                break;
            default:
                throw new IllegalArgumentException("Don't understand '" + onExists + "'; must be one of: 'skip', 'replace', 'exception'");
        }
    }

    @Field
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Compression for the json metadata list output: 'none' (default) or 'gzip'.
     * Does not apply to {@link #emit(String, InputStream, Metadata, ParseContext)}.
     *
     * @param compression
     */
    @Field
    public void setCompression(String compression) {
        switch (compression.toLowerCase(Locale.ROOT)) {
            case "none":
                this.compression = COMPRESSION.NONE;
                break;
            case "gzip":
                this.compression = COMPRESSION.GZIP;
                break;
            default:
                throw new IllegalArgumentException("Don't understand '" + compression + "'; must be one of: 'none', 'gzip'");
        }
    }

    @Override
    public void emit(String path, InputStream inputStream, Metadata userMetadata, ParseContext parseContext) throws IOException, TikaEmitterException {
        Path target = basePath.resolve(path);

        if (!Files.isDirectory(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        if (onExists == ON_EXISTS.REPLACE) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } else if (onExists == ON_EXISTS.EXCEPTION) {
            Files.copy(inputStream, target);
        } else if (onExists == ON_EXISTS.SKIP) {
            if (!Files.isRegularFile(target)) {
                try {
                    Files.copy(inputStream, target);
                } catch (FileAlreadyExistsException e) {
                    //swallow
                }
            }
        }
    }

    enum ON_EXISTS {
        SKIP, EXCEPTION, REPLACE
    }

    enum COMPRESSION {
        NONE, GZIP
    }
}
