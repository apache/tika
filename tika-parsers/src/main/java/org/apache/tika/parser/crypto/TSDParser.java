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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
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
import org.bouncycastle.tsp.cms.CMSTimeStampedDataParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/*

Nome Formato:            Time Stamped Data Envelope

Mime Type:               application/timestamped-data

Estensione:              .tsd

*/
public class TSDParser extends AbstractParser {
    
    /**
     * 
     */
    private static final long serialVersionUID = 6139181424595882376L;
    
    private final String TSD_LOOP_LABEL = "Time-Stamp-n.";
    private final String TSD_DESCRIPTION_VALUE = "Time Stamped Data Envelope";
    private final String TSD_PARSED_LABEL = "File-Parsed";
    private final String TSD_PARSED_DATE = "File-Parsed-DateTime";
    private final String TSD_DATE = "Time-Stamp-DateTime";
    private final String TSD_DATE_FORMAT = "UTC";
    private final String TSD_POLICY_ID = "Policy-Id";
    private final String TSD_SERIAL_NUMBER = "Serial-Number";
    private final String TSD_TSA = "TSA";
    private final String TSD_ALGORITHM = "Algorithm";
    
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("timestamped-data"));
    public static final String TSD_MIME_TYPE = "application/timestamped-data";
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }
    
    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
                
        //Try to parse TSD File
        List<TSDMetas> tsdMetasList = this.buildMetas(stream);
        
        Integer count = 1;
        
        for(TSDMetas tsdm: tsdMetasList) {
            metadata.set(TSD_LOOP_LABEL + count + " - " + Metadata.CONTENT_TYPE, TSD_MIME_TYPE);
            metadata.set(TSD_LOOP_LABEL + count + " - " + Metadata.DESCRIPTION, TSD_DESCRIPTION_VALUE);
            metadata.set(TSD_LOOP_LABEL + count + " - " + this.TSD_PARSED_LABEL, tsdm.getParseBuiltStr());
            metadata.set(TSD_LOOP_LABEL + count + " - " + this.TSD_PARSED_DATE, tsdm.getParsedDateStr() + " " + this.TSD_DATE_FORMAT);
            metadata.set(TSD_LOOP_LABEL + count + " - " + this.TSD_DATE, tsdm.getEmitDateStr() + " " + this.TSD_DATE_FORMAT);
            metadata.set(TSD_LOOP_LABEL + count + " - " + this.TSD_POLICY_ID, tsdm.getPolicyId());
            metadata.set(TSD_LOOP_LABEL + count + " - " + this.TSD_SERIAL_NUMBER, tsdm.getSerialNumberFormatted());
            metadata.set(TSD_LOOP_LABEL + count + " - " + this.TSD_TSA, tsdm.getTSAstr());
            metadata.set(TSD_LOOP_LABEL + count + " - " + this.TSD_ALGORITHM, tsdm.getAlgorithmName());
            count++;
        }
                
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
        
     }
    
    private List<TSDMetas> buildMetas(InputStream stream) {
        
        List<TSDMetas> tsdMetasList = new ArrayList<TSDMetas>();
        
        try {
             
             CMSTimeStampedDataParser cmsTimeStampedData = new CMSTimeStampedDataParser(stream);
             
             TimeStampToken[] tokens = cmsTimeStampedData.getTimeStampTokens();
             
             for (int i=0; i < tokens.length; i++) {
                 
                 TSDMetas tsdMetas = new TSDMetas(true,
                                                   tokens[i].getTimeStampInfo().getGenTime(),
                                                   tokens[i].getTimeStampInfo().getPolicy().getId(),
                                                   tokens[i].getTimeStampInfo().getSerialNumber(),
                                                   tokens[i].getTimeStampInfo().getTsa(),
                                                   tokens[i].getTimeStampInfo().getHashAlgorithm().getAlgorithm().getId());
                 
                 tsdMetasList.add(tsdMetas);
             }
             
        } catch (Exception ex) {
              tsdMetasList = new ArrayList<TSDMetas>();
        }
        
        return tsdMetasList;
    }
    
    private class TSDMetas {
        
        private final String DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";
        
        private Boolean parseBuilt = false;
        private Date emitDate = new Date();
        private String policyId = "";
        private BigInteger serialNumber = null;
        private GeneralName TSA = null;
        private String algorithm = "";
        private Date parsedDate = new Date();
        
        public TSDMetas() {
            super();
        }
        
        public TSDMetas(Boolean parseBuilt, Date emitDate, String policyId,
                BigInteger serialNumber, GeneralName tSA, String algorithm) {
            super();
            this.parseBuilt = parseBuilt;
            this.emitDate = emitDate;
            this.policyId = policyId;
            this.serialNumber = serialNumber;
            this.TSA = tSA;
            this.algorithm = algorithm;
        }
        
        public Boolean getParseBuilt() {
            return parseBuilt;
        }
        
        public String getParseBuiltStr() {
            return String.valueOf(this.getParseBuilt()!=null ? 
                                  this.getParseBuilt(): false);
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
            return sdf.format(this.getEmitDate()!=null ? 
                              this.getEmitDate(): new Date());
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
            return outsn !=null ? outsn.trim() : "" + getSerialNumber();
        }
        
        public void setSerialNumber(BigInteger serialNumber) {
            this.serialNumber = serialNumber;
        }
        
        public GeneralName getTSA() {
            return TSA;
        }
        
        public String getTSAstr() {
            return TSA+"";
        }
        
        public void setTSA(GeneralName tSA) {
            TSA = tSA;
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
            return sdf.format(this.getParsedDate()!=null ? 
                              this.getParsedDate(): new Date());
        }
        
        public void setParsedDate(Date parsedDate) {
            this.parsedDate = parsedDate;
        }

        @Override
        public String toString() {
            return "TSDMetas [parseBuilt=" + parseBuilt + ", emitDate="
                    + emitDate + ", policyId=" + policyId + ", serialNumber="
                    + serialNumber + ", TSA=" + TSA + ", algorithm="
                    + algorithm + ", parsedDate=" + parsedDate + "]";
        }
    }
    
    private static class OIDNameMapper {
        
        private static final Map<String, String> encryptionAlgs = new HashMap<String, String>();
        private static final Map<String, String> digestAlgs = new HashMap<String, String>();
        
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
            digestAlgs.put(CryptoProObjectIdentifiers.gostR3411.getId(),  "GOST3411");
            digestAlgs.put("1.3.6.1.4.1.5849.1.2.1",  "GOST3411");
        }
        
        public static String getDigestAlgName(String digestAlgOID) {
            String algName = (String)digestAlgs.get(digestAlgOID);

            if (algName != null) {
                return algName;
            }
            
            return digestAlgOID;
        }
        
        public static String getEncryptionAlgName(String encryptionAlgOID) {
            String algName = (String)encryptionAlgs.get(encryptionAlgOID);

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
