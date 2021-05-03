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

package org.apache.tika.parser.microsoft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class JackcessParserTest extends TikaTest {

    @Test
    public void testBasic() throws Exception {

        RecursiveParserWrapper w = new RecursiveParserWrapper(AUTO_DETECT_PARSER);

        for (String fName : new String[]{"testAccess2.accdb", "testAccess2_2000.mdb",
                "testAccess2_2002-2003.mdb"}) {
            InputStream is = null;
            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                    new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML,
                            -1));
            try {
                is = this.getResourceAsStream("/test-documents/" + fName);

                Metadata meta = new Metadata();
                ParseContext c = new ParseContext();
                w.parse(is, handler, meta, c);
            } finally {
                IOUtils.closeQuietly(is);
            }
            List<Metadata> list = handler.getMetadataList();
            assertEquals(4, list.size());
            String mainContent = list.get(0).get(TikaCoreProperties.TIKA_CONTENT);

            //make sure there's a thead and tbody
            assertContains("</thead><tbody>", mainContent);

            //assert table header
            assertContains("<th>ShortTextField</th>", mainContent);

            //test date format
            //java 8 is 6/24/15 ...java 10 is 2015-06-24
            assertTrue(mainContent.contains("6/24/15") || mainContent.contains("2015-06-24"));

            //test that markup is stripped
            assertContains("over the bold italic dog", mainContent);

            //test unicode
            assertContains("\u666E\u6797\u65AF\u987F\u5927\u5B66", mainContent);

            //test embedded document handling
            assertContains("Test Document with embedded pdf",
                    list.get(3).get(TikaCoreProperties.TIKA_CONTENT));
        }
    }

    @Test
    public void testPassword() throws Exception {
        ParseContext c = new ParseContext();
        c.set(PasswordProvider.class, new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "tika";
            }
        });
        String content = null;
        try (InputStream is = this
                .getResourceAsStream("/test-documents/testAccess2_encrypted.accdb")) {
            content = getText(is, AUTO_DETECT_PARSER, c);
        }
        assertContains("red and brown", content);

        //now try wrong password
        c.set(PasswordProvider.class, new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "WRONG";
            }
        });

        boolean ex = false;
        try (InputStream is = this
                .getResourceAsStream("/test-documents/testAccess2_encrypted.accdb")) {
            getText(is, AUTO_DETECT_PARSER, c);
        } catch (EncryptedDocumentException e) {
            ex = true;
        }
        assertTrue("failed to throw encrypted document exception for wrong password", ex);

        //now try null
        c.set(PasswordProvider.class, new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return null;
            }
        });

        ex = false;
        try (InputStream is = this
                .getResourceAsStream("/test-documents/testAccess2_encrypted.accdb")) {
            getText(is, AUTO_DETECT_PARSER, c);
        } catch (EncryptedDocumentException e) {
            ex = true;
        }
        assertTrue("failed to throw encrypted document exception for null password", ex);


        //now try missing password provider
        c = new ParseContext();
        ex = false;
        try (InputStream is = this
                .getResourceAsStream("/test-documents/testAccess2_encrypted.accdb")) {
            getText(is, AUTO_DETECT_PARSER, c);
        } catch (EncryptedDocumentException e) {
            ex = true;
        }
        assertTrue("failed to throw encrypted document exception for missing password provider",
                ex);

        //now try password on file that doesn't need a password
        c = new ParseContext();
        c.set(PasswordProvider.class, new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "tika";
            }
        });
        ex = false;
        try (InputStream is = this.getResourceAsStream("/test-documents/testAccess2.accdb")) {
            content = getText(is, AUTO_DETECT_PARSER, c);
        } catch (EncryptedDocumentException e) {
            ex = true;
        }
        assertFalse("shouldn't have thrown encrypted document exception for " +
                "opening unencrypted file that doesn't need passowrd", ex);
        assertContains("red and brown", content);
    }

    @Test
    public void testReadOnly() throws Exception {
        //TIKA-1681: just make sure an exception is not thrown
        XMLResult r = getXML("testAccess_V1997.mdb");
        assertContains("hijklmnop", r.xml);
    }

    @Test
    public void testMetadata() throws Exception {
        //basic tests for normalized metadata
        XMLResult r = getXML("testAccess_V1997.mdb");
        assertEquals("tmccune", r.metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("Health Market Science", r.metadata.get(OfficeOpenXMLExtended.COMPANY));
        assertEquals("test", r.metadata.get(TikaCoreProperties.TITLE));
    }
}
