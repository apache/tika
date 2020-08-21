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

package org.apache.tika.batch.builders;

import java.util.Locale;
import java.util.Map;

import org.apache.tika.batch.DigestingAutoDetectParserFactory;
import org.apache.tika.batch.ParserFactory;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.digestutils.BouncyCastleDigester;
import org.apache.tika.parser.digestutils.CommonsDigester;
import org.apache.tika.util.ClassLoaderUtil;
import org.apache.tika.util.XMLDOMUtil;
import org.w3c.dom.Node;

public class AppParserFactoryBuilder implements IParserFactoryBuilder {

    @Override
    public ParserFactory build(Node node, Map<String, String> runtimeAttrs) {
        Map<String, String> localAttrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttrs);
        String className = localAttrs.get("class");
        ParserFactory pf = ClassLoaderUtil.buildClass(ParserFactory.class, className);

        if (localAttrs.containsKey("parseRecursively")) {
            String bString = localAttrs.get("parseRecursively").toLowerCase(Locale.ENGLISH);
            if (bString.equals("true")) {
                pf.setParseRecursively(true);
            } else if (bString.equals("false")) {
                pf.setParseRecursively(false);
            } else {
                throw new RuntimeException("parseRecursively must have value of \"true\" or \"false\": "+
                        bString);
            }
        }
        if (pf instanceof DigestingAutoDetectParserFactory) {
            DigestingParser.Digester d = buildDigester(localAttrs);
            ((DigestingAutoDetectParserFactory)pf).setDigester(d);
        }
        return pf;
    }

    private DigestingParser.Digester buildDigester(Map<String, String> localAttrs) {

        String readLimitString = localAttrs.get("digestMarkLimit");
        if (readLimitString == null) {
            throw new IllegalArgumentException("Must specify \"digestMarkLimit\" for "+
            "the DigestingAutoDetectParserFactory");
        }
        int readLimit = -1;

        try {
            readLimit = Integer.parseInt(readLimitString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter \"digestMarkLimit\" must be a parseable int: "+
            readLimitString);
        }
        String digestString = localAttrs.get("digest");
        try {
            return new CommonsDigester(readLimit, digestString);
        } catch (IllegalArgumentException commonsException) {
            try {
                return new BouncyCastleDigester(readLimit, digestString);
            } catch (IllegalArgumentException bcException) {
                throw new IllegalArgumentException("Tried both CommonsDigester ("+commonsException.getMessage()+
                        ") and BouncyCastleDigester ("+bcException.getMessage()+")", bcException);
            }
        }
    }
}
