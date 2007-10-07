/**
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
package org.apache.tika.parser;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tika.config.ParserConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.xml.XMLParser;

/**
 * Factory class. Build parser from xml config file.
 * 
 * 
 */
public class ParserFactory {

    static Logger logger = Logger.getRootLogger();

    public static Parser getParser(ParserConfig config) throws TikaException {
        String className = config.getParserClass();
        if (StringUtils.isBlank(className)) {
            throw new TikaException(
                    "Parser class name missing from ParserConfig.");
        }
        try {
            logger.info("Loading parser class = " + className);
            Parser parser = (Parser) Class.forName(className).newInstance();
            // FIXME: Replace with proper JavaBean dependency/config injection
            if (parser instanceof XMLParser) {
                ((XMLParser) parser).setNamespace(config.getNameSpace());
            }
            return new ParserPostProcessor(parser);
        } catch (Exception e) {
            logger.error("Unable to instantiate parser: " + className, e);
            throw new TikaException(e.getMessage());
        }
    }

}
