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
package org.apache.tika.parser.multiple;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.xml.sax.ContentHandler;

import org.apache.tika.config.Param;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Runs the input stream through all available parsers,
 * merging the metadata from them based on the
 * {@link AbstractMultipleParser.MetadataPolicy} chosen.
 * <p>
 * Warning - currently only one Parser should output
 * any Content to the {@link ContentHandler}, the rest
 * should only output {@link Metadata}. A solution to
 * multiple-content is still being worked on...
 *
 * @since Apache Tika 1.18
 */
public class SupplementingParser extends AbstractMultipleParser {
    /**
     * The different Metadata Policies we support (not discard)
     */
    public static final List<MetadataPolicy> allowedPolicies =
            Arrays.asList(MetadataPolicy.FIRST_WINS, MetadataPolicy.LAST_WINS,
                    MetadataPolicy.KEEP_ALL);
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 313179254565350994L;

    @SuppressWarnings("rawtypes")
    public SupplementingParser(MediaTypeRegistry registry, Collection<? extends Parser> parsers,
                               Map<String, Param> params) {
        super(registry, parsers, params);
    }

    public SupplementingParser(MediaTypeRegistry registry, MetadataPolicy policy,
                               Parser... parsers) {
        this(registry, policy, Arrays.asList(parsers));
    }

    public SupplementingParser(MediaTypeRegistry registry, MetadataPolicy policy,
                               Collection<? extends Parser> parsers) {
        super(registry, policy, parsers);

        // Ensure it's a supported policy
        if (!allowedPolicies.contains(policy)) {
            throw new IllegalArgumentException(
                    "Unsupported policy for SupplementingParser: " + policy);
        }
    }

    @Override
    protected boolean parserCompleted(Parser parser, Metadata metadata, ContentHandler handler,
                                      ParseContext context, Exception exception) {
        // If there was no exception, just carry on to the next
        if (exception == null) {
            return true;
        }

        // Have the next parser tried
        return true;
    }
}

