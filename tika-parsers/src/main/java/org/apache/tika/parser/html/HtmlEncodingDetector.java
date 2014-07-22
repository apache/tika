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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.CharsetUtils;

/**
 * Character encoding detector for determining the character encoding of a
 * HTML document based on the potential charset parameter found in a
 * Content-Type http-equiv meta tag somewhere near the beginning. Especially
 * useful for determining the type among multiple closely related encodings
 * (ISO-8859-*) for which other types of encoding detection are unreliable.
 *
 * @since Apache Tika 1.2
 */
public class HtmlEncodingDetector implements EncodingDetector {

    // TIKA-357 - use bigger buffer for meta tag sniffing (was 4K)
    private static final int META_TAG_BUFFER_SIZE = 8192;

  
    private static final Pattern HTTP_META_PATTERN = Pattern.compile(
          "(?is)<\\s*meta\\s+([^<>]+)"
          );
    
    //this should match both the older:
    //<meta http-equiv="content-type" content="text/html; charset=xyz"/>
    //and 
    //html5 <meta charset="xyz">
    //See http://webdesign.about.com/od/metatags/qt/meta-charset.htm
    //for the noisiness that one might encounter in charset attrs.
    //Chose to go with strict ([-_:\\.a-z0-9]+) to match encodings
    //following http://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html
    //For a more general "not" matcher, try:
    //("(?is)charset\\s*=\\s*['\\\"]?\\s*([^<>\\s'\\\";]+)")
    private static final Pattern FLEXIBLE_CHARSET_ATTR_PATTERN = Pattern.compile(
          ("(?is)charset\\s*=\\s*(?:['\\\"]\\s*)?([-_:\\.a-z0-9]+)")
          );
    
    private static final Charset ASCII = Charset.forName("US-ASCII");

    public Charset detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return null;
        }

        // Read enough of the text stream to capture possible meta tags
        input.mark(META_TAG_BUFFER_SIZE);
        byte[] buffer = new byte[META_TAG_BUFFER_SIZE];
        int n = 0;
        int m = input.read(buffer);
        while (m != -1 && n < buffer.length) {
            n += m;
            m = input.read(buffer, n, buffer.length - n);
        }
        input.reset();

        // Interpret the head as ASCII and try to spot a meta tag with
        // a possible character encoding hint
        
        String head = ASCII.decode(ByteBuffer.wrap(buffer, 0, n)).toString();

        Matcher equiv = HTTP_META_PATTERN.matcher(head);
        Matcher charsetMatcher = FLEXIBLE_CHARSET_ATTR_PATTERN.matcher("");
        //iterate through meta tags
        while (equiv.find()) {
           String attrs = equiv.group(1);
           charsetMatcher.reset(attrs);
           //iterate through charset= and return the first match
           //that is valid
           while (charsetMatcher.find()){
              String candCharset = charsetMatcher.group(1);
              if (CharsetUtils.isSupported(candCharset)){
                 try{
                    return CharsetUtils.forName(candCharset);
                 } catch (Exception e){
                    //ignore
                 }
              }
           }
        }
        return null;
    }

}
