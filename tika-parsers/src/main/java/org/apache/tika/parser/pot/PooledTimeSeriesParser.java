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

package org.apache.tika.parser.pot;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.mp4.MP4Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Uses the Pooled Time Series algorithm + command line tool, to
 * generate a numeric representation of the video suitable for
 * similarity searches.
 * <p>See https://wiki.apache.org/tika/PooledTimeSeriesParser for
 * more details and setup instructions.
 */
public class PooledTimeSeriesParser extends AbstractParser {

    private static final long serialVersionUID = -2855917932512164988L;

    static final boolean isAvailable = ExternalParser.check(
            new String[]{"pooled-time-series", "--help"}, -1);

    private static final Set<MediaType> SUPPORTED_TYPES =
            isAvailable ? Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(new MediaType[]{
                            MediaType.video("avi"), MediaType.video("mp4")
                    }))) : Collections.<MediaType>emptySet();
    ;
    // TODO: Add all supported video types

    private static final Logger LOG = LoggerFactory.getLogger(PooledTimeSeriesParser.class);

    /**
     * Returns the set of media types supported by this parser when used with the
     * given parse context.
     *
     * @param context parse context
     * @return immutable set of media types
     * @since Apache Tika 0.7
     */
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Parses a document stream into a sequence of XHTML SAX events. Fills in
     * related document metadata in the given metadata object.
     * <p>
     * The given document stream is consumed but not closed by this method. The
     * responsibility to close the stream remains on the caller.
     * <p>
     * Information about the parsing context can be passed in the context
     * parameter. See the parser implementations for the kinds of context
     * information they expect.
     *
     * @param stream   the document stream (input)
     * @param handler  handler for the XHTML SAX events (output)
     * @param metadata document metadata (input and output)
     * @param context  parse context
     * @throws IOException   if the document stream could not be read
     * @throws SAXException  if the SAX events could not be processed
     * @throws TikaException if the document could not be parsed
     * @since Apache Tika 0.5
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

        if (!isAvailable) {
            LOG.warn("PooledTimeSeries not installed!");
            return;
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
            File input = tikaStream.getFile();
            String cmdOutput = computePoT(input);
            try(InputStream ofStream = new FileInputStream(new File(
                    input.getAbsoluteFile() + ".of.txt"))) {
                try(InputStream ogStream = new FileInputStream(new File(
                        input.getAbsoluteFile() + ".hog.txt"))) {

                    extractHeaderOutput(ofStream, metadata, "of");
                    extractHeaderOutput(ogStream, metadata, "og");
                    xhtml.startDocument();
                    doExtract(ofStream, xhtml, "Histogram of Optical Flows (HOF)",
                            metadata.get("of_frames"), metadata.get("of_vecSize"));
                    doExtract(ogStream, xhtml, "Histogram of Oriented Gradients (HOG)",
                            metadata.get("og_frames"), metadata.get("og_vecSize"));
                    xhtml.endDocument();
                }
            }
            // Temporary workaround for TIKA-1445 - until we can specify
            //  composite parsers with strategies (eg Composite, Try In Turn),
            //  always send the image onwards to the regular parser to have
            //  the metadata for them extracted as well
            _TMP_VIDEO_METADATA_PARSER.parse(tikaStream, handler, metadata, context);

        } finally {
            tmp.dispose();
        }
    }

    // TIKA-1445 workaround parser
    private static Parser _TMP_VIDEO_METADATA_PARSER = new CompositeVideoParser();

    private static class CompositeVideoParser extends CompositeParser {
        private static final long serialVersionUID = -2398203965206381382L;
        private static List<Parser> videoParsers = Arrays.asList(new Parser[]{
                new MP4Parser()
        });

        CompositeVideoParser() {
            super(new MediaTypeRegistry(), videoParsers);
        }
    }

    private String computePoT(File input)
            throws IOException, TikaException {

        CommandLine cmdLine = new CommandLine("pooled-time-series");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        cmdLine.addArgument("-f");
        cmdLine.addArgument(input.getAbsolutePath());
        LOG.trace("Executing: {}", cmdLine);
        DefaultExecutor exec = new DefaultExecutor();
        exec.setExitValue(0);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
        exec.setWatchdog(watchdog);
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        exec.setStreamHandler(streamHandler);
        int exitValue = exec
                .execute(cmdLine, EnvironmentUtils.getProcEnvironment());
        return outputStream.toString("UTF-8");

    }

    /**
     * Reads the contents of the given stream and write it to the given XHTML
     * content handler. The stream is closed once fully processed.
     *
     * @param stream     Stream where is the result of ocr
     * @param xhtml      XHTML content handler
     * @param tableTitle The name of the matrix/table to display.
     * @param frames     Number of frames read from the video.
     * @param vecSize    Size of the OF or HOG vector.
     * @throws SAXException if the XHTML SAX events could not be handled
     * @throws IOException  if an input error occurred
     */
    private void doExtract(InputStream stream, XHTMLContentHandler xhtml,
                           String tableTitle, String frames, String vecSize) throws SAXException,
            IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                UTF_8))) {
            String line = null;
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "", "rows", "CDATA", frames);
            attributes.addAttribute("", "", "cols", "CDATA", vecSize);

            xhtml.startElement("h3");
            xhtml.characters(tableTitle);
            xhtml.endElement("h3");
            xhtml.startElement("table", attributes);
            while ((line = reader.readLine()) != null) {
                xhtml.startElement("tr");
                for (String val : line.split(" ")) {
                    xhtml.startElement("td");
                    xhtml.characters(val);
                    xhtml.endElement("td");
                }
                xhtml.endElement("tr");
            }
            xhtml.endElement("table");
        }
    }

    private void extractHeaderOutput(InputStream stream, Metadata metadata,
                                     String prefix) throws IOException {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                UTF_8))) {
            String line = reader.readLine();
            String[] firstLine = line.split(" ");
            String frames = firstLine[0];
            String vecSize = firstLine[1];

            if (prefix == null) {
                prefix = "";
            }
            metadata.add(prefix + "_frames", frames);
            metadata.add(prefix + "_vecSize", vecSize);
        }
    }

}
