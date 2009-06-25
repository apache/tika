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

import org.apache.tika.io.ClosedInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;

/**
 * Content handler decorator that always returns an empty stream from the
 * {@link #resolveEntity(String, String)} method to prevent potential
 * network or other external resources from being accessed by an XML parser.
 *
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-185">TIKA-185</a>
 */
public class OfflineContentHandler extends ContentHandlerDecorator {

    public OfflineContentHandler(ContentHandler handler) {
        super(handler);
    }

    /**
     * Returns an empty stream. This will make an XML parser silently
     * ignore any external entities.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        return new InputSource(new ClosedInputStream());
    }

}
