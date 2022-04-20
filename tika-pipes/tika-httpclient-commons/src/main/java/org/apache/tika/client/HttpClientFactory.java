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
package org.apache.tika.client;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * This holds quite a bit of state and is not thread safe.  Beware!
 * <p>
 * Also, we're currently ignoring the SSL checks.  Please open a ticket/PR
 * if you need robust SSL.
 */
public class HttpClientFactory {

    public static final String AES_ENV_VAR = "AES_KEY";

    private static final String CIPHER_TYPE = "AES/GCM/PKCS5Padding";
    private static final Logger LOG = LoggerFactory.getLogger(HttpClientFactory.class);

    private AES aes = null;

    private String proxyHost;
    private int proxyPort;
    private Set<String> allowedHostsForRedirect = new HashSet<>();
    private int maxConnectionsPerRoute = 1000;
    private int maxConnections = 2000;
    private int requestTimeout = 120000;
    private int connectTimeout = 120000;
    private int socketTimeout = 120000;
    private int keepAliveOnBadKeepAliveValueMs = 1000;
    private String userName;
    private String password;
    private String ntDomain;//if using nt credentials
    private String authScheme = "basic"; //ntlm or basic
    private boolean credentialsAESEncrypted = false;
    private boolean disableContentCompression = false;

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Set<String> getAllowedHostsForRedirect() {
        return allowedHostsForRedirect;
    }

    public void setAllowedHostsForRedirect(Set<String> allowedHostsForRedirect) {
        this.allowedHostsForRedirect = allowedHostsForRedirect;
    }

    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public int getKeepAliveOnBadKeepAliveValueMs() {
        return keepAliveOnBadKeepAliveValueMs;
    }

    public void setKeepAliveOnBadKeepAliveValueMs(int keepAliveOnBadKeepAliveValueMs) {
        this.keepAliveOnBadKeepAliveValueMs = keepAliveOnBadKeepAliveValueMs;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNtDomain() {
        return ntDomain;
    }

    public void setNtDomain(String ntDomain) {
        this.ntDomain = ntDomain;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    /**
     * only basic and ntlm are supported
     *
     * @param authScheme
     */
    public void setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
    }

    public void setCredentialsAESEncrypted(boolean credentialsAESEncrypted)
            throws TikaConfigException {
        if (credentialsAESEncrypted) {
            if (System.getenv(AES_ENV_VAR) == null) {
                throw new TikaConfigException(
                        "must specify aes key in the environment variable: " + AES_ENV_VAR);
            }
            if (credentialsAESEncrypted) {
                aes = new AES();
            }
        }
        this.credentialsAESEncrypted = credentialsAESEncrypted;
    }

    public void setDisableContentCompression(boolean disableContentCompression) {
        this.disableContentCompression = disableContentCompression;
    }

    public HttpClientFactory copy() throws TikaConfigException {
        HttpClientFactory cp = new HttpClientFactory();
        cp.setAllowedHostsForRedirect(new HashSet<>(allowedHostsForRedirect));
        cp.setAuthScheme(authScheme);
        cp.setConnectTimeout(connectTimeout);
        cp.setCredentialsAESEncrypted(credentialsAESEncrypted);
        cp.setDisableContentCompression(disableContentCompression);
        cp.setKeepAliveOnBadKeepAliveValueMs(keepAliveOnBadKeepAliveValueMs);
        cp.setMaxConnectionsPerRoute(maxConnectionsPerRoute);
        cp.setMaxConnections(maxConnections);
        cp.setNtDomain(ntDomain);
        cp.setPassword(password);
        cp.setProxyHost(proxyHost);
        cp.setProxyPort(proxyPort);
        cp.setRequestTimeout(requestTimeout);
        cp.setSocketTimeout(socketTimeout);
        return cp;
    }


    public HttpClient build() throws TikaConfigException {
        LOG.info("http client does not verify ssl at this point.  " +
                "If you need that, please open a ticket.");
        TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
        SSLContext sslContext = null;
        try {
            sslContext =
                    SSLContexts.custom().loadTrustMaterial(
                            null, acceptingTrustStrategy).build();
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new TikaConfigException("", e);
        }
        SSLConnectionSocketFactory sslsf =
                new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

        Registry<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create().register("https", sslsf)
                        .register("http", new PlainConnectionSocketFactory()).build();

        PoolingHttpClientConnectionManager manager =
                new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        manager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        manager.setMaxTotal(maxConnections);

        HttpClientBuilder builder = HttpClients.custom();
        if (disableContentCompression) {
            builder.disableContentCompression();
        }
        addCredentialsProvider(builder);
        addProxy(builder);
        return builder.setConnectionManager(manager)
                .setRedirectStrategy(new CustomRedirectStrategy(allowedHostsForRedirect))
                .setDefaultRequestConfig(RequestConfig.custom().setTargetPreferredAuthSchemes(
                        Arrays.asList(AuthSchemes.BASIC, AuthSchemes.NTLM))
                        .setConnectionRequestTimeout(requestTimeout)
                        .setConnectionRequestTimeout(connectTimeout).setSocketTimeout(socketTimeout)
                        .build()).setKeepAliveStrategy(getKeepAliveStrategy())
                .setSSLSocketFactory(sslsf).setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
    }

    private void addProxy(HttpClientBuilder builder) {
        if (!StringUtils.isBlank(proxyHost)) {
            HttpHost proxy = new HttpHost(proxyHost, proxyPort);
            DefaultProxyRoutePlanner proxyRoutePlanner = new DefaultProxyRoutePlanner(proxy);
            builder.setRoutePlanner(proxyRoutePlanner);
        }
    }

    private void addCredentialsProvider(HttpClientBuilder builder) throws TikaConfigException {

        if (StringUtils.isBlank(userName) && StringUtils.isBlank(password)) {
            return;
        }

        if ((StringUtils.isBlank(userName) && StringUtils.isBlank(password)) ||
                (StringUtils.isBlank(password) && StringUtils.isBlank(userName))) {
            throw new IllegalArgumentException(
                    "can't have one of 'username', " + "'password' null and the other not");
        }

        String finalUserName = decrypt(userName);
        String finalPassword = decrypt(password);
        String finalDomain = decrypt(ntDomain);
        CredentialsProvider provider = new BasicCredentialsProvider();
        Credentials credentials = null;
        Registry<AuthSchemeProvider> authSchemeRegistry = null;
        if (authScheme.equals("basic")) {
            credentials = new UsernamePasswordCredentials(finalUserName, finalPassword);
            authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                    .register("basic", new BasicSchemeFactory()).build();
        } else if (authScheme.equals("ntlm")) {
            if (StringUtils.isBlank(ntDomain)) {
                throw new IllegalArgumentException("must specify 'ntDomain'");
            }
            credentials = new NTCredentials(finalUserName, finalPassword,
                    null, finalDomain);
            authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                    .register("ntlm", new NTLMSchemeFactory()).build();
        }
        provider.setCredentials(AuthScope.ANY, credentials);
        builder.setDefaultCredentialsProvider(provider);
        builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);

    }

