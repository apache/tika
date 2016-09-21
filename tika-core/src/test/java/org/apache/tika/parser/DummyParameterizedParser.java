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
package org.apache.tika.parser;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import static org.osgi.util.measurement.Unit.s;

/**
 * A test Parsers to test {@link Field}
 * @since Apache Tika 1.14
 */
public class DummyParameterizedParser extends AbstractParser {

    private static Set<MediaType> MIMES = new HashSet<>();
    static {
        MIMES.add(MediaType.TEXT_PLAIN);
        MIMES.add(MediaType.TEXT_HTML);
        MIMES.add(MediaType.APPLICATION_XML);
        MIMES.add(MediaType.OCTET_STREAM);
    }

    @Field(name = "testparam") private String testParam = "init_string";
    @Field private short xshort = -2;
    @Field private int xint = -3;
    @Field private long xlong = -4;
    @Field(name = "xbigint") private BigInteger xbigInt;
    @Field private float xfloat = -5.0f;
    @Field private double xdouble = -6.0d;
    @Field private boolean xbool = true;
    @Field private URL xurl;
    @Field private URI xuri;

    @Field private String missing = "default";


    private String inner = "inner";
    private File xfile;

    public DummyParameterizedParser() {
        try {
            xurl = new URL("http://tika.apache.org/url");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
        try {
            xuri = new URI("http://tika.apache.org/uri");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
    @Field
    public void setXfile(File xfile){
        this.xfile = xfile;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {

        return MIMES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.add("testparam", testParam);
        metadata.add("xshort", xshort + "");
        metadata.add("xint", xint + "");
        metadata.add("xlong", xlong + "");
        metadata.add("xbigint", xbigInt + "");
        metadata.add("xfloat", xfloat + "");
        metadata.add("xdouble", xdouble + "");
        metadata.add("xbool", xbool + "");
        metadata.add("xuri", xuri + "");
        metadata.add("xurl", xurl + "");
        metadata.add("xfile", xfile + "");

        metadata.add("inner", inner + "");
        metadata.add("missing", missing + "");
    }
}
