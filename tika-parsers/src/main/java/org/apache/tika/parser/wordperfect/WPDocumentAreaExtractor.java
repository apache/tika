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
package org.apache.tika.parser.wordperfect;

import java.io.IOException;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Extracts WordPerfect Document Area text from a WordPerfect document.
 * @author Pascal Essiembre
 */
abstract class WPDocumentAreaExtractor {

    public void extract(WPInputStream in, XHTMLContentHandler xhtml) 
            throws IOException, SAXException {
        int chunk = 4096;
        StringBuilder out = new StringBuilder(chunk);
        int c;
        while ((c = in.read()) != -1) {
            extract(c, in, out);
            if (out.length() >= chunk) {
                xhtml.characters(out.toString());
                out.setLength(0);
            }
        }
        xhtml.characters(out.toString());
        out.setLength(0);
    }
 
    protected abstract void extract(
            int c, WPInputStream in, StringBuilder out) throws IOException;
    
    
    // Skips until the given character is encountered.
    protected int skipUntilChar(WPInputStream in, int targetChar)
            throws IOException {
        int count = 0;
        int c;
        while ((c = in.read()) != -1) {
            count++;
            if (c == targetChar) {
                return count;
            }
        }
        return count;
    }
}
