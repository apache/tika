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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.pipes.fetcher.RangeFetcher;
import org.apache.tika.server.core.resource.TikaResource;

/**
 * This class looks for &quot;fetcherName&quot; in the http header.  If it is not null
 * and not empty, this will return a new TikaInputStream from the fetch key
 * and the base path as set in the definition of the named fetcher.
 * As of Tika &gt; 2.5.0, the &quot;fetchKey&quot; is URL decoded.
 * <p>
 * Users may also specify the &quot;fetcherName&quote; and &quot;fetchKey&quot; in
 * query parameters with in the request.
 * <p>
 * <em>WARNING:</em> Unless you carefully lock down access to the server,
 * whoever has access to this service will have the read access of the server.
 * In short, anyone with access to this service could request and get
 * &quot;file:///etc/supersensitive_file_dont_read.txt&quot;.  Or, if your server has access
 * to your intranet, and you let the public hit this service, they will now
 * have access to your intranet.
 * See <a href="https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2015-3271">CVE-2015-3271</a>
 */
public class FetcherStreamFactory implements InputStreamFactory {

    private static final Logger LOG = LoggerFactory.getLogger(FetcherStreamFactory.class);

    private final FetcherManager fetcherManager;

    public FetcherStreamFactory(FetcherManager fetcherManager) {
        this.fetcherManager = fetcherManager;
    }

    /**
     * Tries to parse a long out of the value.  If the val is blank, it returns -1.
     * Throws {@link NumberFormatException}
     *
     * @param val
     * @return
     */
    private static long getLong(String val) {
        if (StringUtils.isBlank(val)) {
            return -1;
        }
        return Long.parseLong(val);
    }

    @Override
    public InputStream getInputStream(InputStream is, Metadata metadata, HttpHeaders httpHeaders, UriInfo uriInfo) throws IOException {
        MultivaluedMap params = (uriInfo == null) ? null : uriInfo.getQueryParameters();
        String fetcherName = getParam("fetcherName", httpHeaders, params);
        String fetchKey = getParam("fetchKey", httpHeaders, params);
        fetchKey = urlDecode(fetchKey);
        if (StringUtils.isBlank(fetchKey)) {
            fetchKey = getParam("fetchKeyLiteral", httpHeaders, params);
        }
        ParseContext parseContext = new ParseContext();
        TikaResource.fillParseContext(httpHeaders.getRequestHeaders(), metadata, parseContext);
        long fetchRangeStart = getLong(getParam("fetchRangeStart", httpHeaders, params));
        long fetchRangeEnd = getLong(getParam("fetchRangeEnd", httpHeaders, params));
        if (StringUtils.isBlank(fetcherName) != StringUtils.isBlank(fetchKey)) {
            throw new IOException("Must specify both a 'fetcherName' and a 'fetchKey'. I see: " + " fetcherName:" + fetcherName + " and fetchKey:" + fetchKey);
        }
        if (fetchRangeStart < 0 && fetchRangeEnd > -1) {
            throw new IllegalArgumentException("fetchRangeStart must be > -1 if a fetchRangeEnd " + "is specified");
        }

        if (fetchRangeStart > -1 && fetchRangeEnd < 0) {
            throw new IllegalArgumentException("fetchRangeEnd must be > -1 if a fetchRangeStart " + "is specified");
        }

        if (!StringUtils.isBlank(fetcherName)) {
            try {
                LOG.debug("going to fetch '{}' from fetcher: {}", fetchKey, fetcherName);
                Fetcher fetcher = fetcherManager.getFetcher(fetcherName);
                if (fetchRangeStart > -1 && fetchRangeEnd > -1 && !(fetcher instanceof RangeFetcher)) {
                    throw new IllegalArgumentException(
                            "Can't call a fetch with a range on a fetcher that" + " is not a RangeFetcher: name=" + fetcher.getName() + " class=" + fetcher.getClass());
                }
                return fetcher.fetch(fetchKey, metadata, parseContext);
            } catch (TikaException e) {
                throw new IOException(e);
            }
        }
        return is;
    }

    private String urlDecode(String fetchKey) {
        if (fetchKey == null) {
            return fetchKey;
        }
        try {
            return URLDecoder.decode(fetchKey, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            LOG.warn("couldn't decode fetch key", fetchKey);
        }
        return fetchKey;
    }

    private String getParam(String paramName, HttpHeaders httpHeaders, MultivaluedMap uriParams) {
        if (uriParams == null || !uriParams.containsKey(paramName)) {
            return httpHeaders.getHeaderString(paramName);
        }

        return (String) uriParams.getFirst(paramName);
    }
}
