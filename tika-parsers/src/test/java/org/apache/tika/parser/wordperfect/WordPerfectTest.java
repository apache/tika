/* Copyright 2016 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.wordperfect;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.WriteOutContentHandler;
import org.junit.Test;

/**
 * Junit test class for the {@link WordPerfectParser}.
 * @author Pascal Essiembre
 */
public class WordPerfectTest extends TikaTest {

    private Tika tika = new Tika();

    @Test
    public void testWordPerfectParser() throws Exception {
        File file = getResourceAsFile("/test-documents/testWordPerfect.wpd");

        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        tika.getParser().parse(
                new FileInputStream(file),
                new WriteOutContentHandler(writer),
                metadata,
                new ParseContext());
        String content = writer.toString();

        assertEquals("application/vnd.wordperfect", 
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(1, metadata.getValues(Metadata.CONTENT_TYPE).length);
        assertContains("test test", content);
    }
}
