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
package org.apache.tika.metadata.filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * This class removes the entire metadata object if the
 * mime matches the mime filter.  The idea is that you might not want
 * to store/transmit metadata for images or specific file types.
 */
@TikaComponent
public class RemoveByMimeMetadataFilter extends MetadataFilter {

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        public List<String> mimes = new ArrayList<>();
    }

    private final Set<String> mimes;

    public RemoveByMimeMetadataFilter() {
        this(new HashSet<>());
    }

    public RemoveByMimeMetadataFilter(Set<String> mimes) {
        this.mimes = mimes;
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public RemoveByMimeMetadataFilter(Config config) {
        this.mimes = new HashSet<>(config.mimes);
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public RemoveByMimeMetadataFilter(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    @Override
    public void filter(List<Metadata> metadataList, ParseContext parseContext) throws TikaException {
        metadataList.removeIf(this::shouldRemove);
    }

    private boolean shouldRemove(Metadata metadata) {
        String mimeString = metadata.get(Metadata.CONTENT_TYPE);
        if (mimeString == null) {
            return false;
        }
        MediaType mt = MediaType.parse(mimeString);
        if (mt == null) {
            return false;
        }
        mimeString = mt.getBaseType().toString();

        return mimes.contains(mimeString);
    }

    /**
     * @param mimes list of mimes that will trigger complete removal of metadata
     */
    public void setMimes(List<String> mimes) {
        this.mimes.addAll(mimes);
    }

    public List<String> getMimes() {
        return new ArrayList<>(mimes);
    }
}
