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

package org.apache.tika.parser.html;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;

import org.apache.tika.mime.MediaType;

/**
 * Not thread safe.  Create a separate util for each thread.
 */
public class DataURISchemeUtil {

    public static String UNSPECIFIED_MEDIA_TYPE = "text/plain;charset=US-ASCII";

    private static Pattern PARSE_PATTERN = Pattern.compile("(?s)data:([^,]*?)(base64)?,(.*)$");
    private static Pattern EXTRACT_PATTERN =
            Pattern.compile("(?s)data:([^,]*?)(base64)?,([^\"\']*)[\"\']");
    private final Matcher parseMatcher = PARSE_PATTERN.matcher("");
    private final Matcher extractMatcher = EXTRACT_PATTERN.matcher("");
    Base64 base64 = new Base64();

    public DataURIScheme parse(String string) throws DataURISchemeParseException {
        parseMatcher.reset(string);
        if (parseMatcher.find()) {
            return build(parseMatcher.group(1), parseMatcher.group(2), parseMatcher.group(3));
        }
        throw new DataURISchemeParseException("Couldn't find expected pattern");
    }

    private DataURIScheme build(String mediaTypeString, String isBase64, String dataString) {
        byte[] data = null;
        //strip out back slashes as you might have in css
        dataString = (dataString != null) ? dataString.replaceAll("\\\\", " ") : dataString;

        if (dataString == null || dataString.length() == 0) {
            data = new byte[0];
        } else if (isBase64 != null) {
            data = base64.decode(dataString);
        } else {
            //TODO: handle encodings
            MediaType mediaType = MediaType.parse(mediaTypeString);
            Charset charset = StandardCharsets.UTF_8;
            if (mediaType.hasParameters()) {
                String charsetName = mediaType.getParameters().get("charset");
                if (charsetName != null && Charset.isSupported(charsetName)) {
                    try {
                        charset = Charset.forName(charsetName);
                    } catch (IllegalCharsetNameException e) {
                        //swallow and default to UTF-8
                    }
                }
            }
            data = dataString.getBytes(charset);
        }
        return new DataURIScheme(mediaTypeString, (isBase64 != null), data);
    }

    /**
     * Extracts DataURISchemes from free text, as in javascript.
     *
     * @param string
     * @return list of extracted DataURISchemes
     */
    public List<DataURIScheme> extract(String string) {
        extractMatcher.reset(string);
        List<DataURIScheme> list = null;
        while (extractMatcher.find()) {
            DataURIScheme dataURIScheme = build(extractMatcher.group(1), extractMatcher.group(2),
                    extractMatcher.group(3));
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(dataURIScheme);
        }
        return (list == null) ? Collections.EMPTY_LIST : list;
    }

}
