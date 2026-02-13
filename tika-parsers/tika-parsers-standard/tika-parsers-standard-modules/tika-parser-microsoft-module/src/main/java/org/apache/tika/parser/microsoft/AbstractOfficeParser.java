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
package org.apache.tika.parser.microsoft;

import org.apache.poi.util.IOUtils;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Intermediate layer to set {@link OfficeParserConfig} uniformly.
 */
public abstract class AbstractOfficeParser implements Parser {

    private OfficeParserConfig defaultOfficeParserConfig = new OfficeParserConfig();

    /**
     * Checks to see if the user has specified an {@link OfficeParserConfig}.
     * If so, no changes are made; if not, one is added to the context.
     *
     * @param parseContext
     */
    public void configure(ParseContext parseContext) {
        OfficeParserConfig officeParserConfig =
                parseContext.get(OfficeParserConfig.class, defaultOfficeParserConfig);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
    }

    /**
     * Allows subclasses to set the default configuration during construction.
     *
     * @param config the configuration to use as default
     */
    protected void setDefaultOfficeParserConfig(OfficeParserConfig config) {
        this.defaultOfficeParserConfig = config;
    }

    public OfficeParserConfig getDefaultConfig() {
        return defaultOfficeParserConfig;
    }

    /**
     * <b>WARNING:</b> this sets a static variable in POI.
     * This allows users to override POI's protection of the allocation
     * of overly large byte arrays.  Use carefully; and please open up issues on
     * POI's bugzilla to bump values for specific records.
     *
     * If the value is &lt;&eq; 0, this value is ignored
     *
     * @param maxOverride
     */
    public void setByteArrayMaxOverride(int maxOverride) {
        if (maxOverride > 0) {
            IOUtils.setByteArrayMaxOverride(maxOverride);
            //required for serialization
            defaultOfficeParserConfig.setMaxOverride(maxOverride);
        }
    }

    public int getByteArrayMaxOverride() {
        return defaultOfficeParserConfig.getMaxOverride();
    }

}
