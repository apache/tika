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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.grib.GribParser;
import org.apache.tika.parser.hdf.HDFParser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Each parser opens only its own formats.
 */
public class NetcdfReadersTest extends TikaTest {

    private static final String NETCDF = "sresa1b_ncar_ccsm3_0_run1_200001.nc";
    private static final String GRIB = "gdas1.forecmwf.2014062612.grib2";

    @TempDir
    Path tmp;

    @Test
    public void testNetCDFParserReadsNetCDF() throws Exception {
        Path nc = copy(NETCDF, "a.nc");
        Metadata metadata = new Metadata();
        parse(new NetCDFParser(), TikaInputStream.get(nc), metadata);
        assertTrue(metadata.names().length > 5, metadata.toString());
        assertEquals(List.of("a.nc"), names(tmp));
    }

    @Test
    public void testNetCDFParserRefusesAnotherFormat() throws Exception {
        // a GRIB file
        Path grib = copy(GRIB, "a.nc");
        assertRefused(new NetCDFParser(), TikaInputStream.get(grib));
        assertEquals(List.of("a.nc"), names(tmp));
    }

    @Test
    public void testHDFParserRefusesAnotherFormat() throws Exception {
        assertRefused(new HDFParser(), getResourceAsStream("/test-documents/" + NETCDF));
    }

    @Test
    public void testGribParserRefusesAnotherFormat() throws Exception {
        assertRefused(new GribParser(), TikaInputStream.get(copy(NETCDF, "a.grib2")));
    }

    @Test
    public void testReaderFollowsTheSignature() {
        assertEquals(NetcdfReaders.NETCDF3,
                NetcdfReaders.forSignature(new byte[]{'C', 'D', 'F', 1, 0, 0, 0, 0}, 8));
        assertEquals(NetcdfReaders.GRIB2,
                NetcdfReaders.forSignature(new byte[]{'G', 'R', 'I', 'B', 0, 0, 0, 2}, 8));
        assertNull(NetcdfReaders.forSignature(new byte[]{'G', 'R', 'I', 'B', 0, 0, 0, 3}, 8));
        assertNull(NetcdfReaders.forSignature(new byte[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'}, 8));
        assertNull(NetcdfReaders.forSignature(new byte[]{'C', 'D', 'F', 1, 0, 0, 0, 0}, 4));
    }

    private void assertRefused(Parser parser, TikaInputStream stream) {
        TikaException e = assertThrows(TikaException.class,
                () -> parse(parser, stream, new Metadata()));
        assertEquals("not a format this parser reads", e.getMessage());
    }

    private void parse(Parser parser, TikaInputStream stream, Metadata metadata)
            throws Exception {
        try (TikaInputStream tis = stream) {
            parser.parse(tis, new BodyContentHandler(-1), metadata, new ParseContext());
        }
    }

    private Path copy(String resource, String name) throws Exception {
        Path target = tmp.resolve(name);
        try (InputStream is = getResourceAsStream("/test-documents/" + resource)) {
            Files.copy(is, target);
        }
        return target;
    }

    private static List<String> names(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }
}
