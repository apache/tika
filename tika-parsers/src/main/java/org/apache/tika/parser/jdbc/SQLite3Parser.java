package org.apache.tika.parser.jdbc;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This is the main class for parsing SQLite3 files.  When {@link #parse} is called,
 * this creates a new {@link org.apache.tika.parser.jdbc.SQLite3DBParser}.
 * <p/>
 * Given potential conflicts of native libraries in web servers, users will
 * need to add org.xerial's sqlite-jdbc jar to the class path for this parser
 * to work.  For development and testing, this jar is specified in tika-parsers'
 * pom.xml, but it is currently set to "provided."
 * <p/>
 * Note that this family of jdbc parsers is designed to treat each CLOB and each BLOB
 * as an embedded document; i.e. it will recursively process documents that are stored
 * in a sqlite db as "bytes".
 * <p/>
 * If using a TikaInputStream, make sure to close it to delete the temp file
 * that has to be created.
 */
public class SQLite3Parser extends AbstractParser implements Initializable {
    private static volatile boolean HAS_WARNED = false;
    private static final Object[] LOCK = new Object[0];

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -752276948656079347L;

    private static final MediaType MEDIA_TYPE = MediaType.application("x-sqlite3");

    private static final Set<MediaType> SUPPORTED_TYPES;
    static {
        Set<MediaType> tmp;
        try {
            Class.forName(SQLite3DBParser.SQLITE_CLASS_NAME);
            tmp = Collections.singleton(MEDIA_TYPE);
        } catch (ClassNotFoundException e) {
            tmp = Collections.EMPTY_SET;
        }
        SUPPORTED_TYPES = Collections.unmodifiableSet(tmp);
    }
    /**
     * Checks to see if class is available for org.sqlite.JDBC.
     * <p/>
     * If not, this class will return an EMPTY_SET for  getSupportedTypes()
     */
    public SQLite3Parser() {

    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        SQLite3DBParser p = new SQLite3DBParser();
        p.parse(stream, handler, metadata, context);
    }

    /**
     * No-op
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        if (SUPPORTED_TYPES.size() == 0) {
            if (HAS_WARNED) {
                return;
            }
            synchronized (LOCK) {
                //check again while under the lock
                if (HAS_WARNED) {
                    return;
                }
                problemHandler.handleInitializableProblem("org.apache.tika.parser.SQLite3Parser",
                        "org.xerial's sqlite-jdbc is not loaded.\n" +
                                "Please provide the jar on your classpath to parse sqlite files.\n" +
                                "See tika-parsers/pom.xml for the correct version.");
                HAS_WARNED = true;
            }
        }
    }
}
