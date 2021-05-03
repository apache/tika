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
 * Tries multiple parsers in turn, until one succeeds.
 * <p>
 * Can optionally keep Metadata from failed parsers when
 * trying the next one, depending on the {@link AbstractMultipleParser.MetadataPolicy}
 * chosen.
 *
 * @since Apache Tika 1.18
 */
public class FallbackParser extends AbstractMultipleParser {
    /**
     * The different Metadata Policies we support (all)
     */
    public static final List<MetadataPolicy> allowedPolicies =
            Arrays.asList(MetadataPolicy.values());
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 5844409020977206167L;

    @SuppressWarnings("rawtypes")
    public FallbackParser(MediaTypeRegistry registry, Collection<? extends Parser> parsers,
                          Map<String, Param> params) {
        super(registry, parsers, params);
    }

    public FallbackParser(MediaTypeRegistry registry, MetadataPolicy policy,
                          Collection<? extends Parser> parsers) {
        super(registry, policy, parsers);
    }

    public FallbackParser(MediaTypeRegistry registry, MetadataPolicy policy, Parser... parsers) {
        super(registry, policy, parsers);
    }

    @Override
    protected boolean parserCompleted(Parser parser, Metadata metadata, ContentHandler handler,
                                      ParseContext context, Exception exception) {
        // If there was no exception, abort further parsers
        return exception != null;

        // Have the next parser tried
    }
}

