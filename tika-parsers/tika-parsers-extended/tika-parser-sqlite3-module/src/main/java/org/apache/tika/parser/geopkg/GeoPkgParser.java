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
package org.apache.tika.parser.geopkg;


import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.sqlite3.SQLite3Parser;

/**
 * Customization of sqlite parser to skip certain common blob columns.
 * <p>
 * The motivation is that "geom" and "data" columns are intrinsic to geopkg
 * and are not regular embedded files. Tika treats all blob columns as, potentially,
 * embedded files -- this can add dramatically to the time to parse geopkg
 * files, which might have hundreds of thousands of uninteresting blobs.
 * <p>
 * Users may modify which columns are ignored or turn off "ignoring"
 * of all solumns.
 * <p>
 * To add a column to the default "ignore blob columns" via tika-config.xml:
 *  <pre>{@code}
 *   <parsers>
 *     <parser class="org.apache.tika.parser.DefaultParser"/>
 *     <parser class="org.apache.tika.parser.geopkg.GeoPkgParser">
 *       <param name="ignoreBlobColumns" type="list">
 *         <string>geom</string>
 *         <string>data</string>
 *         <string>something</string>
 *       </param>
 *     </parser>
 *   </parsers>
 *   }</pre>
 * <p>
 *   Or use an empty list to parse all columns.
 */
public class GeoPkgParser extends SQLite3Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -752276948656079347L;

    private static final MediaType MEDIA_TYPE = MediaType.application("x-geopackage");

    private static final Set<MediaType> SUPPORTED_TYPES;


    static {
        SUPPORTED_TYPES = Collections.singleton(MEDIA_TYPE);
    }

    private static final Set<String> DEFAULT_IGNORE_BLOB_COLUMNS = Set.of("geom", "data");
    private Set<String> ignoreBlobColumns = new HashSet<>(DEFAULT_IGNORE_BLOB_COLUMNS);
    /**
     * Checks to see if class is available for org.sqlite.JDBC.
     * <p/>
     * If not, this class will return an EMPTY_SET for  getSupportedTypes()
     */
    public GeoPkgParser() {

    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        GeoPkgDBParser p = new GeoPkgDBParser(ignoreBlobColumns);
        p.parse(stream, handler, metadata, context);
    }

    @Field
    public void setIgnoreBlobColumns(List<String> ignoreBlobColumns) {
        this.ignoreBlobColumns.clear();
        this.ignoreBlobColumns.addAll(ignoreBlobColumns);
    }
    /**
     * No-op
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
    }
}
