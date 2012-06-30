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
package org.apache.tika.detect;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Test cases for the {@link MagicDetector} class.
 */
public class MagicDetectorTest extends TestCase {

    public void testDetectNull() throws Exception {
        MediaType html = new MediaType("text", "html");
        Detector detector = new MagicDetector(html, "<html".getBytes("ASCII"));
        assertEquals(
                MediaType.OCTET_STREAM,
                detector.detect(null, new Metadata()));
    }

    public void testDetectSimple() throws Exception {
        MediaType html = new MediaType("text", "html");
        Detector detector = new MagicDetector(html, "<html".getBytes("ASCII"));

        assertDetect(detector, html, "<html");
        assertDetect(detector, html, "<html><head/><body/></html>");
        assertDetect(detector, MediaType.OCTET_STREAM, "<HTML");
        assertDetect(detector, MediaType.OCTET_STREAM, "<?xml?><html");
        assertDetect(detector, MediaType.OCTET_STREAM, " <html");
        assertDetect(detector, MediaType.OCTET_STREAM, "");
    }

    public void testDetectOffsetRange() throws Exception {
        MediaType html = new MediaType("text", "html");
        Detector detector = new MagicDetector(
                html, "<html".getBytes("ASCII"), null, 0, 64);

        assertDetect(detector, html, "<html");
        assertDetect(detector, html, "<html><head/><body/></html>");
        assertDetect(detector, html, "<?xml?><html/>");
        assertDetect(detector, html, "\n    <html");
        assertDetect(detector, html, "\u0000<html");
        assertDetect(detector, MediaType.OCTET_STREAM, "<htm");
        assertDetect(detector, MediaType.OCTET_STREAM, " html");
        assertDetect(detector, MediaType.OCTET_STREAM, "<HTML");

        assertDetect(detector, html,
                "0........1.........2.........3.........4.........5.........6"
                + "1234<html");
        assertDetect(detector, MediaType.OCTET_STREAM,
                "0........1.........2.........3.........4.........5.........6"
                + "12345<html");

        assertDetect(detector, MediaType.OCTET_STREAM, "");
    }

    public void testDetectMask() throws Exception {
        MediaType html = new MediaType("text", "html");
        byte up = (byte) 0xdf;
        Detector detector = new MagicDetector(
                html,
                new byte[] { '<',  'H',  'T',  'M',  'L' },
                new byte[] { (byte) 0xff, up, up, up, up },
                0, 64);

        assertDetect(detector, html, "<html");
        assertDetect(detector, html, "<HTML><head/><body/></html>");
        assertDetect(detector, html, "<?xml?><HtMl/>");
        assertDetect(detector, html, "\n    <html");
        assertDetect(detector, html, "\u0000<HTML");
        assertDetect(detector, MediaType.OCTET_STREAM, "<htm");
        assertDetect(detector, MediaType.OCTET_STREAM, " html");

        assertDetect(detector, html,
                "0        1         2         3         4         5         6"
                + "1234<html");
        assertDetect(detector, MediaType.OCTET_STREAM,
                "0        1         2         3         4         5         6"
                + "12345<html");

        assertDetect(detector, MediaType.OCTET_STREAM, "");
    }

    public void testDetectRegExPDF() throws Exception {
        MediaType pdf = new MediaType("application", "pdf");
        Detector detector = new MagicDetector(
                pdf, "(?s)\\A.{0,144}%PDF-".getBytes("ASCII"), null, true, 0, 0);

        assertDetect(detector, pdf, "%PDF-1.0");
        assertDetect(
                detector, pdf,
                "0        10        20        30        40        50        6"
                + "0        70        80        90        100       110       1"
                + "20       130       140"
                + "34%PDF-1.0");
        assertDetect(
                detector, MediaType.OCTET_STREAM,
                "0        10        20        30        40        50        6"
                + "0        70        80        90        100       110       1"
                + "20       130       140"
                + "345%PDF-1.0");
        assertDetect(detector, MediaType.OCTET_STREAM, "");
    }

