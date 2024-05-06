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

import java.io.IOException;
import java.io.InputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class NonValidatingContentHandler extends ContentHandlerDecorator {
    class ClosedInputStream extends InputStream {

        /**
         * Returns -1 to indicate that the stream is closed.
         *
         * @return always -1
         */
        @Override
        public int read() {
            return -1;
        }
    }

    public NonValidatingContentHandler(ContentHandler handler) {
        super(handler);
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        // NO-OP
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        // NO-OP
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        // NO-OP
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {
        return new InputSource(new ClosedInputStream());
    }
}
