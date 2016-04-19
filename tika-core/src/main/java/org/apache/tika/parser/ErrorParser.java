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
package org.apache.tika.parser;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;

/**
 * Dummy parser that always throws a {@link TikaException} without even
 * attempting to parse the given document stream. Useful as a sentinel parser
 * for unknown document types.
 */
public class ErrorParser extends AbstractParser {
    private static final long serialVersionUID = 7727423956957641824L;
    
    /**
     * Singleton instance of this class.
     */
    public static final ErrorParser INSTANCE = new ErrorParser();

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.emptySet();
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws TikaException {
        throw new TikaException("Parse error");
    }
}
