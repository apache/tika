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
package org.apache.tika.pipes.emitter.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.StreamEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

/**
 * Emits to existing s3 bucket
 * <pre class="prettyprint">
 *  &lt;properties&gt;
 *      &lt;emitters&gt;
 *          &lt;emitter class="org.apache.tika.pipes.emitter.s3.S3Emitter&gt;
 *              &lt;params&gt;
 *                  &lt;!-- required --&gt;
 *                  &lt;param name="name" type="string"&gt;s3e&lt;/param&gt;
 *                  &lt;!-- required --&gt;
 *                  &lt;param name="region" type="string"&gt;us-east-1&lt;/param&gt;
 *                  &lt;!-- required --&gt;
 *                  &lt;param name="credentialsProvider"
 *                       type="string"&gt;(profile|instance)&lt;/param&gt;
 *                  &lt;!-- required if credentialsProvider=profile--&gt;
 *                  &lt;param name="profile" type="string"&gt;my-profile&lt;/param&gt;
 *                  &lt;!-- required --&gt;
 *                  &lt;param name="bucket" type="string"&gt;my-bucket&lt;/param&gt;
 *                  &lt;!-- optional; prefix to add to the path before emitting;
 *                       default is no prefix --&gt;
 *                  &lt;param name="prefix" type="string"&gt;my-prefix&lt;/param&gt;
 *                  &lt;!-- optional; default is 'json' this will be added to the SOURCE_PATH
 *                                    if no emitter key is specified. Do not add a "."
 *                                     before the extension --&gt;
 *                  &lt;param name="fileExtension" type="string"&gt;json&lt;/param&gt;
 *                  &lt;!-- optional; default is 'true'-- whether to copy the
 *                     json to a local file before putting to s3 --&gt;
 *                  &lt;param name="spoolToTemp" type="bool"&gt;true&lt;/param&gt;
 *              &lt;/params&gt;
 *          &lt;/emitter&gt;
 *      &lt;/emitters&gt;
 *  &lt;/properties&gt;</pre>
 */
