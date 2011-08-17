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
package org.apache.tika.parser.rtf;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.WriteOutContentHandler;

/**
 * Junit test class for the Tika {@link RTFParser}
 */
public class RTFParserTest extends TikaTest {
    private TikaConfig tc;
    private RTFParser parser;

    public void setUp() throws Exception {
        tc = TikaConfig.getDefaultConfig();
        parser = new RTFParser();
    }

    public void testBasicExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTF.rtf");
        
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(
                new FileInputStream(file),
                new WriteOutContentHandler(writer),
                metadata,
                new ParseContext());
        String content = writer.toString();

        assertEquals("application/rtf", metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Test", content);
        assertContains("indexation Word", content);
    }

    public void testRTFms932Extraction() throws Exception {
        String content = getText("testRTF-ms932.rtf");
        
        // Hello in Japanese
        assertContains("\u3053\u3093\u306b\u3061\u306f", content);
    }

    public void testRTFUmlautSpacesExtraction() throws Exception {
        String content = getText("testRTFUmlautSpaces.rtf");

        assertContains("\u00DCbersicht", content);
    }

    public void testRTFWordPadCzechCharactersExtraction() throws Exception {
        String content = getText("testRTFWordPadCzechCharacters.rtf");

        assertContains("\u010Cl\u00E1nek t\u00FDdne", content);
        assertContains("starov\u011Bk\u00E9 \u017Eidovsk\u00E9 n\u00E1bo\u017Eensk\u00E9 texty", content);
    }

    public void testRTFWord2010CzechCharactersExtraction() throws Exception {
        String content = getText("testRTFWord2010CzechCharacters.rtf");

        assertContains("\u010Cl\u00E1nek t\u00FDdne", content);
        assertContains("starov\u011Bk\u00E9 \u017Eidovsk\u00E9 n\u00E1bo\u017Eensk\u00E9 texty", content);
    }

    public void testRTFTableCellSeparation() throws Exception {
        String content = getText("testRTFTableCellSeparation.rtf");

        content = content.replaceAll("\\s+"," ");
        assertContains("a b c d \u00E4 \u00EB \u00F6 \u00FC", content);
    }
    
    public void testGothic() throws Exception {
    	String content = getText("testRTFUnicodeGothic.rtf");
    	assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }
    
    public void testJapaneseText() throws Exception {
       String content = getText("testRTFJapanese.rtf");

       assertContains("1.", content);
       assertContains("4.", content);
       
       // Special version of (GHQ)
       assertContains("\uff08\uff27\uff28\uff31\uff09", content);
       
       // 6 other characters
       assertContains("\u6771\u4eac\u90fd\u4e09\u9df9\u5e02", content);
    }

    public void testTextWithCurlyBraces() throws Exception {
        String content = getText("testRTFWithCurlyBraces.rtf");
        //assertContains("{ some text inside curly brackets }", content);
        assertContains("{  some text inside curly brackets  }", content);
    }

    private String getText(String filename) throws Exception {
       File file = getResourceAsFile("/test-documents/" + filename);
       
       Metadata metadata = new Metadata();
       StringWriter writer = new StringWriter();
       parser.parse(
               new FileInputStream(file),
               new WriteOutContentHandler(writer),
               metadata,
               new ParseContext());
       String content = writer.toString();
       return content;
    }
}
