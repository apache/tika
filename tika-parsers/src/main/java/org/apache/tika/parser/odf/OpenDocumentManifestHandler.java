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
package org.apache.tika.parser.odf;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.sax.ContentHandlerDecorator;

/**
 * For now, this only looks for any encryption-data elements.
 * If found this will throw an EncryptedDocumentException wrapped
 * in a SAXException.
 *
 * If desired, we can add to this to actually extract information
 * necessary for decryption.  Please open an issue or pull
 * request for this added functionality.
 *
 */
class OpenDocumentManifestHandler extends ContentHandlerDecorator {

    /**
     *
     * @param namespaceURI
     * @param localName
     * @param qName
     * @param attrs
     * @throws SAXException wrapping a {@link EncryptedDocumentException} if the file is encrypted
     */
    @Override
    public void startElement(
            String namespaceURI, String localName, String qName,
            Attributes attrs) throws SAXException {
        if (localName.equals("encryption-data")) {
            throw new SAXException(new EncryptedDocumentException());
        }
    }

}