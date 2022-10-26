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
package org.apache.tika.parser.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class JpegParserTest extends TikaTest {

    @Test
    public void testGeoPointMetadataFilter() throws Exception {

        TikaConfig config = new TikaConfig(getClass().getResource(
                "/configs/tika-config-geo-point-metadata-filter.xml"));
        Parser p = new AutoDetectParser(config);


        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1),
                1000, config.getMetadataFilter());
        try (InputStream is = getResourceAsStream("/test-documents/testJPEG_GEO_2.jpg")) {
            wrapper.parse(is, handler, new Metadata(), new ParseContext());
        }
        List<Metadata> metadataList = handler.getMetadataList();

        Metadata metadata = metadataList.get(0);
        // Geo tags should be there with 5dp, and not rounded
        assertEquals("51.575762", metadata.get(Metadata.LATITUDE));
        assertEquals("-1.567886", metadata.get(Metadata.LONGITUDE));
        assertEquals("51.575762,-1.567886", metadata.get("myGeoPoint"));

    }
}
