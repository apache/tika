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
package org.apache.tika.detect.crypto.dev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Null;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;

public class ASN1Dumper {

    public static void main(String[] args) throws Exception {
        Path p = Paths.get(args[0]);
        try (InputStream is = Files.newInputStream(p)) {
            ASN1InputStream asn1InputStream = new ASN1InputStream(is);
            ASN1Primitive root = asn1InputStream.readObject();
            handleNode(root, 0);
        }
    }

    private static void handleNode(ASN1Primitive primitive, int depth) throws IOException {
        if (primitive instanceof ASN1Sequence) {
            handleSequence((ASN1Sequence) primitive, depth);
        } else if (primitive instanceof ASN1TaggedObject) {
            handleTagged((ASN1TaggedObject) primitive, depth);
        } else if (primitive instanceof ASN1Integer) {
            System.out.println(d(depth) + "Integer: " + ((ASN1Integer)primitive).getValue().intValue());
        } else if (primitive instanceof ASN1OctetString) {
            ASN1OctetString octetString = (ASN1OctetString) primitive;
            try {
                ASN1Primitive newP = ASN1Primitive.fromByteArray(octetString.getOctets());
                handleNode(newP, depth);
            } catch (IOException e) {
                System.out.println(d(depth) + "FAILED: " + octetString.toString().substring(0, 10));

            }
        } else if (primitive instanceof ASN1ObjectIdentifier) {
            ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) primitive;
            System.out.println(d(depth) + "OID: " + oid.toString());

        } else if (primitive instanceof ASN1Set) {
            for (ASN1Encodable obj : ((ASN1Set)primitive)) {
                handleNode(obj.toASN1Primitive(), depth + 1);
            }
        } else if (primitive instanceof ASN1Null) {
            System.out.println(d(depth) + "NULL");
        } else if (primitive instanceof DERIA5String) {
            System.out.println(d(depth) + ((DERIA5String)primitive).getString());
        } else if (primitive instanceof DERPrintableString) {
            System.out.println(d(depth) + ((DERPrintableString)primitive).getString());
        } else if (primitive instanceof ASN1Boolean) {
            System.out.println(d(depth) + ((ASN1Boolean)primitive).toString());
        } else {
            System.out.println(d(depth) + "Not handling " + primitive.getClass());
        }
    }

    private static void handleSequence(ASN1Sequence seq, int depth) throws IOException {
        System.out.println(d(depth) + "seq size: " + seq.size());
        int i = 0;
        for (ASN1Encodable p : seq) {
            String s = p.toString();
            if (s.length() > 20) {
                s = s.substring(0, 20) + "...";
            }
//            System.out.println(d(depth) + "SEQUENCE " + i++ + " : " + s + " : " + p.getClass());
        }
  //      System.out.println(d(depth) + "handling children");
        for (ASN1Encodable p : seq) {
            handleNode(p.toASN1Primitive(), depth + 1);
        }

    }

    private static void handleTagged(ASN1TaggedObject tagged, int depth) throws IOException {
        System.out.println(d(depth) + "handling tagged " + tagged.getBaseObject().getClass());
        handleNode(tagged.getBaseObject().toASN1Primitive(), depth + 1);
    }


    private static String d(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0 ; i < depth; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }
}