    private String decrypt(String encrypted) throws TikaConfigException {
        if (aes == null || encrypted == null) {
            return encrypted;
        }
        return aes.decrypt(encrypted);
    }

    //if there's a bad/missing keepalive strategy
    public ConnectionKeepAliveStrategy getKeepAliveStrategy() {
        return (response, context) -> {
            // Honor 'keep-alive' header
            HeaderElementIterator it = new BasicHeaderElementIterator(
                    response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement he = it.nextElement();
                String param = he.getName();
                String value = he.getValue();
                if (value != null && param != null &&
                        param.equalsIgnoreCase("timeout")) {
                    try {
                        return Long.parseLong(value) * 1000;
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            return keepAliveOnBadKeepAliveValueMs;
        };
    }

    private static class CustomRedirectStrategy extends LaxRedirectStrategy {

        private static final Logger LOG = LoggerFactory.getLogger(CustomRedirectStrategy.class);
        private final Set<String> allowedHosts;

        public CustomRedirectStrategy(Set<String> allowedHosts) {
            this.allowedHosts = allowedHosts;
        }

        @Override
        protected URI createLocationURI(final String location) throws ProtocolException {
            String newLocation = location;
            try {
                new URI(newLocation);
            } catch (final URISyntaxException ex) {
                LOG.warn("Redirected URL: [ " + newLocation + " ] will be encoded");
                try {
                    newLocation = URLEncoder.encode(newLocation, StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    LOG.warn("Well, that didn't work out... :(");
                }
            }
            return super.createLocationURI(newLocation);
        }

        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response,
                                    HttpContext context)
                throws ProtocolException {
            boolean isRedirectedSuper = super.isRedirected(request, response, context);
            if (isRedirectedSuper) {
                Header locationHeader = response.getFirstHeader("Location");
                String location = locationHeader.getValue();
                if (StringUtils.isBlank(location)) {
                    return false;
                }
                URI uri;
                try {
                    uri = new URI(location);
                } catch (URISyntaxException e) {
                    return true;
                }
                if (!allowedHosts.isEmpty() && !allowedHosts.contains(uri.getHost())) {
                    LOG.info("Not allowing external redirect. OriginalUrl={}," +
                            " RedirectLocation={}", request.getRequestLine().getUri(), location);
                    return false;
                }
            }
            return isRedirectedSuper;
        }
    }


    private static class AES {
        private final SecretKeySpec secretKey;

        private AES() throws TikaConfigException {
            secretKey = setKey(System.getenv(AES_ENV_VAR));
        }

        private static SecretKeySpec setKey(String myKey) throws TikaConfigException {
            //TODO: sha-256?
            try {
                byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
                MessageDigest sha = MessageDigest.getInstance("SHA-1");
                key = sha.digest(key);
                key = Arrays.copyOf(key, 16);
                return new SecretKeySpec(key, "AES");
            } catch (NoSuchAlgorithmException e) {
                throw new TikaConfigException("bad key", e);
            }
        }

        public String encrypt(String strToEncrypt) throws TikaConfigException {
            try {
                Cipher cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                return Base64.getEncoder().encodeToString(
                        cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException | InvalidKeyException |
                    NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
                throw new TikaConfigException("bad encryption info", e);
            }
        }

        public String decrypt(String strToDecrypt) throws TikaConfigException {
            try {
                Cipher cipher = Cipher.getInstance(CIPHER_TYPE);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)),
                        StandardCharsets.UTF_8);
            } catch (NoSuchAlgorithmException | InvalidKeyException |
                    NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
                throw new TikaConfigException("bad encryption info", e);
            }
        }
    }
}
