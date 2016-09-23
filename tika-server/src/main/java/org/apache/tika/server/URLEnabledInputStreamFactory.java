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
package org.apache.tika.server;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.tika.io.TikaInputStream;

/**
 * This class looks for &quot;fileUrl&quot; in the http header.  If it is not null
 * and not empty, this will return a new TikaInputStream from the URL.
 * <p>
 * This is not meant to be used in place of a robust, responsible crawler.  Rather, this
 * is a convenience factory.
 * <p>
 * <em>WARNING:</em> Unless you carefully lock down access to the server,
 * whoever has access to this service will have the read access of the server.
 * In short, anyone with access to this service could request and get
 * &quot;file:///etc/supersensitive_file_dont_read.txt&quot;.  Or, if your server has access
 * to your intranet, and you let the public hit this service, they will now
 * have access to your intranet.
 * See <a href="https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2015-3271">CVE-2015-3271</a>
 *
 */
public class URLEnabledInputStreamFactory implements InputStreamFactory {

    @Override
    public InputStream getInputSteam(InputStream is, HttpHeaders httpHeaders) throws IOException {
        String fileUrl = httpHeaders.getHeaderString("fileUrl");
        if(fileUrl != null && !"".equals(fileUrl)){
            return TikaInputStream.get(new URL(fileUrl));
        }
        return is;
    }
}
