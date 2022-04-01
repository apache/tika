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

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.azure.core.credential.AzureSasCredential;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.StreamEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.StringUtils;


/**
 * Emit files to Azure blob storage. Must set endpoint, sasToken and container via config.
 *
 */

public class AZBlobEmitter extends AbstractEmitter implements Initializable, StreamEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AZBlobEmitter.class);
    private String fileExtension = "json";

    private String prefix = "";

    private String sasToken;
    private String container;
    private String endpoint;
    private BlobServiceClient blobServiceClient;
    private BlobContainerClient blobContainerClient;
    private boolean overwriteExisting = false;
    /**
     * Requires the src-bucket/path/to/my/file.txt in the {@link TikaCoreProperties#SOURCE_PATH}.
     *
     * @param metadataList
     * @throws IOException
     * @throws TikaException
     */
    @Override
    public void emit(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaEmitterException {
        if (metadataList == null || metadataList.size() == 0) {
            throw new TikaEmitterException("metadata list must not be null or of size 0");
        }
        //TODO: estimate size of metadata list.  Above a certain size,
        //create a temp file?
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
            JsonMetadataList.toJson(metadataList, writer);
        } catch (IOException e) {
            throw new TikaEmitterException("can't jsonify", e);
        }
        Metadata metadata = new Metadata();
        emit(emitKey, TikaInputStream.get(bos.toByteArray(), metadata), metadata);

    }

    /**
     * @param path         object path; prefix will be prepended
     * @param is           inputStream to copy, if a TikaInputStream contains an underlying file,
     *                     the client will upload the file; if a content-length is included in the
     *                     metadata, the client will upload the stream with the content-length;
     *                     otherwise, the client will copy the stream to a byte array and then
     *                     upload.
     * @param userMetadata this will be written to the az blob's properties.metadata
     * @throws TikaEmitterException or IOexception if there is a Runtime client exception
     */
    @Override
    public void emit(String path, InputStream is, Metadata userMetadata)
            throws IOException, TikaEmitterException {
        String lengthString = userMetadata.get(Metadata.CONTENT_LENGTH);
        long length = -1;
        if (lengthString != null) {
            try {
                length = Long.parseLong(lengthString);
            } catch (NumberFormatException e) {
                LOGGER.warn("Bad content-length: " + lengthString);
            }
        }
        if (is instanceof TikaInputStream && ((TikaInputStream) is).hasFile()) {
            write(path, userMetadata, ((TikaInputStream) is).getPath());
        } else if (length > -1) {
            LOGGER.debug("relying on the content-length set in the metadata object: {}", length);
            write(path, userMetadata, is, length);
        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(is, bos);
            write(path, userMetadata, bos.toByteArray());
        }
    }

    private void write(String path, Metadata userMetadata, InputStream is, long length) {
        String actualPath = getActualPath(path);
        LOGGER.debug("about to emit to target container: ({}) path:({})", container, actualPath);
        BlobClient blobClient = blobContainerClient.getBlobClient(actualPath);
        updateMetadata(blobClient, userMetadata);
        blobClient.upload(is, length, overwriteExisting);
    }

    private void write(String path, Metadata userMetadata, Path file) {
        String actualPath = getActualPath(path);
        LOGGER.debug("about to emit to target container: ({}) path:({})", container, actualPath);
        BlobClient blobClient = blobContainerClient.getBlobClient(actualPath);
        updateMetadata(blobClient, userMetadata);

        blobClient.uploadFromFile(file.toAbsolutePath().toString(), overwriteExisting);
    }

    private void write(String path, Metadata userMetadata, byte[] bytes) {
        String actualPath = getActualPath(path);
        LOGGER.debug("about to emit to target container: ({}) path:({})", container, actualPath);
        BlobClient blobClient = blobContainerClient.getBlobClient(actualPath);
        updateMetadata(blobClient, userMetadata);
        blobClient.upload(new ByteArrayInputStream(bytes), bytes.length, overwriteExisting);
    }

    private void updateMetadata(BlobClient blobClient, Metadata userMetadata) {
        for (String n : userMetadata.names()) {
            if (n.equals(Metadata.CONTENT_LENGTH)) {
                continue;
            }
            String[] vals = userMetadata.getValues(n);
            if (vals.length > 1) {
                LOGGER.warn("Can only write the first value for key {}. I see {} values.", n,
                        vals.length);
            }
            blobClient.getProperties().getMetadata().put(n, vals[0]);
        }

    }

    private String getActualPath(final String path) {
        String ret = null;
        if (!StringUtils.isBlank(prefix)) {
            ret = prefix + "/" + path;
        } else {
            ret = path;
        }

        if (!StringUtils.isBlank(fileExtension)) {
            ret += "." + fileExtension;
        }
        return ret;
    }

    @Field
    public void setSasToken(String sasToken) {
        this.sasToken = sasToken;
    }

    @Field
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @Field
    public void setContainer(String container) {
        this.container = container;
    }

    @Field
    public void setOverwriteExisting(boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    @Field
    public void setPrefix(String prefix) {
        //strip final "/" if it exists
        if (prefix.endsWith("/")) {
            this.prefix = prefix.substring(0, prefix.length() - 1);
        } else {
            this.prefix = prefix;
        }
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
     * This initializes the az blob container client
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //TODO -- allow authentication via other methods
        blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureSasCredential(sasToken))
                .buildClient();
        blobContainerClient = blobServiceClient.getBlobContainerClient(container);
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("sasToken", this.sasToken);
        mustNotBeEmpty("endpoint", this.endpoint);
        mustNotBeEmpty("container", this.container);
    }

}
