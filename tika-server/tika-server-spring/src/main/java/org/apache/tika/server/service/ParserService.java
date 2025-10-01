/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package org.apache.tika.server.service;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.Parser;

public class ParserService {


    @SuppressWarnings("serial")
    public static Parser createParser() {
        final Parser parser = new AutoDetectParser(TIKA_CONFIG);

        if (DIGESTER != null) {
            boolean skipContainer = false;
            if (TIKA_CONFIG
                    .getAutoDetectParserConfig()
                    .getDigesterFactory() != null && TIKA_CONFIG
                    .getAutoDetectParserConfig()
                    .getDigesterFactory()
                    .isSkipContainerDocument()) {
                skipContainer = true;
            }
            return new DigestingParser(parser, DIGESTER, skipContainer);
        }
        return parser;
    }
}
