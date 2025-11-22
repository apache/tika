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

import java.io.IOException;

import org.pf4j.Extension;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.EmitterFactory;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Factory for creating Kafka emitters.
 *
 * <p>Example JSON configuration:
 * <pre>
 * "emitters": {
 *   "kafka-emitter": {
 *     "my-kafka-emitter": {
 *       "topic": "my-topic",
 *       "bootstrapServers": "localhost:9092",
 *       "acks": "all",
 *       "lingerMs": 5000
 *     }
 *   }
 * }
 * </pre>
 */
@Extension
public class KafkaEmitterFactory implements EmitterFactory {

    private static final String NAME = "kafka-emitter";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Emitter buildExtension(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        return KafkaEmitter.build(extensionConfig);
    }
}
