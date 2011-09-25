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

import java.io.IOException;
import java.io.InputStream;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Decrypts the incoming document stream and delegates further parsing to
 * another parser instance. The decryption key and other settings as well
 * as the delegate parser are taken from the parsing context.
 *
 * @since Apache Tika 0.10
 */
public abstract class CryptoParser extends DelegatingParser {

    /** Serial version UID */
    private static final long serialVersionUID = -3507995752666557731L;

    private final String transformation;

    private final Provider provider;

    private final Set<MediaType> types;

    public CryptoParser(
            String transformation, Provider provider, Set<MediaType> types) {
        this.transformation = transformation;
        this.provider = provider;
        this.types = types;
    }

    public CryptoParser(
            String transformation, Set<MediaType> types) {
        this(transformation, null, types);
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return types;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try {
            Cipher cipher;
            if (provider != null) {
                cipher = Cipher.getInstance(transformation, provider);
            } else {
                cipher = Cipher.getInstance(transformation);
            }

            Key key = context.get(Key.class);
            if (key == null) {
                throw new EncryptedDocumentException("No decryption key provided");
            }

            AlgorithmParameters params = context.get(AlgorithmParameters.class);
            SecureRandom random = context.get(SecureRandom.class);
            if (params != null && random != null) {
                cipher.init(Cipher.DECRYPT_MODE, key, params, random);
            } else if (params != null) {
                cipher.init(Cipher.DECRYPT_MODE, key, params);
            } else if (random != null) {
                cipher.init(Cipher.DECRYPT_MODE, key, random);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key);
            }

            super.parse(
                    new CipherInputStream(stream, cipher),
                    handler, metadata, context);
        } catch (GeneralSecurityException e) {
            throw new TikaException("Unable to decrypt document stream", e);
        }
    }

}
