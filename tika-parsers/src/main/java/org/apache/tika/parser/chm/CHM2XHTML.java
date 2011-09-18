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

package org.apache.tika.parser.chm;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.TextContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Utility class
 * 
 * 
 */
public class CHM2XHTML {

    public static void process(CHMDocumentInformation chmDoc,
            ContentHandler handler) throws TikaException {
        String text = chmDoc.getText();
        try {
            if (text.length() > 0) {
                handler.characters(text.toCharArray(), 0, text.length());
                new CHM2XHTML(chmDoc, handler);
            } else
                throw new TikaException("Could not extract content");

        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getText(CHMDocumentInformation chmDoc)
            throws TikaException {
        return chmDoc.getText();
    }

    protected TextContentHandler handler;

    public CHM2XHTML(CHMDocumentInformation chmDoc, ContentHandler handler) {
        this.handler = new TextContentHandler(handler);
    }
}
