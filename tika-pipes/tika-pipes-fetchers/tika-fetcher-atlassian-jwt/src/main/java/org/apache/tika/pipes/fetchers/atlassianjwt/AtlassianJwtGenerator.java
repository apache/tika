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
package org.apache.tika.pipes.fetchers.atlassianjwt;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class AtlassianJwtGenerator {
    private final String sharedSecret;
    private final String issuer;
    private final String subject;
    private final int expiresInSeconds;

    public AtlassianJwtGenerator(String sharedSecret, String issuer, String subject, int expiresInSeconds) {
        this.sharedSecret = sharedSecret;
        this.issuer = issuer;
        this.subject = subject;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String generateJwt(String method, String url) throws JOSEException, URISyntaxException, NoSuchAlgorithmException {
        String qsh = generateQueryStringHash(method, url);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(expiresInSeconds, ChronoUnit.SECONDS)))
                .claim("qsh", qsh);
        
        // Only add subject if it's not null or empty
        if (subject != null && !subject.trim().isEmpty()) {
            claimsBuilder.subject(subject);
        }
        
        JWTClaimsSet claimsSet = claimsBuilder.build();

        JWSSigner signer = new MACSigner(sharedSecret.getBytes(StandardCharsets.UTF_8));
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        signedJWT.sign(signer);

        String jwt = signedJWT.serialize();
        
        return jwt;
    }

    private String generateQueryStringHash(String method, String url) throws URISyntaxException, NoSuchAlgorithmException {
        URI uri = new URI(url);
        String canonicalRequest = createCanonicalRequestString(method, uri);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        String qsh = hexString.toString();
        
        return qsh;
    }

    private String createCanonicalRequestString(String httpMethod, URI url) {
        String urlPath = url.getRawPath();
        if (urlPath == null) {
            urlPath = "/";
        }
        
        // Split on "?" and take first part
        String[] pathParts = urlPath.split("\\?");
        urlPath = pathParts[0];
        
        // Build path: ensure leading slash, trim trailing slashes, decode & with %26
        String path = "/" + urlPath.replaceAll("^/+", "").replaceAll("/+$", "").replace("&", "%26");
        if (path.equals("//")) {
            path = "/";
        }

        // Confluence paths are prefixed with "/wiki" however that prefix should not be used
        // for calculating the canonical so we strip it off in case it is present
        if (path.startsWith("/wiki")) {
            path = path.substring(5); // Remove "/wiki"
            if (path.isEmpty()) {
                path = "/";
            }
        }
        
        String canonicalQueryString = generateCanonicalQueryString(url.getQuery());
        String canonicalRequest = httpMethod.toUpperCase(Locale.ROOT) + "&" + path + "&" + canonicalQueryString;
        
        return canonicalRequest;
    }

    private String generateCanonicalQueryString(String query) {
        if (query == null || query.isEmpty()) {
            return "";
        }

        // Query parameters go into a map for uniqueness + further iteration
        Map<String, List<String>> queryParams = new HashMap<>();
        String[] params = query.split("&");
        
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : "";
            
            // Skip jwt param, unneeded but present
            if ("jwt".equals(key)) {
                continue;
            }
            
            queryParams.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        
        List<String> canonicalParams = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
            List<String> values = entry.getValue();
            
            // URL encode all values
            List<String> encodedValues = values.stream()
                    .map(v -> URLEncoder.encode(v, StandardCharsets.UTF_8))
                    .collect(Collectors.toList());
            
            // Query parameter values need to be sorted in alphabetical order
            Collections.sort(encodedValues);
            
            // Individual parameter values are comma separated
            String joinedValues = String.join(",", encodedValues);
            String pair = key + "=" + joinedValues;
            // Decode + -> %20
            pair = pair.replace("+", "%20");
            
            canonicalParams.add(pair);
        }
        
        // And the whole collection must be sorted
        // (https://developer.atlassian.com/cloud/bitbucket/query-string-hash/#sort-query-parameter-value-lists)
        Collections.sort(canonicalParams);

        // And finally rejoined to create the canonical query string
        return String.join("&", canonicalParams);
    }
}
