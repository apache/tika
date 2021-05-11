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

import java.io.IOException;
import java.io.Serializable;

import org.w3c.dom.Element;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

/**
 * Filters the metadata in place after the parse
 *
 * @since Apache Tika 1.25
 */
public abstract class MetadataFilter extends ConfigBase implements Serializable {

    /**
     * Loads the metadata filter from the config file if it exists, otherwise returns NoOpFilter
     * @param root
     * @return
     * @throws TikaConfigException
     * @throws IOException
     */
    public static MetadataFilter load(Element root, boolean allowMissing) throws TikaConfigException,
            IOException {
        try {
            return buildComposite("metadataFilters", CompositeMetadataFilter.class,
                    "metadataFilter", MetadataFilter.class, root);
        } catch (TikaConfigException e) {
            if (allowMissing && e.getMessage().contains("could not find metadataFilters")) {
                return new NoOpFilter();
            }
            throw e;
        }
    }

    public abstract void filter(Metadata metadata) throws TikaException;
}
