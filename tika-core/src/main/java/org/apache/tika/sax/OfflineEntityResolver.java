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
package org.apache.tika.sax;

import org.apache.commons.io.input.ClosedInputStream;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;

/**
 * Answers every external DTD or entity with an empty stream, and records what was asked
 * for on the document's metadata when a {@link ParseRecord} is in the context.
 */
public class OfflineEntityResolver implements EntityResolver {

    private final ParseContext context;

    public OfflineEntityResolver(ParseContext context) {
        this.context = context;
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        ParseRecord record = context == null ? null : context.get(ParseRecord.class);
        if (record != null) {
            record.addExternalReference(systemId != null ? systemId : publicId);
        }
        return new InputSource(new ClosedInputStream());
    }
}
