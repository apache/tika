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

import org.w3c.dom.Node;

import org.apache.tika.batch.ParserFactory;
import org.apache.tika.util.ClassLoaderUtil;
import org.apache.tika.util.XMLDOMUtil;

public class ParserFactoryBuilder implements IParserFactoryBuilder {


    @Override
    public ParserFactory build(Node node, Map<String, String> runtimeAttrs) {
        Map<String, String> localAttrs = XMLDOMUtil.mapifyAttrs(node, runtimeAttrs);
        String className = localAttrs.get("class");
        ParserFactory pf = ClassLoaderUtil.buildClass(ParserFactory.class, className);

        if (localAttrs.containsKey("parseRecursively")) {
            String bString = localAttrs
                    .get("parseRecursively")
                    .toLowerCase(Locale.ENGLISH);
            if (bString.equals("true")) {
                pf.setParseRecursively(true);
            } else if (bString.equals("false")) {
                pf.setParseRecursively(false);
            } else {
                throw new RuntimeException("parseRecursively must have value of \"true\" or \"false\": " + bString);
            }
        }
        return pf;
    }
}
