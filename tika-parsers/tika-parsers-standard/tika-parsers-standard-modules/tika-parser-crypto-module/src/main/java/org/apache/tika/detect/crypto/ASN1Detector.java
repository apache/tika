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
package org.apache.tika.detect.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DLTaggedObject;

import org.apache.tika.config.Field;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * This is a very limited asn1 detector that focuses on pkcs and timestamped-data (so far)
 */
public class ASN1Detector implements Detector {

    private static final String DATA_OID = "1.2.840.113549.1.7.1";

    private static final Map<String, String> ENVELOPED = Map.of("smime-type", "enveloped-data");
    private static final Map<String, String> SIGNED = Map.of("smime-type", "signed-data");
    private static final Map<String, String> CERTS_ONLY = Map.of("smime-type", "certs-only");
    private static final Map<String, String> COMPRESSED = Map.of("smime-type", "compressed-data");


    private static final long serialVersionUID = -8414458255467101503L;
    private static final MediaType PKCS12_MEDIA_TYPE = MediaType.application("x-pkcs12");
    private static final MediaType PKCS7_ENVELOPED = new MediaType("application", "pkcs7-mime", ENVELOPED);
    private static final MediaType PKCS7_SIGNED = new MediaType("application", "pkcs7-mime", SIGNED);
    private static final MediaType PKCS7_CERTS_ONLY = new MediaType("application", "pkcs7-mime", CERTS_ONLY);
    private static final MediaType PKCS7_COMPRESSED = new MediaType("application", "pkcs7-mime", COMPRESSED);
    private static final MediaType PKCS7_SIGNATURE_ONLY = MediaType.application("pkcs7-signature");

    //not pkcs7 at all, but shares magic with compressed pkcs7
    private static final MediaType TIME_STAMPED_DATA = MediaType.application("timestamped-data");

