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

package org.apache.tika.parser.gdal;

//JDK imports

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.tika.parser.external.ExternalParser.INPUT_FILE_TOKEN;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.XHTMLContentHandler;

//Tika imports
//SAX imports

/**
 * Wraps execution of the <a href="http//gdal.org/">Geospatial Data Abstraction
 * Library (GDAL)</a> <code>gdalinfo</code> tool used to extract geospatial
 * information out of hundreds of geo file formats.
 * <p/>
 * The parser requires the installation of GDAL and for <code>gdalinfo</code> to
 * be located on the path.
 * <p/>
 * Basic information (Size, Coordinate System, Bounding Box, Driver, and
 * resource info) are extracted as metadata, and the remaining metadata patterns
 * are extracted and added.
 * <p/>
 * The output of the command is available from the provided
 * {@link ContentHandler} in the
 * {@link #parse(InputStream, ContentHandler, Metadata, ParseContext)} method.
 */
public class GDALParser extends AbstractParser {

    private static final long serialVersionUID = -3869130527323941401L;
    private static final Logger LOG = LoggerFactory.getLogger(GDALParser.class);

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(MediaType.application("x-netcdf"), MediaType.application("vrt"),
                    MediaType.image("geotiff"), MediaType.image("nitf"),
                    MediaType.application("x-rpf-toc"), MediaType.application("x-ecrg-toc"),
                    MediaType.image("hfa"), MediaType.image("sar-ceos"), MediaType.image("ceos"),
                    MediaType.application("jaxa-pal-sar"), MediaType.application("gff"),
                    MediaType.application("elas"), MediaType.application("aig"),
                    MediaType.application("aaigrid"), MediaType.application("grass-ascii-grid"),
                    MediaType.application("sdts-raster"), MediaType.application("dted"),
                    MediaType.image("png"), MediaType.image("jpeg"), MediaType.image("raster"),
                    MediaType.application("jdem"), MediaType.image("gif"),
                    MediaType.image("big-gif"), MediaType.image("envisat"), MediaType.image("fits"),
                    MediaType.application("fits"), MediaType.image("bsb"),
                    MediaType.application("xpm"), MediaType.image("bmp"),
                    MediaType.image("x-dimap"), MediaType.image("x-airsar"),
                    MediaType.application("x-rs2"), MediaType.application("x-pcidsk"),
                    MediaType.application("pcisdk"), MediaType.image("x-pcraster"),
                    MediaType.image("ilwis"), MediaType.image("sgi"),
                    MediaType.application("x-srtmhgt"), MediaType.application("leveller"),
                    MediaType.application("terragen"), MediaType.application("x-gmt"),
                    MediaType.application("x-isis3"), MediaType.application("x-isis2"),
                    MediaType.application("x-pds"), MediaType.application("x-til"),
                    MediaType.application("x-ers"), MediaType.application("x-l1b"),
                    MediaType.image("fit"), MediaType.application("x-grib"), MediaType.image("jp2"),
                    MediaType.application("x-rmf"), MediaType.application("x-wcs"),
                    MediaType.application("x-wms"), MediaType.application("x-msgn"),
                    MediaType.application("x-wms"), MediaType.application("x-wms"),
                    MediaType.application("x-rst"), MediaType.application("x-ingr"),
                    MediaType.application("x-gsag"), MediaType.application("x-gsbg"),
                    MediaType.application("x-gs7bg"), MediaType.application("x-cosar"),
                    MediaType.application("x-tsx"), MediaType.application("x-coasp"),
                    MediaType.application("x-r"), MediaType.application("x-map"),
                    MediaType.application("x-pnm"), MediaType.application("x-doq1"),
                    MediaType.application("x-doq2"), MediaType.application("x-envi"),
                    MediaType.application("x-envi-hdr"), MediaType.application("x-generic-bin"),
                    MediaType.application("x-p-aux"), MediaType.image("x-mff"),
                    MediaType.image("x-mff2"), MediaType.image("x-fujibas"),
                    MediaType.application("x-gsc"), MediaType.application("x-fast"),
                    MediaType.application("x-bt"), MediaType.application("x-lan"),
                    MediaType.application("x-cpg"), MediaType.image("ida"),
                    MediaType.application("x-ndf"), MediaType.image("eir"),
                    MediaType.application("x-dipex"), MediaType.application("x-lcp"),
                    MediaType.application("x-gtx"), MediaType.application("x-los-las"),
                    MediaType.application("x-ntv2"), MediaType.application("x-ctable2"),
                    MediaType.application("x-ace2"), MediaType.application("x-snodas"),
                    MediaType.application("x-kro"), MediaType.image("arg"),
                    MediaType.application("x-rik"), MediaType.application("x-usgs-dem"),
                    MediaType.application("x-gxf"), MediaType.application("x-dods"),
                    MediaType.application("x-http"), MediaType.application("x-bag"),
                    MediaType.application("x-hdf"), MediaType.image("x-hdf5-image"),
                    MediaType.application("x-nwt-grd"), MediaType.application("x-nwt-grc"),
                    MediaType.image("adrg"), MediaType.image("x-srp"),
                    MediaType.application("x-blx"), MediaType.application("x-rasterlite"),
                    MediaType.application("x-epsilon"), MediaType.application("x-sdat"),
                    MediaType.application("x-kml"), MediaType.application("x-xyz"),
                    MediaType.application("x-geo-pdf"), MediaType.image("x-ozi"),
                    MediaType.application("x-ctg"), MediaType.application("x-e00-grid"),
                    MediaType.application("x-zmap"), MediaType.application("x-webp"),
                    MediaType.application("x-ngs-geoid"), MediaType.application("x-mbtiles"),
                    MediaType.application("x-ppi"), MediaType.application("x-cappi"))));


    private String command;

    public GDALParser() {
        setCommand("gdalinfo ${INPUT}");
    }

    public String getCommand() {
        return this.command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String processCommand(InputStream stream) {
        TikaInputStream tis = (TikaInputStream) stream;
        String pCommand = this.command;
        try {
            if (this.command.contains(INPUT_FILE_TOKEN)) {
                pCommand = this.command.replace(INPUT_FILE_TOKEN, tis.getFile().getPath());
            }
        } catch (Exception e) {
            LOG.warn("exception processing command", e);
        }

        return pCommand;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        if (!ExternalParser.check("gdalinfo")) {
            return;
        }

        // first set up and run GDAL
        // process the command
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);

        String runCommand = processCommand(tis);
        String output = execCommand(new String[]{runCommand});

        // now extract the actual metadata params
        // from the GDAL output in the content stream
        // to do this, we need to literally process the output
        // from the invoked command b/c we can't read metadata and
        // output text from the handler in ExternalParser
        // at the same time, so for now, we can't use the
        // ExternalParser to do this and I've had to bring some of
        // that functionality directly into this class
        // TODO: investigate a way to do both using ExternalParser

        extractMetFromOutput(output, metadata);
        applyPatternsToOutput(output, metadata, getPatterns());

        // make the content handler and provide output there
        // now that we have metadata
        processOutput(handler, metadata, output);
    }

    private Map<Pattern, String> getPatterns() {
        Map<Pattern, String> patterns = new HashMap<>();
        this.addPatternWithColon("Driver", patterns);
        this.addPatternWithColon("Files", patterns);
        this.addPatternWithIs("Size", patterns);
        this.addPatternWithIs("Coordinate System", patterns);
        this.addBoundingBoxPattern("Upper Left", patterns);
        this.addBoundingBoxPattern("Lower Left", patterns);
        this.addBoundingBoxPattern("Upper Right", patterns);
        this.addBoundingBoxPattern("Lower Right", patterns);
        return patterns;
    }

    private void addPatternWithColon(String name, Map<Pattern, String> patterns) {
        patterns.put(Pattern.compile(name + "\\:\\s*([A-Za-z0-9/ _\\-\\.]+)\\s*"), name);
    }

    private void addPatternWithIs(String name, Map<Pattern, String> patterns) {
        patterns.put(Pattern.compile(name + " is ([A-Za-z0-9\\.,\\s`']+)"), name);
    }

    private void addBoundingBoxPattern(String name, Map<Pattern, String> patterns) {
        patterns.put(Pattern.compile(
                name + "\\s*\\(\\s*([0-9]+\\.[0-9]+\\s*,\\s*[0-9]+\\.[0-9]+\\s*)\\)\\s*"), name);
    }

    private void extractMetFromOutput(String output, Metadata met) {
        try (Scanner scanner = new Scanner(output)) {
            String currentKey = null;
            String[] headings = {"Subdatasets", "Corner Coordinates"};
            StringBuilder metVal = new StringBuilder();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains("=") || hasHeadings(line, headings)) {
                    if (currentKey != null) {
                        // time to flush this key and met val
                        met.add(currentKey, metVal.toString());
                    }
                    metVal.setLength(0);

                    String[] lineToks = line.split("=");
                    currentKey = lineToks[0].trim();
                    if (lineToks.length == 2) {
                        metVal.append(lineToks[1]);
                    }
                } else {
                    metVal.append(line);
                }

            }
        }
    }

    private boolean hasHeadings(String line, String[] headings) {
        if (headings != null && headings.length > 0) {
            for (String heading : headings) {
                if (line.contains(heading)) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    private void applyPatternsToOutput(String output, Metadata metadata,
                                       Map<Pattern, String> metadataPatterns) {
        try (Scanner scanner = new Scanner(output)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                for (Pattern p : metadataPatterns.keySet()) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        if (metadataPatterns.get(p) != null && !metadataPatterns.get(p).equals("")) {
                            metadata.add(metadataPatterns.get(p), m.group(1));
                        } else {
                            metadata.add(m.group(1), m.group(2));
                        }
                    }
                }
            }
        }
    }

    private String execCommand(String[] cmd) throws IOException {
        // Execute
        Process process;
        String output = null;
        if (cmd.length == 1) {
            process = Runtime.getRuntime().exec(cmd[0]);
        } else {
            process = Runtime.getRuntime().exec(cmd);
        }

        try {
            InputStream out = process.getInputStream();

            try {
                output = extractOutput(out);
            } catch (Exception e) {
                LOG.warn("Exception extracting output", e);
                output = "";
            }

        } finally {
            try {
                process.waitFor();
            } catch (InterruptedException ignore) {
            }
        }
        return output;

    }

    private String extractOutput(InputStream stream) throws SAXException, IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new InputStreamReader(stream, UTF_8)) {
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                sb.append(buffer, 0, n);
            }
        }
        return sb.toString();
    }

    private void processOutput(ContentHandler handler, Metadata metadata, String output)
            throws SAXException, IOException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        InputStream stream = new ByteArrayInputStream(output.getBytes(UTF_8));
        try (Reader reader = new InputStreamReader(stream, UTF_8)) {
            xhtml.startDocument();
            xhtml.startElement("p");
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                xhtml.characters(buffer, 0, n);
            }
            xhtml.endElement("p");

        } finally {
            xhtml.endDocument();
        }

    }

}