public class S3Emitter extends AbstractEmitter implements Initializable, StreamEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Emitter.class);
    private String region;
    private String profile;
    private String bucket;
    private String credentialsProvider;
    private String accessKey;
    private String secretKey;
    private String endpointConfigurationService;
    private String endpointConfigurationSigningRegion;
    private String fileExtension = "json";
    private boolean spoolToTemp = true;
    private String prefix = null;
    private int maxConnections = ClientConfiguration.DEFAULT_MAX_CONNECTIONS;
    private boolean pathStyleAccessEnabled = false;
    private AmazonS3 s3Client;

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

        if (!spoolToTemp) {
            UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
            try (Writer writer = new BufferedWriter(
                    new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {
                JsonMetadataList.toJson(metadataList, writer);
            } catch (IOException e) {
                throw new TikaEmitterException("can't jsonify", e);
            }
            byte[] bytes = bos.toByteArray();
            try (InputStream is = TikaInputStream.get(bytes)) {
                emit(emitKey, is, new Metadata());
            }
        } else {
            try (TemporaryResources tmp = new TemporaryResources()) {
                Path tmpPath = tmp.createTempFile();
                try (Writer writer = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE)) {
                    JsonMetadataList.toJson(metadataList, writer);
                } catch (IOException e) {
                    throw new TikaEmitterException("can't jsonify", e);
                }
                try (InputStream is = TikaInputStream.get(tmpPath)) {
                    emit(emitKey, is, new Metadata());
                }
            }
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

        if (!StringUtils.isBlank(prefix)) {
            path = prefix + "/" + path;
        }

        if (!StringUtils.isBlank(fileExtension)) {
            path += "." + fileExtension;
        }

        LOGGER.debug("about to emit to target bucket: ({}) path:({})", bucket, path);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        for (String n : userMetadata.names()) {
            String[] vals = userMetadata.getValues(n);
            if (vals.length > 1) {
                LOGGER.warn("Can only write the first value for key {}. I see {} values.",
                        n,
                        vals.length);
            }
            objectMetadata.addUserMetadata(n, vals[0]);
        }
        //In practice, sending a file is more robust
        //We ran into stream reset issues during digesting, and aws doesn't
        //like putObjects for streams without lengths
        if (is instanceof TikaInputStream) {
            if (((TikaInputStream) is).hasFile()) {
                try {
                    PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, path,
                            ((TikaInputStream) is).getFile()).withMetadata(objectMetadata);
                    s3Client.putObject(putObjectRequest);
                } catch (IOException e) {
                    throw new TikaEmitterException("exception sending underlying file", e);
                }
                return;
            }
        }
        try {
            s3Client.putObject(bucket, path, is, objectMetadata);
        } catch (AmazonClientException e) {
            throw new IOException("problem writing s3object", e);
        }
    }

    /**
     * Whether or not to spool the metadatalist to a tmp file before putting object.
     * Default: <code>true</code>.  If this is set to <code>false</code>,
     * this emitter writes the json object to memory and then puts that into s3.
     *
     * @param spoolToTemp
     */
    @Field
    public void setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
    }

    @Field
    public void setRegion(String region) {
        this.region = region;
    }

    @Field
    public void setProfile(String profile) {
        this.profile = profile;
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

    @Field
    public void setCredentialsProvider(String credentialsProvider) {
        if (!credentialsProvider.equals("profile") && !credentialsProvider.equals("instance") && !credentialsProvider.equals("key_secret")) {
            throw new IllegalArgumentException(
                    "credentialsProvider must be either 'profile', 'instance' or 'key_secret'");
        }
        this.credentialsProvider = credentialsProvider;
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

    @Field
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    @Field
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * maximum number of http connections allowed.  This should be
     * greater than or equal to the number of threads emitting to S3.
     *
     * @param maxConnections
     */
    @Field
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Field
    public void setEndpointConfigurationService(String endpointConfigurationService) {
        this.endpointConfigurationService = endpointConfigurationService;
    }

    @Field
    public void setEndpointConfigurationSigningRegion(String endpointConfigurationSigningRegion) {
        this.endpointConfigurationSigningRegion = endpointConfigurationSigningRegion;
    }

    /**
     * This initializes the s3 client. Note, we wrap S3's RuntimeExceptions,
     * e.g. AmazonClientException in a TikaConfigException.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //params have already been set...ignore them
        AWSCredentialsProvider provider;
        if ("instance".equals(credentialsProvider)) {
            provider = InstanceProfileCredentialsProvider.getInstance();
        } else if ("profile".equals(credentialsProvider)) {
            provider = new ProfileCredentialsProvider(profile);
        } else if (credentialsProvider.equals("key_secret")) {
            provider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        } else {
            throw new TikaConfigException("credentialsProvider must be set and " +
                    "must be either 'instance', 'profile' or 'key_secret'");
        }
        ClientConfiguration clientConfig = new ClientConfiguration()
                .withMaxConnections(maxConnections);
        try {
            AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard()
                    .withClientConfiguration(clientConfig)
                    .withCredentials(provider)
                    .withPathStyleAccessEnabled(pathStyleAccessEnabled);
            if (!StringUtils.isBlank(endpointConfigurationService) && !StringUtils.isBlank(endpointConfigurationSigningRegion)) {
                amazonS3ClientBuilder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointConfigurationService, endpointConfigurationSigningRegion));
            } else {
                amazonS3ClientBuilder.withRegion(region);
            }
            s3Client = amazonS3ClientBuilder.build();
        } catch (AmazonClientException e) {
            throw new TikaConfigException("can't initialize s3 emitter", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("bucket", this.bucket);
    }

    @Field
    public void setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
        this.pathStyleAccessEnabled = pathStyleAccessEnabled;
    }
}