    private int markLimit = 1000000;

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return null;
        }
        try {
            input.mark(2);
            int b = input.read();
            if (b != 0x30) {
                return null;
            }
            b = input.read();
            if (b < 0x7A || b > 0x84) {
                return null;
            }
        } finally {
            input.reset();
        }
        PKCSFeatures pkcsFeatures = new PKCSFeatures();
        BoundedInputStream bis = new BoundedInputStream(markLimit, input);
        bis.mark(markLimit);
        try {
            ASN1InputStream asn1InputStream = new ASN1InputStream(bis);
            ASN1Primitive root = null;
            if ((root = asn1InputStream.readObject()) != null) {
                handleRootNode(root, pkcsFeatures);
                if (pkcsFeatures.primaryType == PKCSFeatures.PRIMARY_TYPE.TIME_STAMPED_DATA) {
                    return TIME_STAMPED_DATA;
                } else if (pkcsFeatures.looksLikePKCS12) {
                    return PKCS12_MEDIA_TYPE;
                } else if (pkcsFeatures.primaryType == PKCSFeatures.PRIMARY_TYPE.ENVELOPED_DATA) {
                    return PKCS7_ENVELOPED;
                } else if (pkcsFeatures.primaryType == PKCSFeatures.PRIMARY_TYPE.COMPRESSED) {
                    return PKCS7_COMPRESSED;
                } else if (pkcsFeatures.primaryType == PKCSFeatures.PRIMARY_TYPE.SIGNED_DATA) {
                    if (pkcsFeatures.hasData) {
                        return PKCS7_SIGNED;
                    } else if (pkcsFeatures.hasCerts) {
                        return PKCS7_CERTS_ONLY;
                    } else {
                        return PKCS7_SIGNATURE_ONLY;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            //swallow
        } finally {
            bis.reset();
        }
        return null;
    }

    private void handleRootNode(ASN1Primitive root, PKCSFeatures pkcsFeatures) throws IOException {
        String oid = null;
        ASN1TaggedObject taggedObject = null;
        if (!(root instanceof ASN1Sequence)) {
            return;
        }
        ASN1Sequence seq = (ASN1Sequence) root;
        //try for pkcs12
        if (seq.size() == 3) {
            tryPKCS12(seq, pkcsFeatures);
            if (pkcsFeatures.looksLikePKCS12) {
                return;
            }
        }
        for (ASN1Encodable c : ((ASN1Sequence) root)) {
            if (c instanceof ASN1ObjectIdentifier) {
                oid = ((ASN1ObjectIdentifier) c).toString();
            } else if (c instanceof ASN1TaggedObject) {
                taggedObject = (ASN1TaggedObject) c;
            }
        }
        PKCSFeatures.PRIMARY_TYPE type = PKCSFeatures.lookup(oid);
        pkcsFeatures.primaryType = type;
        if (type == PKCSFeatures.PRIMARY_TYPE.UNKNOWN) {
            return;
        } else if (type == PKCSFeatures.PRIMARY_TYPE.TIME_STAMPED_DATA) {
            return;
        }
        if (taggedObject != null) {
            handleNode(taggedObject, pkcsFeatures);
        }
    }

    private void tryPKCS12(ASN1Sequence seq, ASN1Detector.PKCSFeatures pkcsFeatures) {
        //This could much more rigorous -- see TIKA-3784

        //require version 3 as the first value
        ASN1Encodable obj0 = seq.getObjectAt(0);
        if (! (obj0 instanceof ASN1Integer)) {
            return;
        }
        if (((ASN1Integer)obj0).getValue().intValue() != 3) {
            return;
        }
        //require two sequences
        if (! (seq.getObjectAt(1) instanceof ASN1Sequence) ||
                ! (seq.getObjectAt(2) instanceof ASN1Sequence)) {
            return;
        }
        //first sequence must have a data type oid as its first element
        ASN1Sequence seq1 = (ASN1Sequence) seq.getObjectAt(1);
        if (seq1.size() < 2) {
            return;
        }
        if (! (seq1.getObjectAt(0) instanceof ASN1ObjectIdentifier)) {
            return;
        }
        if (! DATA_OID.equals(((ASN1ObjectIdentifier)seq1.getObjectAt(0)).getId())) {
            return;
        }
        //and a tagged object as its second
        //if you parse the tagged object and iterate through its children
        //you should eventually find oids starting with "1.2.840.113549.1.12.*"
        if (! (seq1.getObjectAt(1) instanceof DLTaggedObject)) {
            return;
        }
        pkcsFeatures.looksLikePKCS12 = true;
    }

    private void handleSequence(ASN1Sequence seq, PKCSFeatures pkcsFeatures) throws IOException {
        if (seq.size() == 0) {
            return;
        }
        if (isCert(seq)) {
            pkcsFeatures.hasCerts = true;
            return;
        }
        if (hasSignedData(seq)) {
            pkcsFeatures.hasData = true;
            return;
        }


    }

    private boolean isCert(ASN1Sequence seq) {
        if (seq.size() != 6) {
            return false;
        }
        //do more
        //e.g. check for sequence in seq.get(2) and make sure there's a data oid there
        return true;
    }

    private boolean hasSignedData(ASN1Sequence seq) {
        if (seq.size() != 5) {
            return false;
        }
        //data should be a sequence in position 2
        ASN1Encodable dataSequence = seq.getObjectAt(2);
        if (! (dataSequence instanceof ASN1Sequence)) {
            return false;
        }
        if (((ASN1Sequence) dataSequence).size() < 1) {
            return false;
        }
        ASN1Encodable obj0 = ((ASN1Sequence) dataSequence).getObjectAt(0);
        if (obj0 instanceof ASN1ObjectIdentifier) {
            if (DATA_OID.equals(((ASN1ObjectIdentifier) obj0).getId())) {
                //TODO -- check for null or actual data?
                if (((ASN1Sequence) dataSequence).size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleNode(ASN1Primitive primitive, PKCSFeatures pkcsFeatures) throws IOException {
        if (primitive instanceof ASN1Sequence) {
            handleSequence((ASN1Sequence) primitive, pkcsFeatures);
        } else if (primitive instanceof ASN1TaggedObject) {
            handleTagged((ASN1TaggedObject) primitive, pkcsFeatures);
        } else if (primitive instanceof ASN1OctetString) {
            ASN1OctetString octetString = (ASN1OctetString) primitive;
            try {
                ASN1Primitive newP = ASN1Primitive.fromByteArray(octetString.getOctets());
                handleNode(newP, pkcsFeatures);
            } catch (IOException e) {
                //swallow

            }
        } else if (primitive instanceof ASN1ObjectIdentifier) {
            ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) primitive;

        } else if (primitive instanceof ASN1Set) {
            for (ASN1Encodable obj : ((ASN1Set)primitive)) {
                handleNode(obj.toASN1Primitive(), pkcsFeatures);
            }
        }
    }

    private void handleTagged(ASN1TaggedObject tagged, PKCSFeatures pkcsFeatures) throws IOException {
        handleNode(tagged.getBaseObject().toASN1Primitive(), pkcsFeatures);
    }

    @Field
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }

    private static class PKCSFeatures {
        enum PRIMARY_TYPE {
            SIGNED_DATA("1.2.840.113549.1.7.2"), ENVELOPED_DATA("1.2.840.113549.1.7.3"),
            SIGNED_AND_ENVELOPED_DATA("1.2.840.113549.1.7.4"),
            DIGESTED_DATA("1.2.840.113549.1.7.5"),
            ENCRYPTED_DATA("1.2.840.113549.1.7.6"), COMPRESSED("1.2.840.113549.1.9.16.1.9"),
            TIME_STAMPED_DATA("1.2.840.113549.1.9.16.1.31"), UNKNOWN("UNKNOWN");
            private final String oid;

            PRIMARY_TYPE(String oid) {
                this.oid = oid;
            }
        }

        private static Map<String, PRIMARY_TYPE> TYPE_LOOKUP = new HashMap<>();
        static {
            for (PRIMARY_TYPE t : PRIMARY_TYPE.values()) {
                if (t == PRIMARY_TYPE.UNKNOWN) {
                    continue;
                }
                TYPE_LOOKUP.put(t.oid, t);
            }
        }
        private PRIMARY_TYPE primaryType = PRIMARY_TYPE.UNKNOWN;
        private boolean hasData;
        private boolean hasCerts;
        private boolean hasSignature;
        private boolean looksLikePKCS12;

        static PRIMARY_TYPE lookup(String oid) {
            if (TYPE_LOOKUP.containsKey(oid)) {
                return TYPE_LOOKUP.get(oid);
            }
            return PRIMARY_TYPE.UNKNOWN;
        }

        @Override
        public String toString() {
            return "PKCSFeatures{" + "primaryType=" + primaryType + ", hasData=" + hasData + ", hasCerts=" + hasCerts + ", hasSignature=" + hasSignature + ", hasPKCS12Oid=" +
                    looksLikePKCS12 + '}';
        }
    }
}
