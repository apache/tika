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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.tika.config.LiusConfig;
import org.apache.tika.config.ParserConfig;
import org.apache.tika.exception.LiusException;
import org.apache.tika.utils.MimeTypesUtils;

import org.apache.log4j.Logger;

/**
 * Factory class. Build parser from xml config file.
 * 
 * @author Rida Benjelloun (ridabenjelloun@apache.org)
 */
public class ParserFactory {

    static Logger logger = Logger.getRootLogger();

    /**
     * Build parser from file and Lius config object
     */
    public static Parser getParser(File file, LiusConfig tc)
            throws IOException, LiusException {
        String mimeType = MimeTypesUtils.getMimeType(file);
        ParserConfig pc = tc.getParserConfig(mimeType);
        String className = pc.getParserClass();
        Parser parser = null;
        Class<?> parserClass = null;
        if (className != null) {
            try {
                logger.info("Loading parser class = " + className
                        + " MimeType = " + mimeType);

                parserClass = Class.forName(className);
                parser = (Parser) parserClass.newInstance();

            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage());

            } catch (InstantiationException e) {
                logger.error(e.getMessage());
            } catch (IllegalAccessException e) {
                logger.error(e.getMessage());
            }
            parser.setMimeType(mimeType);
            parser.configure(tc);
            parser.setInputStream(new FileInputStream(file));

        }

        return parser;
    }

    /**
     * Build parser from string file path and Lius config object
     */
    public static Parser getParser(String str, LiusConfig tc)
            throws IOException, LiusException {
        return getParser(new File(str), tc);
    }

    /**
     * Build parser from string file path and Lius config file path
     */
    public static Parser getParser(String str, String tcPath)
            throws IOException, LiusException {
        LiusConfig tc = LiusConfig.getInstance(tcPath);
        return getParser(new File(str), tc);
    }

    /**
     * Build parser from file and Lius config file path
     */
    public static Parser getParser(File file, String tcPath)
            throws IOException, LiusException {
        LiusConfig tc = LiusConfig.getInstance(tcPath);
        return getParser(file, tc);
    }

}
