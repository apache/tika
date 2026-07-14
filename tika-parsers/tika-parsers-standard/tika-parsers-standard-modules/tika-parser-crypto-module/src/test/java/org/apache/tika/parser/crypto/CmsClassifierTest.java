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
package org.apache.tika.parser.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * Verifies the ContentInfo content-type OID -&gt; smime-type mapping directly, including the
 * digested/encrypted branches for which real fixtures are impractical. A minimal
 * {@code ContentInfo{ contentType, content }} is enough: the classifier keys off the OID (the
 * signedData sub-shape, which needs a full message, is exercised by the fixtures in
 * {@link Pkcs7ParserTest}).
 */
public class CmsClassifierTest {

    private String classify(ASN1ObjectIdentifier contentType) throws Exception {
        byte[] der = new ContentInfo(contentType, new DEROctetString(new byte[]{1, 2, 3}))
                .getEncoded();
        try (TikaInputStream tis = TikaInputStream.get(der)) {
            MediaType type = CmsClassifier.classify(tis);
            return type == null ? null : type.toString();
        }
    }

    @Test
    public void testContentTypeMapping() throws Exception {
        assertEquals("application/pkcs7-mime; smime-type=enveloped-data",
                classify(PKCSObjectIdentifiers.envelopedData));
        assertEquals("application/pkcs7-mime; smime-type=digested-data",
                classify(PKCSObjectIdentifiers.digestedData));
        assertEquals("application/pkcs7-mime; smime-type=encrypted-data",
                classify(PKCSObjectIdentifiers.encryptedData));
        assertEquals("application/pkcs7-mime; smime-type=compressed-data",
                classify(PKCSObjectIdentifiers.id_ct_compressedData));
        // plain data (and any unrecognized OID) is not a routed CMS container
        assertNull(classify(PKCSObjectIdentifiers.data));
    }
}
