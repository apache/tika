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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * This Parser is created to test runtime configuration to parser.
 * This parser simply copies parameters to metadata so that a test
 * suit can be developed to test that :
 * 1. Parameters were parsed from configuration file
 * 2. parameters were supplied to parser via configure(ctx) method
 * 3. parameters were available at parse
 *
 */
public class DummyConfigurableParser extends AbstractParser {

    private static Set<MediaType> MIMES = new HashSet<>();
    static {
        MIMES.add(MediaType.TEXT_PLAIN);
        MIMES.add(MediaType.TEXT_HTML);
        MIMES.add(MediaType.OCTET_STREAM);
    }

    private Map<String, String> params;
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MIMES;
    }

    @Override
    public void configure(ParseContext context) throws TikaException {
        super.configure(context);
        this.params = context.getParams();
        // initialize here
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        for (Map.Entry<String, String> entry : this.params.entrySet()) {
            metadata.add(entry.getKey(), entry.getValue());
        }
    }

}
