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
package org.apache.tika.pipes.emitter.gcs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ComponentConfigs;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.emitter.AbstractStreamEmitter;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.StringUtils;

/**
 * Emitter to write parsed documents to Google Cloud Storage.
 *
 * <p>Example JSON configuration:</p>
 * <pre>
 * {
 *   "emitters": {
 *     "gcs-emitter": {
 *       "my-gcs": {
 *         "projectId": "my-project-id",
 *         "bucket": "my-bucket",
 *         "prefix": "output",
 *         "fileExtension": "json"
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class GCSEmitter extends AbstractStreamEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GCSEmitter.class);

    private final GCSEmitterConfig config;
    private final Storage storage;

    public static GCSEmitter build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        GCSEmitterConfig config = GCSEmitterConfig.load(extensionConfig.jsonConfig());
        config.validate();
        Storage storage = buildStorage(config);
        return new GCSEmitter(extensionConfig, config, storage);
    }

    private GCSEmitter(ExtensionConfig extensionConfig, GCSEmitterConfig config, Storage storage) {
        super(extensionConfig);
        this.config = config;
        this.storage = storage;
    }

    private static Storage buildStorage(GCSEmitterConfig config) {
        return StorageOptions.newBuilder()
                .setProjectId(config.projectId())
                .build()
                .getService();
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ComponentConfigs componentConfigs) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            throw new IOException("metadata list must not be null or empty");
        }
        try (UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get()) {
            try (Writer writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
                JsonMetadataList.toJson(metadataList, writer);
            }
            write(emitKey, new Metadata(), bos.toByteArray());
        }
    }

    @Override
    public void emit(String emitKey, InputStream inputStream, Metadata userMetadata, ComponentConfigs componentConfigs) throws IOException {
        if (inputStream instanceof TikaInputStream && ((TikaInputStream) inputStream).hasFile()) {
            write(emitKey, userMetadata, Files.readAllBytes(((TikaInputStream) inputStream).getPath()));
        } else {
            try (UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get()) {
                IOUtils.copy(inputStream, bos);
                write(emitKey, userMetadata, bos.toByteArray());
            }
        }
    }

    private void write(String path, Metadata userMetadata, byte[] bytes) {
        String prefix = config.getNormalizedPrefix();
        if (!StringUtils.isBlank(prefix)) {
            path = prefix + "/" + path;
        }

        String fileExtension = config.fileExtension();
        if (fileExtension == null) {
            fileExtension = "json";
        }
        if (!StringUtils.isBlank(fileExtension)) {
            path += "." + fileExtension;
        }

        LOGGER.debug("about to emit to target bucket: ({}) path:({})", config.bucket(), path);
        BlobId blobId = BlobId.of(config.bucket(), path);
        BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(blobId);

        for (String n : userMetadata.names()) {
            String[] vals = userMetadata.getValues(n);
            if (vals.length > 1) {
                LOGGER.warn("Can only write the first value for key {}. I see {} values.", n, vals.length);
            }
            blobInfoBuilder.setMetadata(java.util.Map.of(n, vals[0]));
        }

        storage.create(blobInfoBuilder.build(), bytes);
    }
}
