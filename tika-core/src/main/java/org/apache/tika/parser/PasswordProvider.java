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

import org.apache.tika.metadata.Metadata;

/**
 * Interface for providing a password to a Parser for handling Encrypted
 *  and Password Protected Documents.
 * An implementation of this should be set on the {@link ParseContext}
 *  supplied to {@link Parser#parse(java.io.InputStream, org.xml.sax.ContentHandler, Metadata, ParseContext)}
 *  to provide a way to get the document password. 
 * An implementation of this interface defines some specific selection
 *  or lookup criteria, to be applied against the document metadata passed
 *  to the {@link #getPassword(Metadata)} method.
 *
 * @since Apache Tika 1.1
 */
public interface PasswordProvider {
    /**
     * Looks up the password for a document with the given metadata,
     * and returns it for the Parser. If no password is available
     * for the document, will return null.
     *
     * @param metadata document metadata
     * @return The document decryption password, or <code>null</code> if not known
     */
    String getPassword(Metadata metadata);
}
