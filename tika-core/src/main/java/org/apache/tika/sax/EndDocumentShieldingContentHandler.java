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
import org.xml.sax.SAXException;

/**
 * A wrapper around a {@link ContentHandler} which will ignore normal
 *  SAX calls to {@link #endDocument()}, and only fire them later.
 * This is typically used to ensure that we can output the metadata
 *  before ending the document
 */
public class EndDocumentShieldingContentHandler extends ContentHandlerDecorator {
    private boolean endDocumentCalled;

    /**
     * Creates a decorator for the given SAX event handler.
     *
     * @param handler SAX event handler to be decorated
     */
    public EndDocumentShieldingContentHandler(ContentHandler handler) {
       super(handler);
       endDocumentCalled = false;
    }

    @Override
    public void endDocument() throws SAXException {
        endDocumentCalled = true;
    }
    
    public void reallyEndDocument() throws SAXException {
       super.endDocument();
    }
    
    public boolean getEndDocumentWasCalled() {
       return endDocumentCalled;
    }
}
