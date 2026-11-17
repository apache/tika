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

import static org.apache.tika.parser.external.ExternalParser.INPUT_FILE_TOKEN;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IsolatedInput;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

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
public class GDALParser implements Parser {

    private static final long serialVersionUID = -3869130527323941401L;
    private static final Logger LOG = LoggerFactory.getLogger(GDALParser.class);

    public static final long DEFAULT_TIMEOUT_MS = 60000;

    /**
     * The GDAL drivers gdalinfo may open a file with, unless configured otherwise.
     */
    public static final List<String> DEFAULT_ALLOWED_FORMATS =
            List.of("GTiff", "netCDF", "GRIB", "FITS", "NITF", "DTED");

    // the types this parser takes for each driver; a driver not listed here adds none
    private static final Map<String, Set<MediaType>> FORMAT_TYPES = Map.ofEntries(
            Map.entry("GTiff", Set.of(MediaType.image("geotiff"))),
            Map.entry("netCDF", Set.of(MediaType.application("x-netcdf"))),
            Map.entry("GRIB", Set.of(MediaType.application("x-grib"))),
            Map.entry("FITS", Set.of(MediaType.image("fits"), MediaType.application("fits"))),
            Map.entry("NITF", Set.of(MediaType.image("nitf"))),
            Map.entry("HFA", Set.of(MediaType.image("hfa"))),
            Map.entry("DTED", Set.of(MediaType.application("dted"))),
            Map.entry("HDF4", Set.of(MediaType.application("x-hdf"))),
            Map.entry("HDF5", Set.of(MediaType.application("x-hdf"),
                    MediaType.image("x-hdf5-image"))),
            Map.entry("ENVI", Set.of(MediaType.application("x-envi"),
                    MediaType.application("x-envi-hdr"))),
            Map.entry("PNG", Set.of(MediaType.image("png"))),
            Map.entry("JPEG", Set.of(MediaType.image("jpeg"))),
            Map.entry("GIF", Set.of(MediaType.image("gif"))),
            Map.entry("BMP", Set.of(MediaType.image("bmp"))),
            Map.entry("WMS", Set.of(MediaType.application("x-wms"))),
            Map.entry("WCS", Set.of(MediaType.application("x-wcs"))));

    private static final Pattern FORMAT_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private List<String> allowedFormats = DEFAULT_ALLOWED_FORMATS;

    private String command;

    private int maxStdErr = 100000;

    private int maxStdOut = 100000;

    private long timeoutMs = DEFAULT_TIMEOUT_MS;

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

    public List<String> getAllowedFormats() {
        return allowedFormats;
    }

    /**
     * GDAL driver names (as <code>gdalinfo --formats</code> prints them) that gdalinfo may
     * open a file with. This replaces {@link #DEFAULT_ALLOWED_FORMATS}, it does not add to
     * it.
     */
    @Field
    public void setAllowedFormats(List<String> allowedFormats) throws TikaConfigException {
        if (allowedFormats == null || allowedFormats.isEmpty()) {
            throw new TikaConfigException("allowedFormats must name at least one GDAL driver");
        }
        for (String format : allowedFormats) {
            if (!FORMAT_NAME.matcher(format).matches()) {
                throw new TikaConfigException("allowedFormats: not a GDAL driver name: "
                        + format);
            }
            if (!FORMAT_TYPES.containsKey(format)) {
                for (String known : FORMAT_TYPES.keySet()) {
                    if (known.equalsIgnoreCase(format)) {
                        throw new TikaConfigException(
                                "allowedFormats: " + format + " should be " + known);
                    }
                }
                LOG.warn("allowedFormats: {} adds no media type; the names with types are {}",
                        format, new TreeSet<>(FORMAT_TYPES.keySet()));
            }
        }
        this.allowedFormats = List.copyOf(allowedFormats);
    }

    // Split before substituting so whitespace in the input path stays inside one argument.
    // Each -if names a driver gdalinfo may try; with any present it tries no others.
    static String[] buildCommandLine(String command, String inputPath,
                                     List<String> allowedFormats) {
        String[] split = command.split("\\s+", -1);
        List<String> args = new ArrayList<>();
        // before the input argument, or after the program if no argument is the input alone
        int formatsAt = 1;
        for (int i = 0; i < split.length; i++) {
            if (i > 0 && formatsAt == 1 && split[i].equals(INPUT_FILE_TOKEN)) {
                formatsAt = i;
            }
            args.add(split[i].replace(INPUT_FILE_TOKEN, inputPath));
        }
        List<String> formats = new ArrayList<>();
        for (String format : allowedFormats) {
            formats.add("-if");
            formats.add(format);
        }
        args.addAll(formatsAt, formats);
        return args.toArray(new String[0]);
    }

    // VRT and GTI open any file whose header merely contains their XML, so a document of a
    // type routed here can name other files or URLs for gdalinfo to open.
    static void restrictEnvironment(Map<String, String> env) {
        env.merge("GDAL_SKIP", "VRT GTI", (operatorSkip, skip) -> operatorSkip + " " + skip);
        // the input is always a local file, so no /vsicurl/ name needs to open
        env.put("CPL_VSIL_CURL_ALLOWED_FILENAME", "");
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        Set<MediaType> types = new HashSet<>();
        for (String format : allowedFormats) {
            types.addAll(FORMAT_TYPES.getOrDefault(format, Set.of()));
        }
        return types;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        if (!ExternalParser.check("gdalinfo")) {
            return;
        }

        FileProcessResult result;
        long localTimeoutMillis = TikaTaskTimeout.getTimeoutMillis(context, timeoutMs);
        // under a fixed name, in a directory of its own
        try (TemporaryResources tmp = new TemporaryResources();
             IsolatedInput input = IsolatedInput.of(
                     TikaInputStream.get(stream, tmp, metadata).getPath(), metadata)) {
            String[] runCommand =
                    buildCommandLine(this.command, input.getPath().toString(), allowedFormats);
            ProcessBuilder pb = new ProcessBuilder(runCommand);
            restrictEnvironment(pb.environment());
            result = ProcessUtils.execute(pb, localTimeoutMillis, maxStdOut, maxStdErr);
        }

        metadata.set(ExternalProcess.IS_TIMEOUT, result.isTimeout());
        metadata.set(ExternalProcess.EXIT_VALUE, result.getExitValue());
        metadata.set(ExternalProcess.STD_OUT_LENGTH, result.getStdoutLength());
        metadata.set(ExternalProcess.STD_OUT_IS_TRUNCATED, result.isStdoutTruncated());
        metadata.set(ExternalProcess.STD_ERR_LENGTH, result.getStderrLength());
        metadata.set(ExternalProcess.STD_ERR_IS_TRUNCATED, result.isStderrTruncated());

        metadata.set(ExternalProcess.STD_OUT, result.getStdout());
        metadata.set(ExternalProcess.STD_ERR, result.getStderr());
        if (result.getExitValue() != 0) {
            throw new TikaException("GDALParser bad exit value " + result.getExitValue()
                    + " err msg: " + result.getStderr());
        }

        String output = result.getStdout();

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

    private void processOutput(ContentHandler handler, Metadata metadata, String output)
            throws SAXException, IOException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        try (Reader reader = new StringReader(output)) {
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

    @Field
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Field
    public void setMaxStdErr(int maxStdErr) {
        this.maxStdErr = maxStdErr;
    }

    @Field
    public void setMaxStdOut(int maxStdOut) {
        this.maxStdOut = maxStdOut;
    }

}