    public void testDetectRegExGreedy() throws Exception {
        String pattern =
                "(?s)\\x3chtml xmlns=\"http://www\\.w3\\.org/1999/xhtml"
                + "\".*\\x3ctitle\\x3e.*\\x3c/title\\x3e";
        MediaType xhtml = new MediaType("application", "xhtml+xml");
        Detector detector = new MagicDetector(xhtml,
                pattern.getBytes("ASCII"), null,
                true, 0, 8192);

        assertDetect(detector, xhtml,
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<head><title>XHTML test document</title></head>");
    }

    public void testDetectRegExOptions() throws Exception {
        String pattern =
                "(?s)\\A.{0,1024}\\x3c\\!(?:DOCTYPE|doctype) (?:HTML|html) "
                + "(?:PUBLIC|public) \"-//.{1,16}//(?:DTD|dtd) .{0,64}"
                + "(?:HTML|html) 4\\.01";

        String data =
                "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\""
                + "\"http://www.w3.org/TR/html4/strict.dtd\"><HTML>"
                + "<HEAD><TITLE>HTML document</TITLE></HEAD>"
                + "<BODY><P>Hello world!</BODY></HTML>";

        String data1 =
                "<!DOCTYPE html PUBLIC \"-//W3C//dtd html 4.01//EN\""
                + "\"http://www.w3.org/TR/html4/strict.dtd\"><HTML>"
                + "<HEAD><TITLE>HTML document</TITLE></HEAD>"
                + "<BODY><P>Hello world!</BODY></HTML>";

        String data2 =
                "<!DoCtYpE hTmL pUbLiC \"-//W3C//dTd HtMl 4.01//EN\""
                + "\"http://www.w3.org/TR/html4/strict.dtd\"><HTML>"
                + "<HEAD><TITLE>HTML document</TITLE></HEAD>"
                + "<BODY><P>Hello world!</BODY></HTML>";

        MediaType html = new MediaType("text", "html");
        Detector detector = new MagicDetector(
                html, pattern.getBytes("ASCII"), null, true, 0, 0);

        assertDetect(detector, html, data);
        assertDetect(detector, html, data1);
        assertDetect(detector, MediaType.OCTET_STREAM, data2);
    }

    public void testDetectStreamReadProblems() throws Exception {
        byte[] data = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes("ASCII");
        MediaType testMT = new MediaType("application", "test");
        Detector detector = new MagicDetector(testMT, data, null, false, 0, 0);
        // Deliberately prevent InputStream.read(...) from reading the entire
        // buffer in one go
        InputStream stream = new RestrictiveInputStream(data);
        assertEquals(testMT, detector.detect(stream, new Metadata()));
    }

    private void assertDetect(Detector detector, MediaType type, String data) {
        try {
            byte[] bytes = data.getBytes("ASCII");
            InputStream stream = new ByteArrayInputStream(bytes);
            assertEquals(type, detector.detect(stream, new Metadata()));

            // Test that the stream has been reset
            for (int i = 0; i < bytes.length; i++) {
                assertEquals(bytes[i], (byte) stream.read());
            }
            assertEquals(-1, stream.read());
        } catch (IOException e) {
            fail("Unexpected exception from MagicDetector");
        }
    }

    /**
     * InputStream class that does not read in all available bytes in
     * one go.
     */
    private class RestrictiveInputStream extends ByteArrayInputStream {
        public RestrictiveInputStream(byte[] buf) {
            super(buf);
        }

        /**
         * Prevent reading the entire len of bytes if requesting more
         * than 10 bytes.
         */
        public int read(byte[] b, int off, int len) {
            if (len > 10) {
                return super.read(b, off, len-10);
            } else {
                return super.read(b, off, len);
            }
        }
    }

}
