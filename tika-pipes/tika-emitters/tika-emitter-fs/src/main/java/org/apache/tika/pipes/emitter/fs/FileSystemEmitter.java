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

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.tika.config.Field;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.StreamEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;

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
 *              &lt;/params&gt;
 *          &lt;/emitter&gt;
 *      &lt;/emitters&gt;
 *  &lt;/properties&gt;</pre>
 */
public class FileSystemEmitter extends AbstractEmitter implements StreamEmitter {

    private Path basePath = null;
    private String fileExtension = "json";
    private ON_EXISTS onExists = ON_EXISTS.EXCEPTION;

    private boolean prettyPrint = false;

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
            throws IOException, TikaEmitterException {
        Path output;
        if (metadataList == null || metadataList.size() == 0) {
            throw new TikaEmitterException("metadata list must not be null or of size 0");
        }

        if (fileExtension != null && fileExtension.length() > 0) {
            emitKey += "." + fileExtension;
        }
        if (basePath != null) {
            output = basePath.resolve(emitKey);
        } else {
            output = Paths.get(emitKey);
        }

        if (!Files.isDirectory(output.getParent())) {
            Files.createDirectories(output.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            JsonMetadataList.toJson(metadataList, writer, prettyPrint);
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
                throw new IllegalArgumentException("Don't understand '" + onExists +
                                                   "'; must be one of: 'skip', 'replace', 'exception'");
        }
    }

    @Field
    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    @Override
    public void emit(String path, InputStream inputStream, Metadata userMetadata, ParseContext parseContext)
            throws IOException, TikaEmitterException {
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
}
