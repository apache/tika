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

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies client certificates against the truststore and, unless turned off, their
 * revocation status, and logs one INFO line per rejected certificate: the client only sees
 * {@code certificate_unknown}.
 */
final class ClientCertificateTrustManager extends X509ExtendedTrustManager {

    private static final Logger LOG = LoggerFactory.getLogger(ClientCertificateTrustManager.class);

    private final X509ExtendedTrustManager delegate;

    private ClientCertificateTrustManager(X509ExtendedTrustManager delegate) {
        this.delegate = delegate;
    }

    static TrustManager[] create(TlsConfig tlsConfig) throws GeneralSecurityException, IOException {
        KeyStore trustStore = KeyStore.getInstance(tlsConfig.getTrustStoreType());
        try (InputStream in = Files.newInputStream(Paths.get(tlsConfig.getTrustStoreFile()))) {
            trustStore.load(in, tlsConfig.getTrustStorePassword().toCharArray());
        }
        PKIXBuilderParameters params = new PKIXBuilderParameters(trustStore, new X509CertSelector());
        if (tlsConfig.isCheckRevocation()) {
            // an explicit checker reads CRL distribution points and OCSP (AIA) on its own; the
            // implicit one CXF enabled needed com.sun.security.enableCRLDP and rejected every CA-issued cert
            params.addCertPathChecker((PKIXRevocationChecker) CertPathValidator.getInstance("PKIX").getRevocationChecker());
        } else {
            params.setRevocationEnabled(false);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(new CertPathTrustManagerParameters(params));
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager x509) {
                return new TrustManager[]{new ClientCertificateTrustManager(x509)};
            }
        }
        throw new GeneralSecurityException("no X509 trust manager for the truststore");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        try {
            delegate.checkClientTrusted(chain, authType, socket);
        } catch (CertificateException e) {
            throw rejected(chain, e);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        try {
            delegate.checkClientTrusted(chain, authType, engine);
        } catch (CertificateException e) {
            throw rejected(chain, e);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            delegate.checkClientTrusted(chain, authType);
        } catch (CertificateException e) {
            throw rejected(chain, e);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        delegate.checkServerTrusted(chain, authType, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        delegate.checkServerTrusted(chain, authType, engine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }

    private static CertificateException rejected(X509Certificate[] chain, CertificateException e) {
        if (LOG.isInfoEnabled()) {
            String subject = chain == null || chain.length == 0 ? "(none)" : chain[0].getSubjectX500Principal().getName();
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            // both strings come from the client's certificate
            LOG.info("client certificate rejected: {}: {}", clean(subject), clean(String.valueOf(cause.getMessage())));
        }
        return e;
    }

    private static String clean(String s) {
        return s.replaceAll("\\p{Cntrl}", "?");
    }
}
