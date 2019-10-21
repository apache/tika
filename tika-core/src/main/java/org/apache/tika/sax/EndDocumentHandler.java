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

import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Arrays;

/**
 * Abstract handler that caches the character content
 * and then does special processing against all of the content
 * on
 */
public abstract class EndDocumentHandler extends ContentHandlerDecorator {


    protected final StringBuilder stringBuilder;
    protected final Metadata metadata;

    public EndDocumentHandler(ContentHandler contentHandler, Metadata metadata) {
        super(contentHandler);
        this.metadata = metadata;
        this.stringBuilder = new StringBuilder();
    }

    /**
     * The characters method is called whenever a Parser wants to pass raw...
     * characters to the ContentHandler. But, sometimes, phone numbers are split
     * accross different calls to characters, depending on the specific Parser
     * used. So, we simply add all characters to a StringBuilder and analyze it
     * once the document is finished.
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        try {
            String text = new String(Arrays.copyOfRange(ch, start, start + length));
            stringBuilder.append(text);
            super.characters(ch, start, length);
        } catch (SAXException e) {
            handleException(e);
        }
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        _endDocument();
    }

    protected abstract void _endDocument() throws SAXException;
}
