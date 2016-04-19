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
package org.apache.tika.parser.netcdf;

//JDK imports
import java.io.InputStream;

//TIKA imports
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import static org.apache.tika.TikaTest.assertContains;
import static org.junit.Assert.assertEquals;

/**
 * Test cases to exercise the {@link NetCDFParser}.
 */
public class NetCDFParserTest {

    @Test
    public void testParseGlobalMetadata() throws Exception {
        Parser parser = new NetCDFParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = NetCDFParser.class
                .getResourceAsStream("/test-documents/sresa1b_ncar_ccsm3_0_run1_200001.nc")) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals(metadata.get(TikaCoreProperties.TITLE),
                "model output prepared for IPCC AR4");
        assertEquals(metadata.get(Metadata.CONTACT), "ccsm@ucar.edu");
        assertEquals(metadata.get(Metadata.PROJECT_ID),
                "IPCC Fourth Assessment");
        assertEquals(metadata.get(Metadata.CONVENTIONS), "CF-1.0");
        assertEquals(metadata.get(Metadata.REALIZATION), "1");
        assertEquals(metadata.get(Metadata.EXPERIMENT_ID),
                "720 ppm stabilization experiment (SRESA1B)");
        assertEquals(metadata.get("File-Type-Description"), 
                "NetCDF-3/CDM");

        String content = handler.toString();
        assertContains("long_name = \"Surface area\"", content);
        assertContains("float area(lat=128, lon=256)", content);
        assertContains("float lat(lat=128)", content);
        assertContains("double lat_bnds(lat=128, bnds=2)", content);
        assertContains("double lon_bnds(lon=256, bnds=2)", content);
        


    }

}
