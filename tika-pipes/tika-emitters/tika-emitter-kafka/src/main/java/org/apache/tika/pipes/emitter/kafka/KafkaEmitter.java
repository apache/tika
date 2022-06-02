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
package org.apache.tika.pipes.emitter.kafka;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits to kafka
 */
public class KafkaEmitter extends AbstractEmitter implements Initializable, StreamEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaEmitter.class);
    private String server;
    private String topic;
    private String groupId;
    private String prefix = null;
    private String fileExtension = null;
    private Producer<String, Map<String, Object>> producer;

    @Field
    public void setServer(String server) {
        this.server = server;
    }

    @Field
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Field
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

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

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
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

        LOGGER.debug("about to emit to target topic: ({}) path:({})", topic, path);

        Map<String, Object> fields = new HashMap<>();
        for (String n : userMetadata.names()) {
            String[] vals = userMetadata.getValues(n);
            if (vals.length > 1) {
                LOGGER.warn("Can only write the first value for key {}. I see {} values.",
                        n,
                        vals.length);
            }
            fields.put(n, vals[0]);
        }

        producer.send(new ProducerRecord<>(topic, path, fields));
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
     * This initializes the s3 client. Note, we wrap S3's RuntimeExceptions,
     * e.g. AmazonClientException in a TikaConfigException.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

        // create instance for properties to access producer configs   
        Properties props = new Properties();

        //Assign localhost id
        props.put("bootstrap.servers", server);

        //Set acknowledgements for producer requests.      
        props.put("acks", "all");

        //If the request fails, the producer can automatically retry,
        props.put("retries", 0);

        //Specify buffer size in config
        props.put("batch.size", 16384);

        //Reduce the no of requests less than 0   
        props.put("linger.ms", 1);

        //The buffer.memory controls the total amount of memory available to the producer for buffering.   
        props.put("buffer.memory", 33554432);

        producer = new KafkaProducer<>(props);
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("topic", this.topic);
        mustNotBeEmpty("server", this.server);
    }
}
