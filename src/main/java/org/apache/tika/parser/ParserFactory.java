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

import java.io.InputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.config.ParserConfig;
import org.apache.tika.exception.TikaException;

/**
 * Factory class. Build parser from xml config file.
 * 
 * 
 */
public class ParserFactory {

    static Logger logger = Logger.getRootLogger();



    public static Parser getParser(
            InputStream inputStream, String mimeType, TikaConfig tc)
            throws TikaException {

        // Verify that all passed parameters are (probably) valid.

        if (StringUtils.isBlank(mimeType)) {
            throw new TikaException("Mime type not specified.");
        }

        if (inputStream == null) {
            throw new TikaException("Input stream is null.");
        }

        if (tc == null) {
            throw new TikaException("Configuration object is null.");
        }

        ParserConfig pc = getParserConfig(mimeType, tc);
        if (pc == null) {
            throw new TikaException(
                    "Could not find parser config for mime type "
                    + mimeType + ".");
        }

        String className = pc.getParserClass();
        Parser parser = null;

        if (StringUtils.isBlank(className)) {
            throw new TikaException(
                    "Parser class name missing from ParserConfig.");
        }

        try {
            logger.info("Loading parser class = " + className
                    + " MimeType = " + mimeType);

            Class<?> parserClass = Class.forName(className);
            parser = (Parser) parserClass.newInstance();
            parser.setMimeType(mimeType);
            parser.setContents(pc.getContents());
            parser.setInputStream(inputStream);

        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage());
            throw new TikaException(e.getMessage());
        } catch (InstantiationException e) {
            logger.error(e.getMessage());
            throw new TikaException(e.getMessage());
        } catch (IllegalAccessException e) {
            logger.error(e.getMessage());
            throw new TikaException(e.getMessage());
        }

        return parser;
    }


    private static ParserConfig getParserConfig(String mimeType, TikaConfig tc)
            throws TikaException {

        ParserConfig pc = tc.getParserConfig(mimeType);

        if (pc == null) {
            String message =
                    "Could not find parser configuration for mime type "
                    + mimeType + ".";

            logger.error(message);
            throw new TikaException(message);
        }

        return pc;
    }
}
