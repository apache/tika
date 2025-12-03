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
package org.apache.tika.pipes.emitter.azblob;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import com.azure.core.credential.AzureSasCredential;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.AbstractStreamEmitter;
import org.apache.tika.pipes.api.emitter.StreamEmitter;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.utils.StringUtils;

/**
 * Emitter to write files to Azure Blob Storage.
 *
 * <p>Example JSON configuration:</p>
 * <pre>
 * {
 *   "emitters": {
 *     "az-blob-emitter": {
 *       "my-azure": {
 *         "endpoint": "https://myaccount.blob.core.windows.net",
 *         "sasToken": "sv=2020-08-04&amp;ss=b...",
 *         "container": "my-container",
 *         "prefix": "output",
 *         "fileExtension": "json",
 *         "overwriteExisting": false
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
public class AZBlobEmitter extends AbstractStreamEmitter implements StreamEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AZBlobEmitter.class);

    private final AZBlobEmitterConfig config;
    private final BlobContainerClient blobContainerClient;

    public static AZBlobEmitter build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        AZBlobEmitterConfig config = AZBlobEmitterConfig.load(extensionConfig.json());
        config.validate();
        BlobContainerClient containerClient = buildContainerClient(config);
        return new AZBlobEmitter(extensionConfig, config, containerClient);
    }

    private AZBlobEmitter(ExtensionConfig extensionConfig, AZBlobEmitterConfig config, BlobContainerClient containerClient) {
        super(extensionConfig);
        this.config = config;
        this.blobContainerClient = containerClient;
    }

    private static BlobContainerClient buildContainerClient(AZBlobEmitterConfig config) {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(config.endpoint())
                .credential(new AzureSasCredential(config.sasToken()))
                .buildClient();
        return blobServiceClient.getBlobContainerClient(config.container());
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext) throws IOException {
        if (metadataList == null || metadataList.isEmpty()) {
            throw new IOException("metadata list must not be null or empty");
        }
        UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
        try (Writer writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
            JsonMetadataList.toJson(metadataList, writer);
        }
        Metadata metadata = new Metadata();
        emit(emitKey, TikaInputStream.get(bos.toByteArray(), metadata), metadata, parseContext);
    }

    @Override
    public void emit(String emitKey, InputStream inputStream, Metadata userMetadata, ParseContext parseContext) throws IOException {
        String lengthString = userMetadata.get(Metadata.CONTENT_LENGTH);
        long length = -1;
        if (lengthString != null) {
            try {
                length = Long.parseLong(lengthString);
            } catch (NumberFormatException e) {
                LOGGER.warn("Bad content-length: {}", lengthString);
            }
        }
        if (inputStream instanceof TikaInputStream && ((TikaInputStream) inputStream).hasFile()) {
            write(emitKey, userMetadata, ((TikaInputStream) inputStream).getPath());
        } else if (length > -1) {
            LOGGER.debug("relying on the content-length set in the metadata object: {}", length);
            write(emitKey, userMetadata, inputStream, length);
        } else {
            try (UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get()) {
                IOUtils.copy(inputStream, bos);
                write(emitKey, userMetadata, bos.toByteArray());
            }
        }
    }

    private void write(String path, Metadata userMetadata, InputStream is, long length) {
        String actualPath = getActualPath(path);
        LOGGER.debug("about to emit to target container: ({}) path:({})", config.container(), actualPath);
        BlobClient blobClient = blobContainerClient.getBlobClient(actualPath);
        updateMetadata(blobClient, userMetadata);
        blobClient.upload(is, length, config.overwriteExisting());
    }

    private void write(String path, Metadata userMetadata, Path file) {
        String actualPath = getActualPath(path);
        LOGGER.debug("about to emit to target container: ({}) path:({})", config.container(), actualPath);
        BlobClient blobClient = blobContainerClient.getBlobClient(actualPath);
        updateMetadata(blobClient, userMetadata);
        blobClient.uploadFromFile(file.toAbsolutePath().toString(), config.overwriteExisting());
    }

    private void write(String path, Metadata userMetadata, byte[] bytes) throws IOException {
        String actualPath = getActualPath(path);
        LOGGER.debug("about to emit to target container: ({}) path:({})", config.container(), actualPath);
        BlobClient blobClient = blobContainerClient.getBlobClient(actualPath);
        updateMetadata(blobClient, userMetadata);
        blobClient.upload(UnsynchronizedByteArrayInputStream.builder().setByteArray(bytes).get(), bytes.length, config.overwriteExisting());
    }

    private void updateMetadata(BlobClient blobClient, Metadata userMetadata) {
        for (String n : userMetadata.names()) {
            if (n.equals(Metadata.CONTENT_LENGTH)) {
                continue;
            }
            String[] vals = userMetadata.getValues(n);
            if (vals.length > 1) {
                LOGGER.warn("Can only write the first value for key {}. I see {} values.", n, vals.length);
            }
            blobClient.getProperties().getMetadata().put(n, vals[0]);
        }
    }

    private String getActualPath(final String path) {
        String ret;
        String prefix = config.getNormalizedPrefix();
        if (!StringUtils.isBlank(prefix)) {
            ret = prefix + "/" + path;
        } else {
            ret = path;
        }

        String fileExtension = config.getFileExtensionOrDefault();
        if (!StringUtils.isBlank(fileExtension)) {
            ret += "." + fileExtension;
        }
        return ret;
    }
}
