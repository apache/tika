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
package org.apache.tika.parser.dbf;

import org.apache.commons.io.IOUtils;
import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;

public class DBFParserTest extends TikaTest {

    @Test
    public void testBasic() throws Exception {
        XMLResult r = getXML("testDBF.dbf");
        assertEquals(DBFReader.Version.FOXBASE_PLUS.getFullMimeString(), r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("2016-05-24T00:00:00Z", r.metadata.get(TikaCoreProperties.MODIFIED));
        assertEquals("UTF-8", r.metadata.get(Metadata.CONTENT_ENCODING));

        String xml = r.xml.replaceAll("[\\t\\r\\n]", " ");
        //header
        assertContains("<thead> <th>TEXT_FIELD</th> <th>NUMERIC_FI</th> <th>DATE_FIELD</th></thead>",
                xml);
        //look for contents
        assertContains("普林斯顿大学", xml);
        assertContains("\u0627\u0645\u0639\u0629", xml);
        assertContains("05/26/2016", xml);
        assertContains("<td>4.0</td>", xml);
        //make sure there is no problem around row 10
        //where we're buffering
        assertContains("<td>8.0</td>", xml);
        assertContains("<td>9.0</td>", xml);
        assertContains("<td>10.0</td>", xml);
        assertContains("<td>11.0</td>", xml);
        assertContains("<td>licour</td>", xml);
    }

    @Test
    public void testGB18030Encoded() throws Exception {
        XMLResult r = getXML("testDBF_gb18030.dbf");
        assertEquals(DBFReader.Version.FOXBASE_PLUS.getFullMimeString(), r.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("虽然该", r.xml);
    }

    @Test
    public void testTruncated() throws Exception {
        Parser p = new DBFParser();
        //should throw exception for truncation in header
        for (int i = 1; i < 129; i++) {
            try {
                XMLResult r = getXML(truncate("testDBF.dbf", i), p, new Metadata());
                fail("Should have thrown exception for truncation in header: " + i);
            } catch (IOException | TikaException e) {
                //ok -- expected
            } catch (Throwable e) {
                fail("Should only throw IOExceptions or TikaExceptions");
            }
        }
        //default don't throw exception for truncation while reading body
        for (int i = 129; i < 204; i++) {
            try {
                XMLResult r = getXML(truncate("testDBF.dbf", i), p, new Metadata());
            } catch (IOException | TikaException e) {
                fail("Shouldn't have thrown exception for truncation while reading cells: " + i);
                e.printStackTrace();
            }
        }
        try {
            DBFReader.STRICT = true;
            //if strict is true throw exception for truncation in body
            for (int i = 129; i < 204; i++) {
                try {
                    XMLResult r = getXML(truncate("testDBF.dbf", i), p, new Metadata());
                    fail("Should have thrown exception for truncation while reading cells: " + i);
                } catch (IOException | TikaException e) {
                }
            }
        } finally {
            //reset for other tests
            DBFReader.STRICT = false;
        }
    }

    @Test
    public void testSpecificTruncated() throws Exception {
        XMLResult r = getXML(truncate("testDBF.dbf", 781), new AutoDetectParser(), new Metadata());
        String xml = r.xml.replaceAll("[\\t\\r\\n]", " ");

        //if you don't keep track of bytes read, you could get content from prev row
        assertNotContained("holt red hath in every", xml);
        assertNotContained("<td>holt</td> <td>18.0</td>", xml);
        //check that the last row ends with holt but is correctly formatted
        assertContains("<td>holt</td> <td /> <td /></tr>", xml);
    }

    @Test
    public void testVariants() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = getResourceAsStream("/test-documents/testDBF.dbf")) {
            IOUtils.copy(is, bos);
        }
        byte[] bytes = bos.toByteArray();

        for (DBFReader.Version version : DBFReader.Version.values()) {
            //this cast happens to work because of the range of possible values
            bytes[0] = (byte) version.getId();
            XMLResult r = getXML(TikaInputStream.get(bytes), new AutoDetectParser(), new Metadata());
            assertEquals(version.getFullMimeString(), r.metadata.get(Metadata.CONTENT_TYPE));
        }
    }

/*
commented out until we get permission to add the test file
    @Test
    public void testEncodingInHeaderAndDateTime() throws Exception {
        XMLResult r = getXML("prem2007_2.dbf");
        String xml = r.xml.replaceAll("[\\r\\n\\t]", " ");
        assertEquals("application/x-dbf; dbf_version=Visual_FoxPro", r.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("<th>莉こ晤鎢</th>", xml);//header
        assertContains("<td>齠褕</td>", xml);//content
        assertContains("<td>2010-04-20T00:00:00Z</td>", xml);
    }
    */

    InputStream truncate(String testFileName, int length) throws IOException {
        byte[] bytes = new byte[length];
        try (InputStream is = getResourceAsStream("/test-documents/" + testFileName)) {
            IOUtils.readFully(is, bytes);
        }
        return new ByteArrayInputStream(bytes);
    }
}
