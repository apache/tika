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

package org.apache.tika.parser.gdal;

//JDK imports

import java.io.InputStream;

//Tika imports
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.BodyContentHandler;

//Junit imports
import org.junit.Test;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * Test harness for the GDAL parser.
 */
public class TestGDALParser extends TikaTest {

    private boolean canRun() {
        String[] checkCmd = {"gdalinfo"};
        // If GDAL is not on the path, do not run the test.
        return ExternalParser.check(checkCmd);
    }

    @Test
    public void testParseBasicInfo() {
        assumeTrue(canRun());
        final String expectedDriver = "netCDF/Network Common Data Format";
        final String expectedUpperRight = "512.0,    0.0";
        final String expectedUpperLeft = "0.0,    0.0";
        final String expectedLowerLeft = "0.0,  512.0";
        final String expectedLowerRight = "512.0,  512.0";
        final String expectedCoordinateSystem = "`'";
        final String expectedSize = "512, 512";

        GDALParser parser = new GDALParser();
        InputStream stream = TestGDALParser.class
                .getResourceAsStream("/test-documents/sresa1b_ncar_ccsm3_0_run1_200001.nc");
        Metadata met = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        try {
            parser.parse(stream, handler, met, new ParseContext());
            assertNotNull(met);
            assertNotNull(met.get("Driver"));
            assertEquals(expectedDriver, met.get("Driver"));
            assertNotNull(met.get("Files"));
            assertNotNull(met.get("Coordinate System"));
            assertEquals(expectedCoordinateSystem, met.get("Coordinate System"));
            assertNotNull(met.get("Size"));
            assertEquals(expectedSize, met.get("Size"));
            assertNotNull(met.get("Upper Right"));
            assertEquals(expectedUpperRight, met.get("Upper Right"));
            assertNotNull(met.get("Upper Left"));
            assertEquals(expectedUpperLeft, met.get("Upper Left"));
            assertNotNull(met.get("Upper Right"));
            assertEquals(expectedLowerRight, met.get("Lower Right"));
            assertNotNull(met.get("Upper Right"));
            assertEquals(expectedLowerLeft, met.get("Lower Left"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testParseMetadata() {
        assumeTrue(canRun());
        final String expectedNcInst = "NCAR (National Center for Atmospheric Research, Boulder, CO, USA)";
        final String expectedModelNameEnglish = "NCAR CCSM";
        final String expectedProgramId = "Source file unknown Version unknown Date unknown";
        final String expectedProjectId = "IPCC Fourth Assessment";
        final String expectedRealization = "1";
        final String expectedTitle = "model output prepared for IPCC AR4";
        final String expectedSub8Name = "\":ua";
        final String expectedSub8Desc = "[1x17x128x256] eastward_wind (32-bit floating-point)";

        GDALParser parser = new GDALParser();
        InputStream stream = TestGDALParser.class
                .getResourceAsStream("/test-documents/sresa1b_ncar_ccsm3_0_run1_200001.nc");
        Metadata met = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        try {
            parser.parse(stream, handler, met, new ParseContext());
            assertNotNull(met);
            assertNotNull(met.get("NC_GLOBAL#institution"));
            assertEquals(expectedNcInst, met.get("NC_GLOBAL#institution"));
            assertNotNull(met.get("NC_GLOBAL#model_name_english"));
            assertEquals(expectedModelNameEnglish,
                    met.get("NC_GLOBAL#model_name_english"));
            assertNotNull(met.get("NC_GLOBAL#prg_ID"));
            assertEquals(expectedProgramId, met.get("NC_GLOBAL#prg_ID"));
            assertNotNull(met.get("NC_GLOBAL#prg_ID"));
            assertEquals(expectedProgramId, met.get("NC_GLOBAL#prg_ID"));
            assertNotNull(met.get("NC_GLOBAL#project_id"));
            assertEquals(expectedProjectId, met.get("NC_GLOBAL#project_id"));
            assertNotNull(met.get("NC_GLOBAL#realization"));
            assertEquals(expectedRealization, met.get("NC_GLOBAL#realization"));
            assertNotNull(met.get("NC_GLOBAL#title"));
            assertEquals(expectedTitle, met.get("NC_GLOBAL#title"));
            assertNotNull(met.get("SUBDATASET_8_NAME"));
            assertTrue(met.get("SUBDATASET_8_NAME").endsWith(expectedSub8Name));
            assertNotNull(met.get("SUBDATASET_8_DESC"));
            assertEquals(expectedSub8Desc, met.get("SUBDATASET_8_DESC"));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testParseFITS() {
        String fitsFilename = "/test-documents/WFPC2u5780205r_c0fx.fits";

        assumeTrue(canRun());
        // If the exit code is 1 (meaning FITS isn't supported by the installed version of gdalinfo, don't run this test.
        String[] fitsCommand = {"gdalinfo", TestGDALParser.class.getResource(fitsFilename).getPath()};
        assumeTrue(ExternalParser.check(fitsCommand, 1));

        String expectedAllgMin = "-7.319537E1";
        String expectedAtodcorr = "COMPLETE";
        String expectedAtodfile = "uref$dbu1405iu.r1h";
        String expectedCalVersion = "                        ";
        String expectedCalibDef = "1466";

        GDALParser parser = new GDALParser();
        InputStream stream = TestGDALParser.class
                .getResourceAsStream(fitsFilename);
        Metadata met = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        try {
            parser.parse(stream, handler, met, new ParseContext());
            assertNotNull(met);
            assertNotNull(met.get("ALLG-MIN"));
            assertEquals(expectedAllgMin, met.get("ALLG-MIN"));
            assertNotNull(met.get("ATODCORR"));
            assertEquals(expectedAtodcorr, met.get("ATODCORR"));
            assertNotNull(met.get("ATODFILE"));
            assertEquals(expectedAtodfile, met.get("ATODFILE"));
            assertNotNull(met.get("CAL_VER"));
            assertEquals(expectedCalVersion, met.get("CAL_VER"));
            assertNotNull(met.get("CALIBDEF"));
            assertEquals(expectedCalibDef, met.get("CALIBDEF"));

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
