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

import java.io.OutputStream;
import java.nio.charset.Charset;

import org.xml.sax.ContentHandler;

import org.apache.tika.config.TikaComponent;

/**
 * A ContentHandlerFactory that creates UppercasingContentHandler instances.
 * This factory wraps a ToTextContentHandler with an uppercasing decorator
 * to convert all extracted text to uppercase.
 * <p>
 * Used for testing custom ContentHandlerFactory configurations in tika-pipes.
 */
@TikaComponent(contextKey = ContentHandlerFactory.class)
public class UppercasingContentHandlerFactory implements ContentHandlerFactory {

    private static final long serialVersionUID = 1L;

    @Override
    public ContentHandler getNewContentHandler() {
        return new UppercasingContentHandler(new ToTextContentHandler());
    }

    @Override
    public ContentHandler getNewContentHandler(OutputStream os, Charset charset) {
        try {
            return new UppercasingContentHandler(new ToTextContentHandler(os, charset.name()));
        } catch (java.io.UnsupportedEncodingException e) {
            // Should never happen since we're using a valid Charset
            throw new RuntimeException("Unexpected encoding error", e);
        }
    }
}
