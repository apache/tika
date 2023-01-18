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

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
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


public class GCSEmitter extends AbstractEmitter implements Initializable, StreamEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GCSEmitter.class);
    private String projectId;
    private String bucket;
    private String fileExtension = "json";
    private String prefix = null;
    private Storage storage;

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
        try (UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream()) {
            try (Writer writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
                JsonMetadataList.toJson(metadataList, writer);
            } catch (IOException e) {
                throw new TikaEmitterException("can't jsonify", e);
            }

            write(emitKey, new Metadata(), bos.toByteArray());
        }

    }

    /**
     * @param path         -- object path, not including the bucket
     * @param is           inputStream to copy
     * @param userMetadata this will be written to the s3 ObjectMetadata's userMetadata
     * @throws TikaEmitterException or IOexception if there is a Runtime s3 client exception
     */
    @Override
    public void emit(String path, InputStream is, Metadata userMetadata)
            throws IOException, TikaEmitterException {

        if (is instanceof TikaInputStream && ((TikaInputStream) is).hasFile()) {
            write(path, userMetadata, Files.readAllBytes(((TikaInputStream) is).getPath()));
        } else {
            try (UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream()) {
                IOUtils.copy(is, bos);
                write(path, userMetadata, bos.toByteArray());
            }
        }
    }

    private void write(String path, Metadata userMetadata, byte[] bytes) {
        if (!StringUtils.isBlank(prefix)) {
            path = prefix + "/" + path;
        }

        if (!StringUtils.isBlank(fileExtension)) {
            path += "." + fileExtension;
        }

        LOGGER.debug("about to emit to target bucket: ({}) path:({})", bucket, path);
        BlobId blobId = BlobId.of(bucket, path);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        for (String n : userMetadata.names()) {
            String[] vals = userMetadata.getValues(n);
            if (vals.length > 1) {
                LOGGER.warn("Can only write the first value for key {}. I see {} values.", n,
                        vals.length);
            }
            blobInfo.getMetadata().put(n, vals[0]);
        }
        storage.create(blobInfo, bytes);
    }


    @Field
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Field
    public void setBucket(String bucket) {
        this.bucket = bucket;
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
     * This initializes the gcs client.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //params have already been set...ignore them
        //TODO -- add other params to the builder as needed
        storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("bucket", this.bucket);
        mustNotBeEmpty("projectId", this.projectId);
    }

}
