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
package org.apache.tika.server.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

public class TlsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TlsConfig.class);

    /**
     * Default TLS protocols - only TLS 1.2 and 1.3 are enabled by default.
     * TLS 1.0 and 1.1 are considered insecure and should not be used.
     */
    public static final List<String> DEFAULT_PROTOCOLS = Arrays.asList("TLSv1.2", "TLSv1.3");

    /**
     * Default warning threshold for certificate expiration (30 days).
     */
    public static final int DEFAULT_CERT_EXPIRATION_WARNING_DAYS = 30;

    // deprecated 4.2.0, removed in 4.3.0: TLS is on whenever a tlsConfig block is present
    private Boolean active = null;
    private int certExpirationWarningDays = DEFAULT_CERT_EXPIRATION_WARNING_DAYS;
    private String keyStoreType = null;
    private String keyStorePassword = null;
    private String keyStoreFile = null;
    private String trustStoreType = null;
    private String trustStorePassword = null;
    private String trustStoreFile = null;

    private boolean clientAuthenticationWanted = false;
    private boolean clientAuthenticationRequired = false;

    // TLS protocol configuration
    private List<String> includedProtocols = DEFAULT_PROTOCOLS;
    private List<String> excludedProtocols = null;

    // Cipher suite configuration
    private List<String> includedCipherSuites = null;
    private List<String> excludedCipherSuites = null;

    /** @deprecated since 4.2.0: a {@code tlsConfig} block turns TLS on; remove the block to turn it off. */
    @Deprecated
    public void setActive(boolean active) {
        this.active = active;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreFile() {
        return keyStoreFile;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public void checkInitialization() throws TikaConfigException {
        if (Boolean.FALSE.equals(active)) {
            throw new TikaConfigException("tlsConfig.active=false: a tlsConfig block turns TLS on; "
                    + "remove the block to serve plain HTTP (\"active\" is deprecated and will be removed in 4.3.0)");
        }
        if (Boolean.TRUE.equals(active)) {
            LOG.warn("tlsConfig.active is deprecated and ignored: a tlsConfig block turns TLS on; remove \"active\" (it will be rejected in 4.3.0)");
        }
        {
            // Validate keystore configuration
            if (StringUtils.isBlank(keyStoreType)) {
                throw new TikaConfigException("must initialize keyStoreType");
            } else if (StringUtils.isBlank(keyStoreFile)) {
                throw new TikaConfigException("must initialize keyStoreFile");
            } else if (StringUtils.isBlank(keyStorePassword)) {
                throw new TikaConfigException("must initialize keyStorePassword");
            }

            // Validate keystore file exists
            File ksFile = new File(keyStoreFile);
            if (!ksFile.isFile()) {
                throw new TikaConfigException("keyStoreFile does not exist or is not a file: " + keyStoreFile);
            }

            // Validate truststore configuration
            if (hasTrustStore()) {
                if (StringUtils.isBlank(trustStoreType)) {
                    throw new TikaConfigException("must initialize trustStoreType if there's any trustStore info");
                } else if (StringUtils.isBlank(trustStoreFile)) {
                    throw new TikaConfigException("must initialize trustStoreFile if there's any trustStore info");
                } else if (StringUtils.isBlank(trustStorePassword)) {
                    throw new TikaConfigException("must initialize trustStorePassword if there's any trustStore info");
                }

                // Validate truststore file exists
                File tsFile = new File(trustStoreFile);
                if (!tsFile.isFile()) {
                    throw new TikaConfigException("trustStoreFile does not exist or is not a file: " + trustStoreFile);
                }
            }

            // Warn about partial truststore configuration
            checkPartialTrustStoreConfig();

            if (!hasTrustStore() && isClientAuthenticationRequired()) {
                throw new TikaConfigException("requiring client authentication, but no trust store has been specified");
            }
            if (!hasTrustStore() && isClientAuthenticationWanted()) {
                throw new TikaConfigException("clientAuthenticationWanted, but no trust store has been specified to verify client certificates against");
            }
            checkProtocolsAndCipherSuites();
        }
    }

    /**
     * Every included protocol or cipher pattern must match something this JVM supports, and
     * the lists together must leave something enabled; otherwise the server would start and
     * fail every handshake, or silently fall back to the JVM defaults. Exclusions that match
     * nothing are fine: a denylist written for an older JVM stays valid.
     */
    private void checkProtocolsAndCipherSuites() throws TikaConfigException {
        SSLParameters supported;
        try {
            supported = SSLContext.getDefault().getSupportedSSLParameters();
        } catch (NoSuchAlgorithmException e) {
            throw new TikaConfigException("cannot inspect this JVM's TLS support", e);
        }
        List<String> protocols = Arrays.asList(supported.getProtocols());
        Set<String> enabledProtocols = new LinkedHashSet<>(includedProtocols == null ? protocols : includedProtocols);
        for (String p : enabledProtocols) {
            if (!protocols.contains(p)) {
                throw new TikaConfigException("includedProtocols entry '" + p + "' is not supported by this JVM; supported: " + protocols);
            }
        }
        if (excludedProtocols != null) {
            excludedProtocols.forEach(enabledProtocols::remove);
        }
        if (enabledProtocols.isEmpty()) {
            throw new TikaConfigException("includedProtocols/excludedProtocols leave no TLS protocol enabled; supported: " + protocols);
        }
        List<String> ciphers = Arrays.asList(supported.getCipherSuites());
        List<String> enabledCiphers = new ArrayList<>();
        try {
            if (includedCipherSuites == null) {
                enabledCiphers.addAll(ciphers);
            } else {
                for (String pattern : includedCipherSuites) {
                    List<String> matched = ciphers.stream().filter(c -> c.matches(pattern)).toList();
                    if (matched.isEmpty()) {
                        throw new TikaConfigException("includedCipherSuites entry '" + pattern + "' matches no cipher suite supported by this JVM");
                    }
                    enabledCiphers.addAll(matched);
                }
            }
            if (excludedCipherSuites != null) {
                enabledCiphers.removeIf(c -> excludedCipherSuites.stream().anyMatch(c::matches));
            }
        } catch (PatternSyntaxException e) {
            throw new TikaConfigException("cipher suite pattern is not a valid regular expression: " + e.getMessage(), e);
        }
        if (enabledCiphers.isEmpty()) {
            throw new TikaConfigException("includedCipherSuites/excludedCipherSuites leave no cipher suite enabled");
        }
    }

    /**
     * Check for partial truststore configuration and throw an exception.
     * If only some truststore fields are set, this is likely a configuration error.
     */
    private void checkPartialTrustStoreConfig() throws TikaConfigException {
        boolean hasType = !StringUtils.isBlank(trustStoreType);
        boolean hasFile = !StringUtils.isBlank(trustStoreFile);
        boolean hasPassword = !StringUtils.isBlank(trustStorePassword);

        // If any field is set but not all, that's a configuration error
        int setCount = (hasType ? 1 : 0) + (hasFile ? 1 : 0) + (hasPassword ? 1 : 0);
        if (setCount > 0 && setCount < 3) {
            StringBuilder missing = new StringBuilder("Partial truststore configuration detected. Missing: ");
            if (!hasType) missing.append("trustStoreType ");
            if (!hasFile) missing.append("trustStoreFile ");
            if (!hasPassword) missing.append("trustStorePassword ");
            throw new TikaConfigException(missing.toString().trim());
        }
    }

    public boolean isClientAuthenticationWanted() {
        return clientAuthenticationWanted;
    }

    public void setClientAuthenticationWanted(boolean clientAuthenticationWanted) {
        this.clientAuthenticationWanted = clientAuthenticationWanted;
    }

    public boolean isClientAuthenticationRequired() {
        return clientAuthenticationRequired;
    }

    public void setClientAuthenticationRequired(boolean clientAuthenticationRequired) {
        this.clientAuthenticationRequired = clientAuthenticationRequired;
    }

    public List<String> getIncludedProtocols() {
        return includedProtocols;
    }

    public void setIncludedProtocols(List<String> includedProtocols) {
        this.includedProtocols = includedProtocols;
    }

    public List<String> getExcludedProtocols() {
        return excludedProtocols;
    }

    public void setExcludedProtocols(List<String> excludedProtocols) {
        this.excludedProtocols = excludedProtocols;
    }

    public List<String> getIncludedCipherSuites() {
        return includedCipherSuites;
    }

    public void setIncludedCipherSuites(List<String> includedCipherSuites) {
        this.includedCipherSuites = includedCipherSuites;
    }

    public List<String> getExcludedCipherSuites() {
        return excludedCipherSuites;
    }

    public void setExcludedCipherSuites(List<String> excludedCipherSuites) {
        this.excludedCipherSuites = excludedCipherSuites;
    }

    public int getCertExpirationWarningDays() {
        return certExpirationWarningDays;
    }

    public void setCertExpirationWarningDays(int certExpirationWarningDays) {
        this.certExpirationWarningDays = certExpirationWarningDays;
    }

    /**
     * Check certificate expiration dates and log warnings for certificates
     * expiring within the configured threshold.
     * <p>
     * This method should be called after {@link #checkInitialization()} to
     * warn about upcoming certificate expirations.
     */
    public void checkCertificateExpiration() {
        if (certExpirationWarningDays <= 0) {
            return;
        }

        Instant warningThreshold = Instant.now().plus(Duration.ofDays(certExpirationWarningDays));

        // Check keystore certificates
        if (!StringUtils.isBlank(keyStoreFile)) {
            checkKeystoreExpiration(keyStoreFile, keyStoreType, keyStorePassword, "keystore", warningThreshold);
        }

        // Check truststore certificates
        if (hasTrustStore()) {
            checkKeystoreExpiration(trustStoreFile, trustStoreType, trustStorePassword, "truststore", warningThreshold);
        }
    }

    private void checkKeystoreExpiration(String file, String type, String password,
                                          String storeName, Instant warningThreshold) {
        try (FileInputStream fis = new FileInputStream(file)) {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(fis, password.toCharArray());

            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = keyStore.getCertificate(alias);

                if (cert instanceof X509Certificate) {
                    X509Certificate x509 = (X509Certificate) cert;
                    Date notAfter = x509.getNotAfter();
                    Date notBefore = x509.getNotBefore();
                    Instant now = Instant.now();

                    // Check if already expired
                    if (notAfter.toInstant().isBefore(now)) {
                        LOG.error("Certificate '{}' in {} has EXPIRED on {}. " +
                                        "TLS connections will fail!",
                                alias, storeName, notAfter);
                    }
                    // Check if not yet valid
                    else if (notBefore.toInstant().isAfter(now)) {
                        LOG.error("Certificate '{}' in {} is not yet valid until {}. " +
                                        "TLS connections will fail!",
                                alias, storeName, notBefore);
                    }
                    // Check if expiring soon
                    else if (notAfter.toInstant().isBefore(warningThreshold)) {
                        long daysUntilExpiry = Duration.between(now, notAfter.toInstant()).toDays();
                        LOG.warn("Certificate '{}' in {} expires in {} days on {}. " +
                                        "Consider renewing soon.",
                                alias, storeName, daysUntilExpiry, notAfter);
                    } else {
                        LOG.debug("Certificate '{}' in {} is valid until {}",
                                alias, storeName, notAfter);
                    }
                }
            }
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            LOG.warn("Unable to check certificate expiration for {}: {}", storeName, e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "TlsConfig{" +
                "keyStoreType='" + keyStoreType + '\'' +
                ", keyStorePassword='" + maskPassword(keyStorePassword) + '\'' +
                ", keyStoreFile='" + keyStoreFile + '\'' +
                ", trustStoreType='" + trustStoreType + '\'' +
                ", trustStorePassword='" + maskPassword(trustStorePassword) + '\'' +
                ", trustStoreFile='" + trustStoreFile + '\'' +
                ", clientAuthenticationWanted=" + clientAuthenticationWanted +
                ", clientAuthenticationRequired=" + clientAuthenticationRequired +
                ", includedProtocols=" + includedProtocols +
                ", excludedProtocols=" + excludedProtocols +
                ", includedCipherSuites=" + includedCipherSuites +
                ", excludedCipherSuites=" + excludedCipherSuites +
                '}';
    }

    private static String maskPassword(String password) {
        return password == null ? "null" : "****";
    }

    public boolean hasTrustStore() {
        return !StringUtils.isBlank(trustStoreType) && !StringUtils.isBlank(trustStorePassword) && !StringUtils.isBlank(trustStoreFile);
    }
}
