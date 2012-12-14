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
package org.apache.tika.embedder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.embedder.Embedder;
import org.apache.tika.embedder.ExternalEmbedder;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for {@link ExternalEmbedder}s.
 */
public class ExternalEmbedderTest extends TestCase {

    protected static final DateFormat EXPECTED_METADATA_DATE_FORMATTER =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    protected static final String DEFAULT_CHARSET = "UTF-8";
    private static final String COMMAND_METADATA_ARGUMENT_DESCRIPTION = "dc:description";
    private static final String TEST_TXT_PATH = "/test-documents/testTXT.txt";

    private TemporaryResources tmp = new TemporaryResources();

    /**
     * Create the test case
     *
     * @param testName
     *            name of the test case
     */
    public ExternalEmbedderTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(ExternalEmbedderTest.class);
    }

    /**
     * Gets the expected returned metadata value for the given field
     *
     * @param fieldName
     * @return a prefix added to the field name
     */
    protected String getExpectedMetadataValueString(String fieldName, Date timestamp) {
        return this.getClass().getSimpleName() + " embedded " + fieldName +
                " on " + EXPECTED_METADATA_DATE_FORMATTER.format(timestamp);
    }

    /**
     * Gets the tika <code>Metadata</code> object containing data to be
     * embedded.
     *
     * @return the populated tika metadata object
     */
    protected Metadata getMetadataToEmbed(Date timestamp) {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.DESCRIPTION,
                getExpectedMetadataValueString(TikaCoreProperties.DESCRIPTION.toString(), timestamp));
        return metadata;
    }

    /**
     * Gets the <code>Embedder</code> to test.
     *
     * @return the embedder under test
     */
    protected Embedder getEmbedder() {
        ExternalEmbedder embedder = new ExternalEmbedder();
        Map<Property, String[]> metadataCommandArguments = new HashMap<Property, String[]>(1);
        metadataCommandArguments.put(TikaCoreProperties.DESCRIPTION,
                new String[] { COMMAND_METADATA_ARGUMENT_DESCRIPTION });
        embedder.setMetadataCommandArguments(metadataCommandArguments);
        return embedder;
    }

    /**
     * Gets the original input stream before metadata has been embedded.
     *
     * @return a fresh input stream
     */
    protected InputStream getOriginalInputStream() {
        return this.getClass().getResourceAsStream(TEST_TXT_PATH);
    }

    /**
     * Gets the parser to use to verify the result of the embed operation.
     *
     * @return the parser to read embedded metadata
     */
    protected Parser getParser() {
        return new TXTParser();
    }

    /**
     * Whether or not the final result of reading the now embedded metadata is
     * expected in the output of the external tool
     *
     * @return whether or not results are expected in command line output
     */
    protected boolean getIsMetadataExpectedInOutput() {
        return true;
    }

    /**
     * Tests embedding metadata then reading metadata to verify the results.
     *
     * @param isResultExpectedInOutput whether or not results are expected in command line output
     */
    protected void embedInTempFile(boolean isResultExpectedInOutput) {
        Date timestamp = new Date();
        Metadata metadataToEmbed = getMetadataToEmbed(timestamp);
        Embedder embedder = getEmbedder();

        try {
            // Get the input stream for the test document
            InputStream origInputStream = getOriginalInputStream();
            File tempOutputFile = tmp.createTemporaryFile();
            FileOutputStream tempFileOutputStream = new FileOutputStream(tempOutputFile);

            // Embed the metadata into a copy of the original output stream
            embedder.embed(metadataToEmbed, origInputStream, tempFileOutputStream, null);

            ParseContext context = new ParseContext();
            Parser parser = getParser();
            context.set(Parser.class, parser);

            // Setup the extracting content handler
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            OutputStreamWriter outputWriter = new OutputStreamWriter(result,DEFAULT_CHARSET);
            ContentHandler handler = new BodyContentHandler(outputWriter);

            // Create a new metadata object to read the new metadata into
            Metadata embeddedMetadata = new Metadata();

            // Setup a re-read of the now embeded temp file
            FileInputStream embeddedFileInputStream = new FileInputStream(tempOutputFile);

            parser.parse(embeddedFileInputStream, handler, embeddedMetadata,
                    context);

            tmp.dispose();

            String outputString = null;
            if (isResultExpectedInOutput) {
                outputString = result.toString(DEFAULT_CHARSET);
            } else {
                assertTrue("no metadata found", embeddedMetadata.size() > 0);
            }

            // Check each metadata property for the expected value
            for (String metadataName : metadataToEmbed.names()) {
                if (metadataToEmbed.get(metadataName) != null) {
                    String expectedValue = metadataToEmbed.get(metadataName);
                    boolean foundExpectedValue = false;
                    if (isResultExpectedInOutput) {
                        // just check that the entire output contains the expected string
                        foundExpectedValue = outputString.contains(expectedValue);
                    } else {
                        if (embeddedMetadata.isMultiValued(metadataName)) {
                            for (String embeddedValue : embeddedMetadata.getValues(metadataName)) {
                                if (embeddedValue != null) {
                                    if (embeddedValue.contains(expectedValue)) {
                                        foundExpectedValue = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            String embeddedValue = embeddedMetadata.get(metadataName);
                            assertNotNull("expected metadata for "
                                    + metadataName + " not found",
                                    embeddedValue);
                            foundExpectedValue = embeddedValue.contains(expectedValue);
                        }
                    }
                    assertTrue(
                            "result did not contain expected appended metadata "
                                    + metadataName + "="
                                    + expectedValue,
                            foundExpectedValue);
                }
            }
        } catch (IOException e) {
            fail(e.getMessage());
        } catch (TikaException e) {
            fail(e.getMessage());
        } catch (SAXException e) {
            fail(e.getMessage());
        }
    }

    public void testEmbed() throws IOException {
        String os = System.getProperty("os.name", "");
        if (!os.contains("Windows")) {
            embedInTempFile(getIsMetadataExpectedInOutput());
        }
    }

}
