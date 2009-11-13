/**
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

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.utils.RegexUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser decorator that post-processes the results from a decorated parser.
 * The post-processing takes care of filling in the "fulltext", "summary",
 * and "outlinks" metadata entries based on the full text content returned by
 * the decorated parser.
 */
public class ParserPostProcessor extends ParserDecorator {

    /**
     * Creates a post-processing decorator for the given parser.
     *
     * @param parser the parser to be decorated
     */
    public ParserPostProcessor(Parser parser) {
        super(parser);
    }

    /**
     * Forwards the call to the delegated parser and post-processes the
     * results as described above.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        ContentHandler body = new BodyContentHandler();
        ContentHandler tee = new TeeContentHandler(handler, body);
        super.parse(stream, tee, metadata, context);

        String content = body.toString();
        metadata.set("fulltext", content);

        int length = Math.min(content.length(), 500);
        metadata.set("summary", content.substring(0, length));

        for (String link : RegexUtils.extractLinks(content)) {
            metadata.add("outlinks", link);
        }
    }

}
