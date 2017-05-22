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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.RereadableInputStream;
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.teletrust.TeleTrusTObjectIdentifiers;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.cms.CMSTimeStampedData;
import org.bouncycastle.tsp.cms.CMSTimeStampedDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Tika parser for Time Stamped Data Envelope (application/timestamped-data)
 */
public class TSDParser extends AbstractParser {
    private static final long serialVersionUID = 3268158344501763323L;

    private static final Logger LOG = LoggerFactory.getLogger(TSDParser.class);

    private static final String TSD_LOOP_LABEL = "Time-Stamp-n.";
    private static final String TSD_DESCRIPTION_LABEL = "Description";
    private static final String TSD_DESCRIPTION_VALUE = "Time Stamped Data Envelope";
    private static final String TSD_PARSED_LABEL = "File-Parsed";
    private static final String TSD_PARSED_DATE = "File-Parsed-DateTime";
    private static final String TSD_DATE = "Time-Stamp-DateTime";
    private static final String TSD_DATE_FORMAT = "UTC";
    private static final String TSD_POLICY_ID = "Policy-Id";
    private static final String TSD_SERIAL_NUMBER = "Serial-Number";
    private static final String TSD_TSA = "TSA";
    private static final String TSD_ALGORITHM = "Algorithm";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("timestamped-data"));
    public static final String TSD_MIME_TYPE = "application/timestamped-data";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        //Try to parse TSD file
        try (RereadableInputStream ris = new RereadableInputStream(stream, 2048, true, true)) {
            Metadata TSDAndEmbeddedMetadata = new Metadata();

            List<TSDMetas> tsdMetasList = this.extractMetas(ris);
            this.buildMetas(tsdMetasList, metadata != null && metadata.size() > 0 ? TSDAndEmbeddedMetadata : metadata);

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            ris.rewind();

            //Try to parse embedded file in TSD file
            this.parseTSDContent(ris, handler, TSDAndEmbeddedMetadata, context);
            xhtml.endDocument();
        }
    }

    private List<TSDMetas> extractMetas(InputStream stream) {
        List<TSDMetas> tsdMetasList = new ArrayList<>();

        try {
            CMSTimeStampedData cmsTimeStampedData = new CMSTimeStampedData(stream);

            TimeStampToken[] tokens = cmsTimeStampedData.getTimeStampTokens();

            for (int i = 0; i < tokens.length; i++) {
                TSDMetas tsdMetas = new TSDMetas(true,
                        tokens[i].getTimeStampInfo().getGenTime(),
                        tokens[i].getTimeStampInfo().getPolicy().getId(),
                        tokens[i].getTimeStampInfo().getSerialNumber(),
                        tokens[i].getTimeStampInfo().getTsa(),
                        tokens[i].getTimeStampInfo().getHashAlgorithm().getAlgorithm().getId());

                tsdMetasList.add(tsdMetas);
            }

        } catch (Exception ex) {
            LOG.error("Error in TSDParser.buildMetas {}", ex.getMessage());
            tsdMetasList.clear();
        }

        return tsdMetasList;
    }

    private void buildMetas(List<TSDMetas> tsdMetasList, Metadata metadata) {
        Integer count = 1;

        for (TSDMetas tsdm : tsdMetasList) {
            metadata.set(TSD_LOOP_LABEL + count + " - " + Metadata.CONTENT_TYPE, TSD_MIME_TYPE);
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_DESCRIPTION_LABEL, TSD_DESCRIPTION_VALUE);
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_PARSED_LABEL, tsdm.getParseBuiltStr());
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_PARSED_DATE, tsdm.getParsedDateStr() + " " + TSD_DATE_FORMAT);
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_DATE, tsdm.getEmitDateStr() + " " + TSD_DATE_FORMAT);
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_POLICY_ID, tsdm.getPolicyId());
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_SERIAL_NUMBER, tsdm.getSerialNumberFormatted());
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_TSA, tsdm.getTsaStr());
            metadata.set(TSD_LOOP_LABEL + count + " - " + TSD_ALGORITHM, tsdm.getAlgorithmName());
            count++;
        }
    }

    private void parseTSDContent(InputStream stream, ContentHandler handler,
                                 Metadata metadata, ParseContext context) {

        CMSTimeStampedDataParser cmsTimeStampedDataParser = null;
        EmbeddedDocumentExtractor edx = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        if (edx.shouldParseEmbedded(metadata)) {
            try {
                cmsTimeStampedDataParser = new CMSTimeStampedDataParser(stream);

                try (InputStream is = TikaInputStream.get(cmsTimeStampedDataParser.getContent())) {
                    edx.parseEmbedded(is, handler, metadata, false);
                }

            } catch (Exception ex) {
                LOG.error("Error in TSDParser.parseTSDContent {}", ex.getMessage());
            } finally {
                this.closeCMSParser(cmsTimeStampedDataParser);
            }
        }
    }

    private void closeCMSParser(CMSTimeStampedDataParser cmsTimeStampedDataParser) {
        if (cmsTimeStampedDataParser != null) {
            try {
                cmsTimeStampedDataParser.close();
            } catch (Exception ex) {
                LOG.error("Error in TSDParser.closeCMSParser {}", ex.getMessage());
            }
        }
    }

    private class TSDMetas {
        private final String DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

        private Boolean parseBuilt = false;
        private Date emitDate = new Date();
        private String policyId = "";
        private BigInteger serialNumber = null;
        private GeneralName tsa = null;
        private String algorithm = "";
        private Date parsedDate = new Date();

        public TSDMetas() {
            super();
        }

        public TSDMetas(Boolean parseBuilt, Date emitDate, String policyId,
                        BigInteger serialNumber, GeneralName tsa, String algorithm) {
            super();
            this.parseBuilt = parseBuilt;
            this.emitDate = emitDate;
            this.policyId = policyId;
            this.serialNumber = serialNumber;
            this.tsa = tsa;
            this.algorithm = algorithm;
        }

        public Boolean getParseBuilt() {
            return parseBuilt;
        }

        public String getParseBuiltStr() {
            return String.valueOf(this.getParseBuilt() != null ?
                    this.getParseBuilt() : false);
        }

        public void setParseBuilt(Boolean parseBuilt) {
            this.parseBuilt = parseBuilt;
        }

        public Date getEmitDate() {
            return emitDate;
        }

        public void setEmitDate(Date emitDate) {
            this.emitDate = emitDate;
        }

        public String getEmitDateStr() {
            SimpleDateFormat sdf = new SimpleDateFormat(this.DATE_FORMAT, Locale.ROOT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.format(this.getEmitDate() != null ?
                    this.getEmitDate() : new Date());
        }

        public String getPolicyId() {
            return policyId;
        }

        public void setPolicyId(String policyId) {
            this.policyId = policyId;
        }

        public BigInteger getSerialNumber() {
            return serialNumber;
        }

        public String getSerialNumberFormatted() {
            String outsn = String.format(Locale.ROOT, "%12x", getSerialNumber());
            return outsn != null ? outsn.trim() : "" + getSerialNumber();
        }

        public void setSerialNumber(BigInteger serialNumber) {
            this.serialNumber = serialNumber;
        }

        public GeneralName getTsa() {
            return tsa;
        }

        public String getTsaStr() {
            return tsa + "";
        }

        public void setTSA(GeneralName tsa) {
            this.tsa = tsa;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getAlgorithmName() {
            return OIDNameMapper.getDigestAlgName(getAlgorithm());
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public Date getParsedDate() {
            return parsedDate;
        }

        public String getParsedDateStr() {
            SimpleDateFormat sdf = new SimpleDateFormat(this.DATE_FORMAT, Locale.ROOT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.format(this.getParsedDate() != null ?
                    this.getParsedDate() : new Date());
        }

        public void setParsedDate(Date parsedDate) {
            this.parsedDate = parsedDate;
        }

        @Override
        public String toString() {
            return "TSDMetas [parseBuilt=" + parseBuilt + ", emitDate="
                    + emitDate + ", policyId=" + policyId + ", serialNumber="
                    + serialNumber + ", tsa=" + tsa + ", algorithm="
                    + algorithm + ", parsedDate=" + parsedDate + "]";
        }
    }

    private static class OIDNameMapper {
        private static final Map<String, String> encryptionAlgs = new HashMap<>();
        private static final Map<String, String> digestAlgs = new HashMap<>();

        static {
            encryptionAlgs.put(X9ObjectIdentifiers.id_dsa_with_sha1.getId(), "DSA");
            encryptionAlgs.put(X9ObjectIdentifiers.id_dsa.getId(), "DSA");
            encryptionAlgs.put(OIWObjectIdentifiers.dsaWithSHA1.getId(), "DSA");
            encryptionAlgs.put(PKCSObjectIdentifiers.rsaEncryption.getId(), "RSA");
            encryptionAlgs.put(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId(), "RSA");
            encryptionAlgs.put(TeleTrusTObjectIdentifiers.teleTrusTRSAsignatureAlgorithm.getId(), "RSA");
            encryptionAlgs.put(X509ObjectIdentifiers.id_ea_rsa.getId(), "RSA");
            encryptionAlgs.put(CMSSignedDataGenerator.ENCRYPTION_ECDSA, "ECDSA");
            encryptionAlgs.put(X9ObjectIdentifiers.ecdsa_with_SHA2.getId(), "ECDSA");
            encryptionAlgs.put(X9ObjectIdentifiers.ecdsa_with_SHA224.getId(), "ECDSA");
            encryptionAlgs.put(X9ObjectIdentifiers.ecdsa_with_SHA256.getId(), "ECDSA");
            encryptionAlgs.put(X9ObjectIdentifiers.ecdsa_with_SHA384.getId(), "ECDSA");
            encryptionAlgs.put(X9ObjectIdentifiers.ecdsa_with_SHA512.getId(), "ECDSA");
            encryptionAlgs.put(CMSSignedDataGenerator.ENCRYPTION_RSA_PSS, "RSAandMGF1");
            encryptionAlgs.put(CryptoProObjectIdentifiers.gostR3410_94.getId(), "GOST3410");
            encryptionAlgs.put(CryptoProObjectIdentifiers.gostR3410_2001.getId(), "ECGOST3410");
            encryptionAlgs.put("1.3.6.1.4.1.5849.1.6.2", "ECGOST3410");
            encryptionAlgs.put("1.3.6.1.4.1.5849.1.1.5", "GOST3410");

            digestAlgs.put(PKCSObjectIdentifiers.md5.getId(), "MD5");
            digestAlgs.put(OIWObjectIdentifiers.idSHA1.getId(), "SHA1");
            digestAlgs.put(NISTObjectIdentifiers.id_sha224.getId(), "SHA224");
            digestAlgs.put(NISTObjectIdentifiers.id_sha256.getId(), "SHA256");
            digestAlgs.put(NISTObjectIdentifiers.id_sha384.getId(), "SHA384");
            digestAlgs.put(NISTObjectIdentifiers.id_sha512.getId(), "SHA512");
            digestAlgs.put(PKCSObjectIdentifiers.sha1WithRSAEncryption.getId(), "SHA1");
            digestAlgs.put(PKCSObjectIdentifiers.sha224WithRSAEncryption.getId(), "SHA224");
            digestAlgs.put(PKCSObjectIdentifiers.sha256WithRSAEncryption.getId(), "SHA256");
            digestAlgs.put(PKCSObjectIdentifiers.sha384WithRSAEncryption.getId(), "SHA384");
            digestAlgs.put(PKCSObjectIdentifiers.sha512WithRSAEncryption.getId(), "SHA512");
            digestAlgs.put(TeleTrusTObjectIdentifiers.ripemd128.getId(), "RIPEMD128");
            digestAlgs.put(TeleTrusTObjectIdentifiers.ripemd160.getId(), "RIPEMD160");
            digestAlgs.put(TeleTrusTObjectIdentifiers.ripemd256.getId(), "RIPEMD256");
            digestAlgs.put(CryptoProObjectIdentifiers.gostR3411.getId(), "GOST3411");
            digestAlgs.put("1.3.6.1.4.1.5849.1.2.1", "GOST3411");
        }

        public static String getDigestAlgName(String digestAlgOID) {
            String algName = digestAlgs.get(digestAlgOID);

            if (algName != null) {
                return algName;
            }

            return digestAlgOID;
        }

        public static String getEncryptionAlgName(String encryptionAlgOID) {
            String algName = encryptionAlgs.get(encryptionAlgOID);

            if (algName != null) {
                return algName;
            }

            return encryptionAlgOID;
        }

        public static MessageDigest getDigestInstance(String algorithm, String provider)
                throws NoSuchProviderException, NoSuchAlgorithmException {
            if (provider != null) {
                try {
                    return MessageDigest.getInstance(algorithm, provider);
                } catch (NoSuchAlgorithmException e) {
                    return MessageDigest.getInstance(algorithm); // try rolling back
                }
            } else {
                return MessageDigest.getInstance(algorithm);
            }
        }
    }
}
