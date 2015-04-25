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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import static org.apache.tika.parser.external.ExternalParser.INPUT_FILE_TOKEN;

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

    private String command;

    public GDALParser() {
        setCommand("gdalinfo ${INPUT}");
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return this.command;
    }

    public String processCommand(InputStream stream) {
        TikaInputStream tis = (TikaInputStream) stream;
        String pCommand = this.command;
        try {
            if (this.command.contains(INPUT_FILE_TOKEN)) {
                pCommand = this.command.replace(INPUT_FILE_TOKEN, tis.getFile()
                        .getPath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return pCommand;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        Set<MediaType> types = new HashSet<MediaType>();
        types.add(MediaType.application("x-netcdf"));
        types.add(MediaType.application("vrt"));
        types.add(MediaType.image("geotiff"));
        types.add(MediaType.image("ntif"));
        types.add(MediaType.application("x-rpf-toc"));
        types.add(MediaType.application("x-ecrg-toc"));
        types.add(MediaType.image("hfa"));
        types.add(MediaType.image("sar-ceos"));
        types.add(MediaType.image("ceos"));
        types.add(MediaType.application("jaxa-pal-sar"));
        types.add(MediaType.application("gff"));
        types.add(MediaType.application("elas"));
        types.add(MediaType.application("aig"));
        types.add(MediaType.application("aaigrid"));
        types.add(MediaType.application("grass-ascii-grid"));
        types.add(MediaType.application("sdts-raster"));
        types.add(MediaType.application("dted"));
        types.add(MediaType.image("png"));
        types.add(MediaType.image("jpeg"));
        types.add(MediaType.image("raster"));
        types.add(MediaType.application("jdem"));
        types.add(MediaType.image("gif"));
        types.add(MediaType.image("big-gif"));
        types.add(MediaType.image("envisat"));
        types.add(MediaType.image("fits"));
        types.add(MediaType.application("fits"));
        types.add(MediaType.image("bsb"));
        types.add(MediaType.application("xpm"));
        types.add(MediaType.image("bmp"));
        types.add(MediaType.image("x-dimap"));
        types.add(MediaType.image("x-airsar"));
        types.add(MediaType.application("x-rs2"));
        types.add(MediaType.application("x-pcidsk"));
        types.add(MediaType.application("pcisdk"));
        types.add(MediaType.image("x-pcraster"));
        types.add(MediaType.image("ilwis"));
        types.add(MediaType.image("sgi"));
        types.add(MediaType.application("x-srtmhgt"));
        types.add(MediaType.application("leveller"));
        types.add(MediaType.application("terragen"));
        types.add(MediaType.application("x-gmt"));
        types.add(MediaType.application("x-isis3"));
        types.add(MediaType.application("x-isis2"));
        types.add(MediaType.application("x-pds"));
        types.add(MediaType.application("x-til"));
        types.add(MediaType.application("x-ers"));
        types.add(MediaType.application("x-l1b"));
        types.add(MediaType.image("fit"));
        types.add(MediaType.application("x-grib"));
        types.add(MediaType.image("jp2"));
        types.add(MediaType.application("x-rmf"));
        types.add(MediaType.application("x-wcs"));
        types.add(MediaType.application("x-wms"));
        types.add(MediaType.application("x-msgn"));
        types.add(MediaType.application("x-wms"));
        types.add(MediaType.application("x-wms"));
        types.add(MediaType.application("x-rst"));
        types.add(MediaType.application("x-ingr"));
        types.add(MediaType.application("x-gsag"));
        types.add(MediaType.application("x-gsbg"));
        types.add(MediaType.application("x-gs7bg"));
        types.add(MediaType.application("x-cosar"));
        types.add(MediaType.application("x-tsx"));
        types.add(MediaType.application("x-coasp"));
        types.add(MediaType.application("x-r"));
        types.add(MediaType.application("x-map"));
        types.add(MediaType.application("x-pnm"));
        types.add(MediaType.application("x-doq1"));
        types.add(MediaType.application("x-doq2"));
        types.add(MediaType.application("x-envi"));
        types.add(MediaType.application("x-envi-hdr"));
        types.add(MediaType.application("x-generic-bin"));
        types.add(MediaType.application("x-p-aux"));
        types.add(MediaType.image("x-mff"));
        types.add(MediaType.image("x-mff2"));
        types.add(MediaType.image("x-fujibas"));
        types.add(MediaType.application("x-gsc"));
        types.add(MediaType.application("x-fast"));
        types.add(MediaType.application("x-bt"));
        types.add(MediaType.application("x-lan"));
        types.add(MediaType.application("x-cpg"));
        types.add(MediaType.image("ida"));
        types.add(MediaType.application("x-ndf"));
        types.add(MediaType.image("eir"));
        types.add(MediaType.application("x-dipex"));
        types.add(MediaType.application("x-lcp"));
        types.add(MediaType.application("x-gtx"));
        types.add(MediaType.application("x-los-las"));
        types.add(MediaType.application("x-ntv2"));
        types.add(MediaType.application("x-ctable2"));
        types.add(MediaType.application("x-ace2"));
        types.add(MediaType.application("x-snodas"));
        types.add(MediaType.application("x-kro"));
        types.add(MediaType.image("arg"));
        types.add(MediaType.application("x-rik"));
        types.add(MediaType.application("x-usgs-dem"));
        types.add(MediaType.application("x-gxf"));
        types.add(MediaType.application("x-dods"));
        types.add(MediaType.application("x-http"));
        types.add(MediaType.application("x-bag"));
        types.add(MediaType.application("x-hdf"));
        types.add(MediaType.image("x-hdf5-image"));
        types.add(MediaType.application("x-nwt-grd"));
        types.add(MediaType.application("x-nwt-grc"));
        types.add(MediaType.image("adrg"));
        types.add(MediaType.image("x-srp"));
        types.add(MediaType.application("x-blx"));
        types.add(MediaType.application("x-rasterlite"));
        types.add(MediaType.application("x-epsilon"));
        types.add(MediaType.application("x-sdat"));
        types.add(MediaType.application("x-kml"));
        types.add(MediaType.application("x-xyz"));
        types.add(MediaType.application("x-geo-pdf"));
        types.add(MediaType.image("x-ozi"));
        types.add(MediaType.application("x-ctg"));
        types.add(MediaType.application("x-e00-grid"));
        types.add(MediaType.application("x-zmap"));
        types.add(MediaType.application("x-webp"));
        types.add(MediaType.application("x-ngs-geoid"));
        types.add(MediaType.application("x-mbtiles"));
        types.add(MediaType.application("x-ppi"));
        types.add(MediaType.application("x-cappi"));
        return types;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

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
        Map<Pattern, String> patterns = new HashMap<Pattern, String>();
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
        patterns.put(
                Pattern.compile(name + "\\:\\s*([A-Za-z0-9/ _\\-\\.]+)\\s*"),
                name);
    }

    private void addPatternWithIs(String name, Map<Pattern, String> patterns) {
        patterns.put(Pattern.compile(name + " is ([A-Za-z0-9\\.,\\s`']+)"),
                name);
    }

    private void addBoundingBoxPattern(String name,
                                       Map<Pattern, String> patterns) {
        patterns.put(
                Pattern.compile(name
                        + "\\s*\\(\\s*([0-9]+\\.[0-9]+\\s*,\\s*[0-9]+\\.[0-9]+\\s*)\\)\\s*"),
                name);
    }

    private void extractMetFromOutput(String output, Metadata met) {
        Scanner scanner = new Scanner(output);
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
                } else {
                    metVal.append("");
                }
            } else {
                metVal.append(line);
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
        } else return false;
    }

    private void applyPatternsToOutput(String output, Metadata metadata,
                                       Map<Pattern, String> metadataPatterns) {
        Scanner scanner = new Scanner(output);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            for (Pattern p : metadataPatterns.keySet()) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    if (metadataPatterns.get(p) != null
                            && !metadataPatterns.get(p).equals("")) {
                        metadata.add(metadataPatterns.get(p), m.group(1));
                    } else {
                        metadata.add(m.group(1), m.group(2));
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
                e.printStackTrace();
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

    private String extractOutput(InputStream stream) throws SAXException,
            IOException {
        StringBuilder sb = new StringBuilder();
        Reader reader = new InputStreamReader(stream, IOUtils.UTF_8);
        try {
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                sb.append(buffer, 0, n);
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }

    private void processOutput(ContentHandler handler, Metadata metadata,
                               String output) throws SAXException, IOException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        InputStream stream = new ByteArrayInputStream(output.getBytes(IOUtils.UTF_8));
        Reader reader = new InputStreamReader(stream, IOUtils.UTF_8);
        try {
            xhtml.startDocument();
            xhtml.startElement("p");
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                xhtml.characters(buffer, 0, n);
            }
            xhtml.endElement("p");

        } finally {
            reader.close();
            xhtml.endDocument();
        }

    }

}
