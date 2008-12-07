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

import org.xml.sax.ContentHandler;

/**
 * Content handler decorator that prevents the {@link #startDocument()}
 * and {@link #endDocument()} events from reaching the decorated handler.
 * This is useful when you want to direct the results of parsing multiple
 * different XML documents into a single target document without worrying
 * about the {@link #startDocument()} and {@link #endDocument()} methods
 * being called more than once.
 */
public class EmbeddedContentHandler extends ContentHandlerDecorator {

    /**
     * Created a decorator that prevents the given handler from
     * receiving {@link #startDocument()} and {@link #endDocument()}
     * events.
     *
     * @param handler the content handler to be decorated
     */
    public EmbeddedContentHandler(ContentHandler handler) {
        super(handler);
    }

    /**
     * Ignored.
     */
    @Override
    public void startDocument() {
    }

    /**
     * Ignored.
     */
    @Override
    public void endDocument() {
    }

}
